package gt.cadprint.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog-api-${random.uuid}",
        "CADPRINT_ADMIN_PASSWORD=test-admin-password",
        "CADPRINT_OPERATOR_PASSWORD=test-operator-password"
})
@AutoConfigureMockMvc
class ProductCatalogApiIntegrationTest {
    private static final String PRODUCTS = "/api/products";
    private static final String CATEGORIES = "/api/product-categories";
    private static final RequestPostProcessor ADMIN = httpBasic("admin", "test-admin-password");
    private static final RequestPostProcessor OPERATOR = httpBasic("operador", "test-operator-password");

    @Autowired MockMvc api;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test
    void administersCategoriesAndEnforcesTheirRelationshipToActiveProducts() throws Exception {
        api.perform(get(CATEGORIES)).andExpect(status().isUnauthorized());
        send(post(CATEGORIES), OPERATOR, json.createObjectNode().put("name", "Denegada"))
                .andExpect(status().isForbidden());
        JsonNode category = category(" Textiles API ");
        long categoryId = id(category);
        String categoryPath = CATEGORIES + "/" + categoryId;
        api.perform(get(CATEGORIES).with(OPERATOR)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Textiles API')].id").value(org.hamcrest.Matchers.contains(Math.toIntExact(categoryId))));
        send(post(CATEGORIES), ADMIN, json.createObjectNode().put("name", "TEXTILES API"))
                .andExpect(status().isConflict());
        send(put(categoryPath), OPERATOR, json.createObjectNode().put("name", "Ropa").put("version", version(category)))
                .andExpect(status().isForbidden());
        JsonNode renamed = body(send(put(categoryPath), ADMIN,
                json.createObjectNode().put("name", "Ropa API").put("version", version(category)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(categoryId)));
        send(put(categoryPath), ADMIN, json.createObjectNode().put("name", "Tarde").put("version", version(category)))
                .andExpect(status().isConflict());

        JsonNode product = createProduct(product(categoryId, "CAT-STATE", "Producto", "PRECIO_BASE", "10"));
        String productPath = PRODUCTS + "/" + id(product);
        send(put(categoryPath + "/status"), ADMIN, statusInput(renamed, false)).andExpect(status().isConflict());
        send(put(categoryPath + "/status"), OPERATOR, statusInput(renamed, false)).andExpect(status().isForbidden());
        send(put(productPath + "/status"), OPERATOR, statusInput(product, false)).andExpect(status().isForbidden());
        JsonNode inactiveProduct = body(send(put(productPath + "/status"), ADMIN, statusInput(product, false))
                .andExpect(status().isOk()));
        JsonNode inactiveCategory = body(send(put(categoryPath + "/status"), ADMIN, statusInput(renamed, false))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false)));
        send(post(PRODUCTS), OPERATOR, product(categoryId, "INACTIVA", "Producto nuevo", "PRECIO_BASE", "11"))
                .andExpect(status().isConflict());
        send(put(productPath + "/status"), ADMIN, statusInput(inactiveProduct, true))
                .andExpect(status().isConflict());
        send(put(categoryPath + "/status"), ADMIN, statusInput(inactiveCategory, true)).andExpect(status().isOk());
        send(put(productPath + "/status"), ADMIN, statusInput(inactiveProduct, true))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void paginatesAndFiltersProductsWithoutEmbeddingVariants() throws Exception {
        long category = id(category("Filtros API"));
        createProduct(product(category, "  PA-001 ", "Alfa", "PRECIO_BASE", "10"));
        createProduct(product(category, "PA-002", "Beta", "PRECIO_BASE", "15"));
        createProduct(product(category, "COT-FILTRO", "Gamma", "REQUIERE_COTIZACION", null)
                .put("productionMode", "PARA_EXISTENCIAS"));
        api.perform(get(PRODUCTS + "?categoryId=" + category + "&page=1&size=1").with(OPERATOR))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].name").value("Beta"))
                .andExpect(jsonPath("$.content[0].variants").doesNotExist());
        api.perform(get(PRODUCTS).param("search", "a-001").with(OPERATOR)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("PA-001"));
        api.perform(get(PRODUCTS).param("search", "alF").with(OPERATOR)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Alfa"));
        api.perform(get(PRODUCTS + "?categoryId=" + category
                        + "&productionMode=PARA_EXISTENCIAS&pricingMode=REQUIERE_COTIZACION&active=true").with(ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].requiresQuotation").value(true));
        api.perform(get(PRODUCTS + "?size=101").with(OPERATOR)).andExpect(status().isBadRequest());
        api.perform(get(PRODUCTS + "?productionMode=ERROR").with(OPERATOR)).andExpect(status().isBadRequest());
    }

    @Test
    void calculatesReferencePricesAndPreservesIndividualVariantState() throws Exception {
        long category = id(category("Precios API"));
        JsonNode product = createProduct(product(category, "PRICE-BASE", "Precio", "PRECIO_BASE", "12.25"));
        String path = PRODUCTS + "/" + id(product);
        JsonNode first = body(send(post(path + "/variants"), OPERATOR, variant("PRICE-A", null))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.effectivePrice").value(12.25))
                .andExpect(jsonPath("$.requiresQuotation").value(false)));
        JsonNode second = body(send(post(path + "/variants"), OPERATOR, variant("PRICE-B", "15.40"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.effectivePrice").value(15.40)));
        String firstPath = path + "/variants/" + id(first);
        send(put(firstPath + "/status"), OPERATOR, statusInput(first, false)).andExpect(status().isForbidden());
        JsonNode edited = body(send(put(firstPath), OPERATOR,
                variant("PRICE-A-RENAMED", "18").put("version", version(first))).andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("PRICE-A-RENAMED")));
        send(put(firstPath), OPERATOR, variant("PRICE-A", null).put("version", version(first)))
                .andExpect(status().isConflict());
        send(put(firstPath + "/status"), ADMIN, statusInput(edited, false)).andExpect(status().isOk());
        JsonNode quotation = body(send(put(path), OPERATOR,
                product(category, "PRICE-BASE", "Cotizable", "REQUIERE_COTIZACION", null).put("version", version(product)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.effectivePrice").isEmpty())
                .andExpect(jsonPath("$.requiresQuotation").value(true)));
        send(post(path + "/variants"), OPERATOR, variant("PRICE-C", null)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.effectivePrice").isEmpty()).andExpect(jsonPath("$.requiresQuotation").value(true));
        JsonNode inactive = body(send(put(path + "/status"), ADMIN, statusInput(quotation, false))
                .andExpect(status().isOk()));
        api.perform(get(path + "/variants").with(OPERATOR)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id(second) + ")].active").value(true))
                .andExpect(jsonPath("$[?(@.id == " + id(second) + ")].commerciallyActive").value(false))
                .andExpect(jsonPath("$[?(@.id == " + id(second) + ")].effectivePrice").value(15.40))
                .andExpect(jsonPath("$[?(@.id == " + id(second) + ")].requiresQuotation").value(true));
        send(put(path + "/status"), ADMIN, statusInput(inactive, true)).andExpect(status().isOk());
        api.perform(get(path).with(OPERATOR)).andExpect(status().isOk())
                .andExpect(jsonPath("$.variants[0].commerciallyActive").value(false))
                .andExpect(jsonPath("$.variants[1].commerciallyActive").value(true));
        send(put(PRODUCTS + "/999999/variants/" + id(second)), OPERATOR, variant("IGNORED", null).put("version", version(second)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rollsBackSkuConflictsAndAdvancesVersionOnSkuOnlyEdit() throws Exception {
        long category = id(category("Unicidad API"));
        JsonNode product = createProduct(product(category, "SKU-ORIGINAL", "Nombre original", "PRECIO_BASE", "22"));
        String path = PRODUCTS + "/" + id(product);
        JsonNode variant = body(send(post(path + "/variants"), OPERATOR, variant("SKU-VARIANTE", null))
                .andExpect(status().isCreated()));
        send(post(PRODUCTS), OPERATOR, product(category, " sku-original ", "Duplicado", "PRECIO_BASE", "22"))
                .andExpect(status().isConflict());
        send(post(path + "/variants"), OPERATOR, variant("sku-original", null)).andExpect(status().isConflict());
        send(put(path), OPERATOR, product(category, "SKU-VARIANTE", "Cambio que debe revertirse", "PRECIO_BASE", "23")
                        .put("version", version(product))).andExpect(status().isConflict());
        api.perform(get(path).with(OPERATOR)).andExpect(status().isOk())
                .andExpect(jsonPath("$.product.name").value("Nombre original"))
                .andExpect(jsonPath("$.product.sku").value("SKU-ORIGINAL"))
                .andExpect(jsonPath("$.product.version").value(version(product)));
        JsonNode updated = body(send(put(path), OPERATOR,
                product(category, " sku-nuevo ", "Nombre original", "PRECIO_BASE", "22").put("version", version(product)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sku").value("SKU-NUEVO")));
        assertThat(version(updated)).isGreaterThan(version(product));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_skus WHERE sku = 'SKU-ORIGINAL'", Long.class)).isZero();
        send(put(path), OPERATOR, product(category, "OTRO", "Obsoleto", "PRECIO_BASE", "22").put("version", version(product)))
                .andExpect(status().isConflict());
        JsonNode changedVariant = body(send(put(path + "/variants/" + id(variant)), OPERATOR,
                variant("SKU-VARIANTE-2", null).put("version", version(variant))).andExpect(status().isOk()));
        assertThat(version(changedVariant)).isGreaterThan(version(variant));
        send(post(PRODUCTS), OPERATOR, product(category, "FALTA-PRECIO", "Inválido", "PRECIO_BASE", null))
                .andExpect(status().isBadRequest());
        send(post(PRODUCTS), OPERATOR, product(category, "PRECISION", "Inválido", "REQUIERE_COTIZACION", "3.141"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_skus WHERE sku IN ('FALTA-PRECIO','PRECISION')", Long.class))
                .isZero();
    }

    @Test
    void allowsOnlyOneConcurrentReservationAcrossProductAndVariant() throws Exception {
        long category = id(category("Carrera SKU API"));
        JsonNode parent = createProduct(product(category, "RACE-PARENT", "Producto", "PRECIO_BASE", "10"));
        String path = PRODUCTS + "/" + id(parent) + "/variants";
        var ready = new CyclicBarrier(2);
        Callable<Integer> createProduct = () -> {
            ready.await(5, TimeUnit.SECONDS);
            return send(post(PRODUCTS), OPERATOR, product(category, "RACE-SHARED", "Producto", "PRECIO_BASE", "10"))
                    .andReturn().getResponse().getStatus();
        };
        Callable<Integer> createVariant = () -> {
            ready.await(5, TimeUnit.SECONDS);
            return send(post(path), OPERATOR, variant(" race-shared ", null)).andReturn().getResponse().getStatus();
        };
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(createProduct);
            var second = workers.submit(createVariant);
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_skus WHERE sku = 'RACE-SHARED'", Long.class))
                .isEqualTo(1);
    }

    private JsonNode category(String name) throws Exception {
        return body(send(post(CATEGORIES), ADMIN, json.createObjectNode().put("name", name))
                .andExpect(status().isCreated()));
    }

    private JsonNode createProduct(ObjectNode input) throws Exception {
        return body(send(post(PRODUCTS), OPERATOR, input).andExpect(status().isCreated()));
    }

    private ObjectNode product(long category, String sku, String name, String mode, String price) {
        return json.createObjectNode().put("sku", sku).put("name", name).put("categoryId", category)
                .put("productionMode", "BAJO_PEDIDO").put("pricingMode", mode).put("basePrice", price);
    }

    private ObjectNode variant(String sku, String price) {
        return json.createObjectNode().put("sku", sku).put("name", "Variante").put("ownPrice", price);
    }

    private ObjectNode statusInput(JsonNode entity, boolean active) {
        return json.createObjectNode().put("active", active).put("version", version(entity));
    }

    private long id(JsonNode entity) {
        return entity.path("id").asLong();
    }

    private long version(JsonNode entity) {
        return entity.path("version").asLong();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, RequestPostProcessor user, ObjectNode input)
            throws Exception {
        return api.perform(request.with(user).contentType(MediaType.APPLICATION_JSON).content(input.toString()));
    }

    private JsonNode body(ResultActions response) throws Exception {
        return json.readTree(response.andReturn().getResponse().getContentAsString());
    }
}
