package gt.cadprint.manager;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import gt.cadprint.manager.PurchaseDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/purchases")
class PurchaseController {
    private final PurchaseService purchases;

    PurchaseController(PurchaseService purchases) {
        this.purchases = purchases;
    }

    @GetMapping
    PurchasePage list(@RequestParam(required = false) PurchaseStatus status,
                      @RequestParam(required = false) Long supplierId,
                      @RequestParam(required = false) LocalDate from,
                      @RequestParam(required = false) LocalDate to,
                      @RequestParam(required = false) String documentNumber,
                      @RequestParam(defaultValue = "0") int page,
                      @RequestParam(defaultValue = "20") int size) {
        return purchases.list(status, supplierId, from, to, documentNumber, page, size);
    }

    @PostMapping
    ResponseEntity<Detail> create(@Valid @RequestBody DraftRequest input, Authentication auth) {
        Detail created = purchases.create(input, actor(auth));
        return ResponseEntity.created(URI.create("/api/purchases/" + created.purchase().id())).body(created);
    }

    @GetMapping("/{id}")
    Detail detail(@PathVariable Long id) {
        return purchases.detail(id);
    }

    @PutMapping("/{id}")
    Detail edit(@PathVariable Long id, @Valid @RequestBody DraftRequest input, Authentication auth) {
        return purchases.edit(id, input, actor(auth));
    }

    @PostMapping("/{id}/confirm")
    Detail confirm(@PathVariable Long id, @Valid @RequestBody ConfirmRequest input, Authentication auth) {
        return purchases.confirm(id, input, actor(auth));
    }

    @PutMapping("/{id}/receipt")
    ResponseEntity<ReceiptView> receive(@PathVariable Long id, Authentication auth) {
        PurchaseService.ReceiptResult result = purchases.receive(id, actor(auth));
        return result.created()
                ? ResponseEntity.created(URI.create("/api/purchases/" + id + "/receipt")).body(result.receipt())
                : ResponseEntity.ok(result.receipt());
    }

    @PostMapping("/{id}/cancel")
    Detail cancel(@PathVariable Long id, @Valid @RequestBody ReasonRequest input, Authentication auth) {
        return purchases.cancel(id, input.reason(), actor(auth));
    }

    @PostMapping("/{id}/return-to-draft")
    Detail returnToDraft(@PathVariable Long id, @Valid @RequestBody ReasonRequest input, Authentication auth) {
        return purchases.returnToDraft(id, input.reason(), actor(auth));
    }

    @GetMapping("/{id}/events")
    List<EventView> events(@PathVariable Long id) {
        return purchases.events(id);
    }

    private PurchaseService.Actor actor(Authentication auth) {
        boolean admin = auth.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_ADMIN"));
        return new PurchaseService.Actor(auth.getName(), admin);
    }
}
