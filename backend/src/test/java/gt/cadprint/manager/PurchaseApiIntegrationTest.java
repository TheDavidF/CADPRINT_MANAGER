package gt.cadprint.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sprint3-api-${random.uuid}",
        "CADPRINT_ADMIN_PASSWORD=test-admin-password",
        "CADPRINT_OPERATOR_PASSWORD=test-operator-password"
})
@AutoConfigureMockMvc
class PurchaseApiIntegrationTest {
    private static final String API = "/api/purchases";
    private static final RequestPostProcessor OPERATOR = httpBasic("operador", "test-operator-password");
    private static final RequestPostProcessor ADMIN = httpBasic("admin", "test-admin-password");

    @Autowired MockMvc api;
    @Autowired ObjectMapper json;
    @Autowired MaterialRepo materials;
    @Autowired MovementRepo movements;
    @Autowired UnitConversionService units;

    @Test
    void managesDraftVersionsAndLifecycleWithoutInventorySideEffects() throws Exception {
        long supplier = supplier();
        long material = material("PLA-PURCHASE", "g");
        long movementCount = movements.count();

        JsonNode empty = body(send(post(API), OPERATOR, "{\"purchaseDate\":\"2026-01-01\"}")
                .andExpect(status().isCreated()));
        assertThat(empty.path("lines").isEmpty()).isTrue();

        String original = lines(material, "kg", "1", "150", "g", "1", "0.005");
        JsonNode created = body(send(post(API), OPERATOR, draft(supplier, null, null, original, false))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.purchase.status").value("BORRADOR"))
                .andExpect(jsonPath("$.purchase.currency").value("GTQ"))
                .andExpect(jsonPath("$.purchase.total").value(150.01))
                .andExpect(jsonPath("$.lines.length()").value(2)));
        long id = number(created, "id");
        long version = number(created, "version");
        String path = API + "/" + id;
        api.perform(get(path).with(OPERATOR)).andExpect(status().isOk())
                .andExpect(jsonPath("$.purchase.supplierId").value(supplier))
                .andExpect(jsonPath("$.lines[0].materialId").value(material));

        String editedLines = lines(material, "kg", "2", "75", "g", "1", "0.005");
        JsonNode edited = body(send(put(path), OPERATOR, draft(supplier, null, version, editedLines, false))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].quantity").value(2)));
        long updatedVersion = number(edited, "version");
        assertThat(updatedVersion).isGreaterThan(version);
        send(put(path), OPERATOR, draft(supplier, null, version, original, false)).andExpect(status().isConflict());
        send(post(path + "/confirm"), OPERATOR, confirm(version, false)).andExpect(status().isConflict());
        JsonNode confirmed = body(send(post(path + "/confirm"), OPERATOR, confirm(updatedVersion, false))
                .andExpect(status().isOk()).andExpect(jsonPath("$.purchase.status").value("PENDIENTE"))
                .andExpect(jsonPath("$.lines[0].inventoryQuantity").value(2000))
                .andExpect(jsonPath("$.lines[0].inventoryUnitCost").value(0.075)));
        send(put(path), OPERATOR, draft(supplier, null, number(confirmed, "version"), original, false))
                .andExpect(status().isConflict());

        api.perform(get(API + "?status=PENDIENTE&supplierId=" + supplier + "&from=2026-01-01&size=1")
                        .with(ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id)).andExpect(jsonPath("$.totalElements").value(1));

        String reason = "{\"reason\":\"Corregir cantidades\"}";
        send(post(path + "/return-to-draft"), OPERATOR, reason).andExpect(status().isForbidden());
        send(post(path + "/cancel"), OPERATOR, reason).andExpect(status().isForbidden());
        send(post(path + "/return-to-draft"), ADMIN, "{}").andExpect(status().isBadRequest());
        JsonNode returned = body(send(post(path + "/return-to-draft"), ADMIN, reason)
                .andExpect(status().isOk()).andExpect(jsonPath("$.purchase.status").value("BORRADOR"))
                .andExpect(jsonPath("$.lines[0].inventoryQuantity").isEmpty()));
        JsonNode revised = body(send(put(path), OPERATOR,
                draft(supplier, null, number(returned, "version"), original, false)).andExpect(status().isOk()));
        send(post(path + "/confirm"), OPERATOR, confirm(number(revised, "version"), false))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].inventoryQuantity").value(1000))
                .andExpect(jsonPath("$.lines[0].inventoryUnitCost").value(0.15));
        send(post(path + "/cancel"), ADMIN, reason)
                .andExpect(status().isOk()).andExpect(jsonPath("$.purchase.status").value("CANCELADA"));
        send(post(path + "/cancel"), ADMIN, reason).andExpect(status().isConflict());
        api.perform(get(path + "/events").with(OPERATOR)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("CANCELLED"))
                .andExpect(jsonPath("$[0].username").value("admin"))
                .andExpect(jsonPath("$[?(@.eventType == 'RETURNED_TO_DRAFT')].reason").value("Corregir cantidades"));
        api.perform(get(path + "/events")).andExpect(status().isUnauthorized());
        var stored = materials.findById(material).orElseThrow();
        assertThat(stored.stock).isZero();
        assertThat(stored.unitCost).isEqualByComparingTo("0.42");
        assertThat(movements.count()).isEqualTo(movementCount);
    }

    @Test
    void rejectsInvalidConversionsAndLeavesDraftUnchanged() throws Exception {
        long supplier = supplier();
        long material = material("INVALID-UNITS", "kg");
        for (String unit : new String[]{"ml", "g", "bobina"}) {
            JsonNode purchase = body(send(post(API), OPERATOR, draft(supplier, null, null,
                    lines(material, "kg", "1", "10", unit, "0.001", "10"), false))
                    .andExpect(status().isCreated()));
            String path = API + "/" + number(purchase, "id");
            send(post(path + "/confirm"), OPERATOR, confirm(number(purchase, "version"), false))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.startsWith("Línea 2:")));
            api.perform(get(path).with(OPERATOR)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.purchase.status").value("BORRADOR"))
                    .andExpect(jsonPath("$.lines[0].inventoryQuantity").isEmpty());
        }
    }

    @Test
    void requiresExplicitAdminAuthorizationForDuplicateDocuments() throws Exception {
        long supplier = supplier();
        long material = material("DUP-DOC", "g");
        String lines = lines(material, "g", "1", "1", "g", "2", "1");
        send(post(API), OPERATOR, draft(supplier, " FAC   uno ", null, lines, false))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.purchase.documentNumber").value("FAC UNO"));
        send(post(API), OPERATOR, draft(supplier, "fac uno", null, lines, false)).andExpect(status().isConflict());
        send(post(API), OPERATOR, draft(supplier, "fac uno", null, lines, true)).andExpect(status().isForbidden());
        send(post(API), ADMIN, draft(supplier, "fac uno", null, lines, false)).andExpect(status().isConflict());
        JsonNode authorized = body(send(post(API), ADMIN, draft(supplier, "fac uno", null, lines, true))
                .andExpect(status().isCreated()));
        String path = API + "/" + number(authorized, "id");
        long version = number(authorized, "version");
        send(post(path + "/confirm"), OPERATOR, confirm(version, true)).andExpect(status().isForbidden());
        send(post(path + "/confirm"), OPERATOR, confirm(version, false)).andExpect(status().isConflict());
        send(post(path + "/confirm"), ADMIN, confirm(version, true)).andExpect(status().isOk());
        api.perform(get(path + "/events").with(OPERATOR)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.eventType == 'DUPLICATE_DOCUMENT_AUTHORIZED')].username").value(org.hamcrest.Matchers.contains("admin", "admin")));
        send(post(API), OPERATOR, draft(supplier(), "fac uno", null, lines, false)).andExpect(status().isCreated());
    }

    @Test
    void allowsOnlyOneConcurrentConfirmationOfTheSameVersion() throws Exception {
        long supplier = supplier();
        long material = material("CONCURRENT-PURCHASE", "g");
        JsonNode created = body(send(post(API), OPERATOR, draft(supplier, null, null,
                lines(material, "g", "1", "1", "g", "2", "1"), false)).andExpect(status().isCreated()));
        String path = API + "/" + number(created, "id");
        String request = confirm(number(created, "version"), false);
        var ready = new CyclicBarrier(2);
        Callable<Integer> confirm = () -> {
            ready.await(5, TimeUnit.SECONDS);
            return send(post(path + "/confirm"), OPERATOR, request).andReturn().getResponse().getStatus();
        };
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(confirm);
            var second = workers.submit(confirm);
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        api.perform(get(path + "/events").with(OPERATOR)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].eventType").value("CONFIRMED"));
    }

    @ParameterizedTest
    @CsvSource({"kg,g,1,1000", "g,kg,1000,1", "l,ml,2,2000", "ml,l,1000,1",
            "metro,cm,2,200", "cm,metro,200,2", "hoja,hoja,2,2"})
    void convertsOnlyDeclaredUnitPairs(String from, String to, String quantity, String expected) {
        assertThat(units.convert(new BigDecimal(quantity), from, to).quantity()).isEqualByComparingTo(expected);
    }

    private long supplier() throws Exception {
        return body(send(post("/api/suppliers"), OPERATOR, "{\"name\":\"Proveedor prueba\"}")
                .andExpect(status().isCreated())).path("id").asLong();
    }

    private long material(String sku, String unit) throws Exception {
        return body(send(post("/api/materials"), OPERATOR, """
                {"sku":"%s","name":"Material","category":"Filamento","unit":"%s","unitCost":"0.42"}
                """.formatted(sku, unit)).andExpect(status().isCreated())).path("id").asLong();
    }

    private String lines(long id, String firstUnit, String firstQuantity, String firstPrice,
                         String secondUnit, String secondQuantity, String secondPrice) {
        return """
                [{"materialId":%d,"purchaseUnit":"%s","quantity":"%s","unitPrice":"%s"},
                 {"materialId":%d,"purchaseUnit":"%s","quantity":"%s","unitPrice":"%s"}]
                """.formatted(id, firstUnit, firstQuantity, firstPrice, id, secondUnit, secondQuantity, secondPrice);
    }

    private String draft(long supplier, String document, Long version, String lines, boolean authorized) {
        return """
                {"supplierId":%d,"purchaseDate":"2026-01-01","documentNumber":%s,"version":%s,
                 "lines":%s,"authorizeDuplicate":%s}
                """.formatted(supplier, document == null ? "null" : "\"" + document + "\"", version, lines, authorized);
    }

    private String confirm(long version, boolean authorized) {
        return "{\"version\":" + version + ",\"authorizeDuplicate\":" + authorized + "}";
    }

    private ResultActions send(MockHttpServletRequestBuilder request, RequestPostProcessor user, String payload)
            throws Exception {
        return api.perform(request.with(user).contentType(MediaType.APPLICATION_JSON).content(payload));
    }

    private JsonNode body(ResultActions response) throws Exception {
        return json.readTree(response.andReturn().getResponse().getContentAsString());
    }

    private long number(JsonNode detail, String field) {
        return detail.path("purchase").path(field).asLong();
    }
}
