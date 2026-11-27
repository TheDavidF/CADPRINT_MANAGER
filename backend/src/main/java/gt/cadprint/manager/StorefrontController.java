package gt.cadprint.manager;

import java.util.Base64;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class StorefrontController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    StorefrontController(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @GetMapping("/api/storefront-admin")
    Editor editor() throws JsonProcessingException {
        return jdbc.queryForObject("SELECT version, draft_json, published_json FROM public_storefront WHERE id = 1",
                (row, number) -> new RawEditor(row.getLong(1), row.getString(2), row.getString(3) != null))
                .decode(json);
    }

    @PutMapping("/api/storefront-admin")
    Editor save(@Valid @RequestBody Save input) throws JsonProcessingException {
        var catalog = input.catalog();
        for (var card : catalog.cards()) {
            validateImage(card.image());
            if (input.publish() && card.visible() && card.fixedPrice()
                    && (card.price() == null || card.price().isEmpty())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Agrega un precio a cada tarjeta visible configurada con precio fijo");
            }
        }
        if (input.publish() && (catalog.whatsapp().isEmpty() || catalog.cards().stream().noneMatch(Card::visible))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Agrega un número de WhatsApp y al menos una tarjeta visible antes de publicar");
        }
        String draft = json.writeValueAsString(catalog);
        int changed = input.publish()
                ? jdbc.update("""
                    UPDATE public_storefront SET draft_json = ?, published_json = ?, version = version + 1
                    WHERE id = 1 AND version = ?
                    """, draft, json.writeValueAsString(catalog.visibleOnly()), input.version())
                : jdbc.update("""
                    UPDATE public_storefront SET draft_json = ?, version = version + 1
                    WHERE id = 1 AND version = ?
                    """, draft, input.version());
        requireCurrentVersion(changed);
        return new Editor(input.version() + 1, catalog, input.publish() || editor().published());
    }

    @DeleteMapping("/api/storefront-admin")
    Editor unpublish(@RequestParam long version) throws JsonProcessingException {
        requireCurrentVersion(jdbc.update("""
                UPDATE public_storefront SET published_json = NULL, version = version + 1 WHERE id = 1 AND version = ?
                """, version));
        return editor();
    }

    private void requireCurrentVersion(int changed) {
        if (changed != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El catálogo cambió en otra sesión. Vuelve a abrir el editor antes de guardar");
        }
    }

    @GetMapping("/api/storefront")
    ResponseEntity<Catalog> published() throws JsonProcessingException {
        String snapshot = jdbc.queryForObject("SELECT published_json FROM public_storefront WHERE id = 1", String.class);
        if (snapshot == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El catálogo aún no está publicado");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(json.readValue(snapshot, Catalog.class));
    }

    private void validateImage(String value) {
        if (value == null || value.isEmpty()) return;
        int comma = value.indexOf(',');
        if (comma < 0 || !List.of("data:image/png;base64", "data:image/jpeg;base64", "data:image/webp;base64")
                .contains(value.substring(0, comma))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La foto debe ser PNG, JPEG o WebP");
        }
        try {
            if (Base64.getDecoder().decode(value.substring(comma + 1)).length == 0) {
                throw new IllegalArgumentException("empty");
            }
        } catch (IllegalArgumentException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La foto está vacía o dañada");
        }
    }

    record Save(@PositiveOrZero long version, boolean publish, @NotNull @Valid Catalog catalog) {}

    record Catalog(@NotBlank @Size(max = 60) String brand, @NotBlank @Size(max = 100) String headline,
                   @NotNull @Size(max = 320) String introduction,
                   @NotNull @Pattern(regexp = "(?:[1-9][0-9]{7,14})?") String whatsapp,
                   @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String accent,
                   @NotNull @Size(max = 12) List<@NotNull @Valid Card> cards) {
        Catalog visibleOnly() {
            return new Catalog(brand, headline, introduction, whatsapp, accent, cards.stream().filter(Card::visible).toList());
        }
    }

    record Card(@NotBlank @Size(max = 100) String title, @NotNull @Size(max = 60) String category,
                @NotNull @Size(max = 500) String description, boolean fixedPrice,
                @Pattern(regexp = "(?:[0-9]{1,7}(?:\\.[0-9]{1,2})?)?") String price,
                boolean visible, @Size(max = 350000) String image) {}

    record Editor(long version, Catalog draft, boolean published) {}

    private record RawEditor(long version, String draft, boolean published) {
        Editor decode(ObjectMapper json) throws JsonProcessingException {
            return new Editor(version, draft == null ? null : json.readValue(draft, Catalog.class), published);
        }
    }
}
