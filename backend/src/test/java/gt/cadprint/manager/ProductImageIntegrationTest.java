package gt.cadprint.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:product-image-${random.uuid}",
        "CADPRINT_ADMIN_PASSWORD=test-admin-password",
        "CADPRINT_OPERATOR_PASSWORD=test-operator-password"
})
@AutoConfigureMockMvc
class ProductImageIntegrationTest {
    private static final String PATH = "/api/products/{id}/image";
    private static final RequestPostProcessor ADMIN = httpBasic("admin", "test-admin-password");
    private static final RequestPostProcessor OPERATOR = httpBasic("operador", "test-operator-password");

    @TempDir
    static Path directory;

    @DynamicPropertySource
    static void storageDirectory(DynamicPropertyRegistry properties) {
        properties.add("cadprint.product-images.directory", () -> directory.resolve("images").toString());
    }

    @Autowired MockMvc api;
    @Autowired ObjectMapper json;
    @Autowired ProductRepo products;
    @Autowired ProductCategoryRepo categories;
    @Autowired ProductImageRepo images;
    @Autowired ProductImageStorage storage;
    @Autowired JdbcTemplate jdbc;

    @Test
    void uploadsReplacesAndRemovesOnlyTheMainImageWithVersionChecks() throws Exception {
        Product product = product("LIFECYCLE");
        byte[] jpeg = image("jpeg");
        byte[] png = image("png");
        api.perform(get(PATH, product.id)).andExpect(status().isUnauthorized());
        api.perform(get(PATH, product.id).with(OPERATOR)).andExpect(status().isNotFound());
        api.perform(get(PATH, 999999L).with(OPERATOR)).andExpect(status().isNotFound());
        upload(999999L, 0L, jpeg, ADMIN).andExpect(status().isNotFound());

        JsonNode uploaded = body(upload(product.id, product.version, jpeg, OPERATOR).andExpect(status().isOk())
                .andExpect(jsonPath("$.image.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.image.fileSize").value(jpeg.length))
                .andExpect(jsonPath("$.image.createdAt").isString())
                .andExpect(jsonPath("$.image.storageKey").doesNotExist()));
        long uploadedVersion = uploaded.path("version").asLong();
        assertThat(uploadedVersion).isGreaterThan(product.version);
        ProductImage original = images.findByProductId(product.id).orElseThrow();
        assertThat(files()).contains(original.storageKey);
        api.perform(get(PATH, product.id).with(OPERATOR)).andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentType("image/jpeg")).andExpect(content().bytes(jpeg));
        api.perform(get("/api/products/{id}", product.id).with(ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.image.fileSize").value(jpeg.length))
                .andExpect(jsonPath("$.product.version").value(uploadedVersion));

        upload(product.id, product.version, png, ADMIN).andExpect(status().isConflict());
        JsonNode replaced = body(upload(product.id, uploadedVersion, png, ADMIN).andExpect(status().isOk())
                .andExpect(jsonPath("$.image.contentType").value("image/png")));
        long replacedVersion = replaced.path("version").asLong();
        ProductImage replacement = images.findByProductId(product.id).orElseThrow();
        assertThat(replacement.id).isEqualTo(original.id);
        assertThat(replacement.storageKey).isNotEqualTo(original.storageKey);
        assertThat(files()).contains(replacement.storageKey).doesNotContain(original.storageKey);
        api.perform(get(PATH, product.id).with(ADMIN)).andExpect(status().isOk())
                .andExpect(content().contentType("image/png")).andExpect(content().bytes(png));
        api.perform(delete(PATH, product.id).param("version", String.valueOf(replacedVersion)).with(OPERATOR))
                .andExpect(status().isForbidden());
        api.perform(delete(PATH, product.id).param("version", String.valueOf(uploadedVersion)).with(ADMIN))
                .andExpect(status().isConflict());

        JsonNode removed = body(api.perform(delete(PATH, product.id)
                        .param("version", String.valueOf(replacedVersion)).with(ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.image").isEmpty()));
        long removedVersion = removed.path("version").asLong();
        assertThat(removedVersion).isGreaterThan(replacedVersion);
        assertThat(images.findByProductId(product.id)).isEmpty();
        assertThat(files()).doesNotContain(replacement.storageKey);
        api.perform(get(PATH, product.id).with(ADMIN)).andExpect(status().isNotFound());
        api.perform(delete(PATH, product.id).param("version", String.valueOf(removedVersion)).with(ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(removedVersion));
        assertThat(products.findById(product.id).orElseThrow().getSku()).isEqualTo(product.getSku());
    }

    @Test
    void rejectsEmptyUnsupportedCorruptOversizedAndOverResolutionUploads() throws Exception {
        Product product = product("INVALID");
        Set<String> before = files();
        upload(product.id, product.version, new byte[0], OPERATOR).andExpect(status().isBadRequest());
        upload(product.id, product.version, "not an image".getBytes(java.nio.charset.StandardCharsets.US_ASCII), OPERATOR)
                .andExpect(status().isUnsupportedMediaType());
        byte[] jpeg = image("jpeg");
        upload(product.id, product.version, Arrays.copyOf(jpeg, jpeg.length - 2), OPERATOR)
                .andExpect(status().isBadRequest());
        byte[] png = image("png");
        upload(product.id, product.version, Arrays.copyOf(png, png.length - 12), OPERATOR)
                .andExpect(status().isBadRequest());
        upload(product.id, product.version, new byte[ProductImageValidator.MAX_BYTES + 1], OPERATOR)
                .andExpect(status().isPayloadTooLarge());
        upload(product.id, product.version, oversizedPng(png), OPERATOR).andExpect(status().isBadRequest());
        upload(product.id, null, jpeg, OPERATOR).andExpect(status().isBadRequest());
        assertThat(files()).isEqualTo(before);
        assertThat(images.findByProductId(product.id)).isEmpty();
        assertThat(products.findById(product.id).orElseThrow().version).isEqualTo(product.version);
    }

    @Test
    void failedReplacementRetainsThePriorImageAndCleansTheNewFileOnRollback() throws Exception {
        Product product = product("ROLLBACK");
        byte[] png = image("png");
        long version = body(upload(product.id, product.version, png, OPERATOR).andExpect(status().isOk()))
                .path("version").asLong();
        ProductImage before = images.findByProductId(product.id).orElseThrow();
        Set<String> filesBefore = files();
        upload(product.id, version, new byte[0], ADMIN).andExpect(status().isBadRequest());
        jdbc.execute("ALTER TABLE product_images ADD CONSTRAINT test_rollback_image "
                + "CHECK (product_id <> " + product.id + " OR content_type <> 'image/jpeg')");
        try {
            upload(product.id, version, image("jpeg"), ADMIN).andExpect(status().isConflict());
            assertThat(images.findByProductId(product.id).orElseThrow().storageKey).isEqualTo(before.storageKey);
            assertThat(products.findById(product.id).orElseThrow().version).isEqualTo(version);
            assertThat(files()).isEqualTo(filesBefore);
            api.perform(get(PATH, product.id).with(OPERATOR)).andExpect(status().isOk())
                    .andExpect(content().bytes(png));
        } finally {
            jdbc.execute("ALTER TABLE product_images DROP CONSTRAINT test_rollback_image");
        }
    }

    @Test
    void preventsPathTraversalAndEnforcesUniqueProductAndStorageKey() throws Exception {
        assertThatThrownBy(() -> storage.read("../outside.png")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> storage.delete("../outside.png")).isInstanceOf(IOException.class);

        Product first = product("UNIQUE-A");
        Product second = product("UNIQUE-B");
        byte[] jpeg = image("jpeg");
        upload(first.id, first.version, jpeg, OPERATOR).andExpect(status().isOk());
        String usedKey = images.findByProductId(first.id).orElseThrow().storageKey;
        assertThatThrownBy(() -> insertImage(first.id, "e4dd1640-418a-4981-b429-2bb47120ca29"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertImage(second.id, usedKey)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM products WHERE id = ?", first.id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertImage(long productId, String key) {
        jdbc.update("""
                INSERT INTO product_images (product_id, storage_key, content_type, file_size, created_at)
                VALUES (?, ?, 'image/jpeg', 128, CURRENT_TIMESTAMP)
                """, productId, key);
    }

    private ResultActions upload(Long id, Long version, byte[] bytes, RequestPostProcessor actor) throws Exception {
        var request = multipart(HttpMethod.PUT, PATH, id)
                .file(new MockMultipartFile("file", "../../misleading.gif", MediaType.IMAGE_GIF_VALUE, bytes));
        if (version != null) {
            request.param("version", version.toString());
        }
        return api.perform(request.with(actor));
    }

    private Product product(String sku) {
        ProductCategory category = new ProductCategory();
        category.name = "Imagen " + sku;
        Product product = new Product(sku);
        product.name = sku;
        product.category = categories.saveAndFlush(category);
        product.productionMode = ProductionMode.BAJO_PEDIDO;
        product.pricingMode = PricingMode.REQUIERE_COTIZACION;
        return products.saveAndFlush(product);
    }

    private byte[] image(String format) throws IOException {
        var output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), format, output)).isTrue();
        return output.toByteArray();
    }

    private byte[] oversizedPng(byte[] source) {
        byte[] changed = source.clone();
        ByteBuffer data = ByteBuffer.wrap(changed);
        data.putInt(16, 6000);
        data.putInt(20, 4000);
        var crc = new CRC32();
        crc.update(changed, 12, 17);
        data.putInt(29, (int) crc.getValue());
        return changed;
    }

    private Set<String> files() throws IOException {
        Path root = directory.resolve("images");
        if (!Files.exists(root)) {
            return Set.of();
        }
        try (var paths = Files.list(root)) {
            return paths.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
        }
    }

    private JsonNode body(ResultActions response) throws Exception {
        return json.readTree(response.andReturn().getResponse().getContentAsByteArray());
    }
}
