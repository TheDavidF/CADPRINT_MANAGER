package gt.cadprint.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cadprint-test-${random.uuid}",
        "spring.jpa.hibernate.ddl-auto=validate",
        "CADPRINT_ADMIN_PASSWORD=test-admin-password",
        "CADPRINT_OPERATOR_PASSWORD=test-operator-password"
})
@AutoConfigureMockMvc
class ApiIntegrationTest {
    @Autowired
    MockMvc api;

    @Autowired
    ObjectMapper json;

    @Autowired
    MaterialRepo materials;

    @Test
    void verifiesAuthenticationAndCors() throws Exception {
        api.perform(get("/api/me")).andExpect(status().isUnauthorized());
        api.perform(get("/api/me").with(httpBasic("admin", "incorrecta")))
                .andExpect(status().isUnauthorized());
        api.perform(get("/api/me").with(httpBasic("admin", "test-admin-password")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("admin"));
        api.perform(options("/api/me").header("Origin", "http://127.0.0.1:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5173"));
        api.perform(options("/api/me").header("Origin", "http://127.0.0.1:5174")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void validatesInventoryAndPreservesMovementHistory() throws Exception {
        String material = """
                {"sku":"PLA-NEGRO","name":"PLA negro","category":"Filamento","unit":"g",
                 "minStock":100,"unitCost":0.1825,"stock":99}
                """;
        String created = api.perform(post("/api/materials").with(httpBasic("operador", "test-operator-password"))
                        .contentType(MediaType.APPLICATION_JSON).content(material))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stock").value(0))
                .andExpect(jsonPath("$.unitCost").value(0.1825))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();

        api.perform(post("/api/materials").with(httpBasic("operador", "test-operator-password"))
                        .contentType(MediaType.APPLICATION_JSON).content(material.replace("PLA-NEGRO", "pla-negro")))
                .andExpect(status().isConflict());

        move(id, "ENTRADA", "1000", "Recepción").andExpect(status().isCreated());
        move(id, "SALIDA", "65", "Consumo").andExpect(status().isCreated());
        String update = """
                {"name":"PLA actualizado","category":"Filamento","unit":"g",
                 "minStock":50,"unitCost":"0.1826","active":true,"stock":1}
                """;
        api.perform(put("/api/materials/{id}", id).with(httpBasic("operador", "test-operator-password"))
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("PLA actualizado"))
                .andExpect(jsonPath("$.unitCost").value(0.1826))
                .andExpect(jsonPath("$.stock").value(935));
        api.perform(put("/api/materials/{id}", id).with(httpBasic("operador", "test-operator-password"))
                        .contentType(MediaType.APPLICATION_JSON).content(update.replace("\"g\"", "\"kg\"")))
                .andExpect(status().isConflict());

        move(id, "AJUSTE_MAS", "0.125", "Recuento").andExpect(status().isCreated());
        move(id, "AJUSTE_MENOS", "0.125", "Corrección").andExpect(status().isCreated());
        move(id, "SALIDA", "1000", "Consumo").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Stock insuficiente"));
        move(id, "SALIDA", "-1", "Consumo").andExpect(status().isBadRequest());
        move(id, "SALIDA", "0.0015", "Consumo").andExpect(status().isBadRequest());
        move(id, "SALIDA", "1", " ").andExpect(status().isBadRequest());

        api.perform(get("/api/materials").with(httpBasic("operador", "test-operator-password")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].stock").value(935));
        api.perform(get("/api/movements").with(httpBasic("operador", "test-operator-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].afterStock").value(935))
                .andExpect(jsonPath("$[0].beforeStock").value(935.125))
                .andExpect(jsonPath("$[0].username").value("operador"))
                .andExpect(jsonPath("$[0].material.id").value(id))
                .andExpect(jsonPath("$[0].createdAt").isString());
    }

    @Test
    @DirtiesContext
    void rejectsGramToMilliliterAfterEntriesAndExitsEvenAtZeroStock() throws Exception {
        var operator = httpBasic("operador", "test-operator-password");
        String created = api.perform(post("/api/materials").with(operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"PLA-NEG-001","name":"PLA negro","category":"Filamento","unit":"g"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();

        move(id, "ENTRADA", "1000", "Recepcion").andExpect(status().isCreated());
        move(id, "SALIDA", "65", "Consumo").andExpect(status().isCreated());

        String update = """
                {"name":"PLA editado","category":"Filamento","unit":"%s",
                 "minStock":100,"unitCost":0.18,"active":true}
                """;
        api.perform(put("/api/materials/{id}", id).with(operator)
                        .contentType(MediaType.APPLICATION_JSON).content(update.formatted("ml")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").isNotEmpty());

        Material persisted = materials.findById(id).orElseThrow();
        assertThat(persisted.unit).isEqualTo("g");
        assertThat(persisted.name).isEqualTo("PLA negro");
        assertThat(persisted.stock).isEqualByComparingTo("935");

        api.perform(put("/api/materials/{id}", id).with(operator)
                        .contentType(MediaType.APPLICATION_JSON).content(update.formatted(" G ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unit").value("g"));

        move(id, "SALIDA", "935", "Consumo restante").andExpect(status().isCreated());
        api.perform(put("/api/materials/{id}", id).with(operator)
                        .contentType(MediaType.APPLICATION_JSON).content(update.formatted(" ML ")))
                .andExpect(status().isConflict());

        Material depleted = materials.findById(id).orElseThrow();
        assertThat(depleted.unit).isEqualTo("g");
        assertThat(depleted.stock).isZero();
    }

    @Test
    @DirtiesContext
    void managesCategoriesAndAuditsOnlyCommittedMaterialChanges() throws Exception {
        var operator = httpBasic("operador", "test-operator-password");
        api.perform(get("/api/categories").with(operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Filamento')]").isNotEmpty());

        String category = api.perform(post("/api/categories").with(operator)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Resinas"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long categoryId = json.readTree(category).get("id").asLong();
        api.perform(post("/api/categories").with(operator)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"  RESINAS  "}
                                """))
                .andExpect(status().isConflict());

        String created = api.perform(post("/api/materials").with(operator)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"sku":"RES-001","name":"Resina","unit":"ml","categoryId":%d}
                                """.formatted(categoryId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();

        api.perform(put("/api/categories/{id}", categoryId).with(operator)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Resinas UV"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(categoryId));
        api.perform(get("/api/materials").with(operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.sku == 'RES-001')].category").value("Resinas UV"));

        String update = """
                {"name":"Resina UV","categoryId":%d,"unit":"ml","minStock":0,
                 "unitCost":0.1825,"active":true}
                """.formatted(categoryId);
        for (int attempt = 0; attempt < 2; attempt++) {
            api.perform(put("/api/materials/{id}", id).with(operator)
                            .contentType(MediaType.APPLICATION_JSON).content(update))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.categoryId").value(categoryId));
        }
        move(id, "ENTRADA", "1", "Recepcion").andExpect(status().isCreated());
        api.perform(put("/api/materials/{id}", id).with(operator)
                        .contentType(MediaType.APPLICATION_JSON).content(update.replace("ml", "l")))
                .andExpect(status().isConflict());

        var beforeQuery = ApiDtos.MaterialView.from(materials.findById(id).orElseThrow());
        api.perform(get("/api/materials/{id}/changes", id))
                .andExpect(status().isUnauthorized());
        api.perform(get("/api/materials/{id}/changes", id).with(operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.field == 'name')].previousValue").value("Resina"))
                .andExpect(jsonPath("$[?(@.field == 'name')].newValue").value("Resina UV"))
                .andExpect(jsonPath("$[?(@.field == 'unitCost')].newValue").value("0.1825"))
                .andExpect(jsonPath("$[0].username").value("operador"))
                .andExpect(jsonPath("$[0].changedAt").isString())
                .andExpect(jsonPath("$[0].field").value("unitCost"))
                .andExpect(jsonPath("$[1].field").value("name"));
        assertThat(ApiDtos.MaterialView.from(materials.findById(id).orElseThrow())).isEqualTo(beforeQuery);
    }

    private org.springframework.test.web.servlet.ResultActions move(long id, String type, String quantity, String reason)
            throws Exception {
        return api.perform(post("/api/movements").with(httpBasic("operador", "test-operator-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"materialId":%d,"type":"%s","quantity":%s,"reason":"%s"}
                        """.formatted(id, type, quantity, reason)));
    }
}