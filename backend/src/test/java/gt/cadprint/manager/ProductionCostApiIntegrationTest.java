package gt.cadprint.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
        "spring.datasource.url=jdbc:h2:mem:production-cost-api-${random.uuid}",
        "CADPRINT_ADMIN_PASSWORD=test-admin-password",
        "CADPRINT_OPERATOR_PASSWORD=test-operator-password"
})
@AutoConfigureMockMvc
class ProductionCostApiIntegrationTest {
    private static final String BASE = "/api/production-costs";
    private static final RequestPostProcessor ADMIN = httpBasic("admin", "test-admin-password");
    private static final RequestPostProcessor OPERATOR = httpBasic("operador", "test-operator-password");

    @Autowired MockMvc api;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired MaterialRepo materials;

    @Test
    void fdmPreviewAndSaveUseExactConversionsAndSeparateLaborFromMachineTime() throws Exception {
        var profile = profile("FDM-RATES");
        long machineId = machine("FDM-MACHINE", "FDM");
        long materialId = material("FDM-PLA", "kg", "80");
        ObjectNode input = fdm(common(profile.path("id").asLong(), machineId, materialId, "250", "g"));
        ((ObjectNode) input.get("common")).put("chosenUnitPrice", "18.0049");
        var before = counts();

        JsonNode estimate = body(send(post(BASE + "/fdm/preview"), OPERATOR, input).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCost").value(38.20))
                .andExpect(jsonPath("$.unitCost").value(19.10))
                .andExpect(jsonPath("$.indirectCost").value(3))
                .andExpect(jsonPath("$.lines[0].amount").value(20))
                .andExpect(jsonPath("$.lines[0].conversionFactor").value(0.001))
                .andExpect(jsonPath("$.lines[1].amount").value(0.20))
                .andExpect(jsonPath("$.lines[2].amount").value(10))
                .andExpect(jsonPath("$.lines[3].amount").value(5))
                .andExpect(jsonPath("$.price.suggestedUnitPrice").value(23.88))
                .andExpect(jsonPath("$.price.grossProfitPerUnit").value(-1.10))
                .andExpect(jsonPath("$.price.actualMarginPct").value(-6.1111))
                .andExpect(jsonPath("$.price.belowCost").value(true)));
        assertThat(counts()).isEqualTo(before);

        send(post(BASE + "/fdm"), ADMIN, save(input, estimate)).andExpect(status().isConflict());
        long id = body(send(post(BASE + "/fdm"), OPERATOR, save(input, estimate)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.calculation.username").value("operador"))
                .andExpect(jsonPath("$.fdm.printSeconds").value(3600))
                .andExpect(jsonPath("$.price.chosenUnitPrice").value(18))).at("/calculation/id").asLong();
        assertThat(counts()).containsEntry("production_calculations", before.get("production_calculations") + 1)
                .containsEntry("production_fdm_details", before.get("production_fdm_details") + 1)
                .containsEntry("production_cost_lines", before.get("production_cost_lines") + 5)
                .containsEntry("production_price_estimates", before.get("production_price_estimates") + 1)
                .containsEntry("movements", before.get("movements"));
        api.perform(get(BASE + "/calculations?technique=FDM&size=1").with(ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id));
        api.perform(put(BASE + "/calculations/" + id).with(ADMIN)).andExpect(status().isMethodNotAllowed());
        api.perform(delete(BASE + "/calculations/" + id).with(ADMIN)).andExpect(status().isMethodNotAllowed());
        Material material = materials.findById(materialId).orElseThrow();
        assertThat(material.stock).isZero();
        material.unitCost = new BigDecimal("100");
        materials.saveAndFlush(material);
        api.perform(get(BASE + "/calculations/" + id).with(OPERATOR)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].appliedRate").value(80))
                .andExpect(jsonPath("$.price.suggestedUnitPrice").value(23.88));
    }

    @Test
    void sublimationUsesTotalMaterialsAndAllowsZeroSellingPrice() throws Exception {
        long profile = profile("SUB-RATES").path("id").asLong();
        long machine = machine("SUB-PRESS", "SUBLIMATION");
        long ink = material("SUB-INK", "l", "10");
        ObjectNode common = common(profile, machine, ink, "600", "ml")
                .put("productiveSeconds", 3600).put("chosenUnitPrice", "0");
        ObjectNode input = json.createObjectNode().put("cycleCount", 2).put("pressSecondsPerCycle", 1800)
                .put("preparationSeconds", 30).put("laborSeconds", 1800);
        input.set("common", common);
        JsonNode preview = body(send(post(BASE + "/sublimation/preview"), ADMIN, input).andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].amount").value(6))
                .andExpect(jsonPath("$.lines[2].amount").value(10))
                .andExpect(jsonPath("$.lines[3].amount").value(10))
                .andExpect(jsonPath("$.totalCost").value(32.20))
                .andExpect(jsonPath("$.unitCost").value(16.10))
                .andExpect(jsonPath("$.price.chosenUnitPrice").value(0))
                .andExpect(jsonPath("$.price.actualMarginPct").isEmpty())
                .andExpect(jsonPath("$.price.belowCost").value(true)));
        send(post(BASE + "/sublimation"), ADMIN, save(input, preview)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.sublimation.cycleCount").value(2))
                .andExpect(jsonPath("$.price.actualMarginPct").isEmpty());
    }

    @Test
    void checksSourceValidityAndRequiresZeroCostConfirmationWithJustification() throws Exception {
        long profile = profile("CHECK-RATES").path("id").asLong();
        long machine = machine("CHECK-FDM", "FDM");
        long press = machine("CHECK-PRESS", "SUBLIMATION");
        long material = material("ZERO-FDM", "kg", "0");
        ObjectNode common = common(profile, machine, material, "1", "g");
        ObjectNode input = fdm(common);
        send(post(BASE + "/fdm/preview"), OPERATOR, input).andExpect(status().isBadRequest());
        ObjectNode line = (ObjectNode) common.get("materials").get(0);
        line.put("zeroCostReason", "Muestra sin costo");
        send(post(BASE + "/fdm/preview"), OPERATOR, input).andExpect(status().isBadRequest());
        line.put("acceptZeroCost", true);
        send(post(BASE + "/fdm/preview"), OPERATOR, input).andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].amount").value(0))
                .andExpect(jsonPath("$.lines[0].description").value(org.hamcrest.Matchers.containsString("Muestra sin costo")));
        line.put("unit", "ml");
        send(post(BASE + "/fdm/preview"), OPERATOR, input).andExpect(status().isBadRequest());
        line.put("unit", "g");
        common.put("machineId", press);
        send(post(BASE + "/fdm/preview"), OPERATOR, input).andExpect(status().isBadRequest());
        common.put("machineId", machine);
        common.put("productId", 999999);
        send(post(BASE + "/fdm/preview"), OPERATOR, input).andExpect(status().isNotFound());
        common.put("variantId", 999999);
        send(post(BASE + "/fdm/preview"), OPERATOR, input).andExpect(status().isBadRequest());
        common.remove(List.of("productId", "variantId"));
        common.put("targetMarginPct", 100);
        send(post(BASE + "/fdm/preview"), OPERATOR, input).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsStalePreviewsAndRollsBackTheEntireHistoryOnLateFailure() throws Exception {
        JsonNode profile = profile("ROLLBACK-RATES");
        long profileId = profile.path("id").asLong();
        long machine = machine("ROLLBACK-FDM", "FDM");
        long material = material("ROUNDING-PLA", "kg", "5000000");
        ObjectNode common = common(profileId, machine, material, "0.000001", "g").put("chosenUnitPrice", "123.45");
        ObjectNode input = fdm(common);
        JsonNode preview = body(send(post(BASE + "/fdm/preview"), OPERATOR, input).andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].amount").value(0.01)));
        var before = counts();
        send(put(BASE + "/profiles/" + profileId), ADMIN,
                profilePayload("ROLLBACK-RATES").put("version", profile.path("version").asLong())
                        .put("electricityRatePerKwh", "3"))
                .andExpect(status().isOk());
        send(post(BASE + "/fdm"), OPERATOR, save(input, preview)).andExpect(status().isConflict());
        assertThat(counts()).isEqualTo(before);

        JsonNode current = body(send(post(BASE + "/fdm/preview"), OPERATOR, input).andExpect(status().isOk()));
        jdbc.execute("ALTER TABLE production_price_estimates ADD CONSTRAINT test_cost_rollback CHECK (chosen_unit_price <> 123.45)");
        try {
            send(post(BASE + "/fdm"), OPERATOR, save(input, current)).andExpect(status().isConflict());
            assertThat(counts()).isEqualTo(before);
        } finally {
            jdbc.execute("ALTER TABLE production_price_estimates DROP CONSTRAINT test_cost_rollback");
        }
    }

    @Test
    void allowsBothRolesToReadSettingsAndOnlyAdminToWriteThem() throws Exception {
        api.perform(get(BASE + "/profiles")).andExpect(status().isUnauthorized());
        api.perform(get(BASE + "/profiles").with(OPERATOR)).andExpect(status().isOk());
        api.perform(get(BASE + "/machines?technique=FDM").with(OPERATOR)).andExpect(status().isOk());
        send(post(BASE + "/profiles"), OPERATOR, profilePayload("NO-ACCESS")).andExpect(status().isForbidden());
        send(post(BASE + "/machines"), OPERATOR, machinePayload("NO-ACCESS", "FDM")).andExpect(status().isForbidden());
        JsonNode profile = profile("PERMISSION-PROFILE");
        send(put(BASE + "/profiles/" + profile.path("id").asLong()), OPERATOR,
                profilePayload("PERMISSION-PROFILE").put("version", profile.path("version").asLong()))
                .andExpect(status().isForbidden());
        long machine = machine("PERMISSION-MACHINE", "FDM");
        send(put(BASE + "/machines/" + machine), OPERATOR, machinePayload("PERMISSION-MACHINE", "FDM").put("version", 0))
                .andExpect(status().isForbidden());
    }

    private ObjectNode common(long profile, long machine, long material, String quantity, String unit) {
        ObjectNode input = json.createObjectNode().put("jobDescription", "Pieza de prueba")
                .put("expectedGoodUnits", 2).put("costProfileId", profile).put("machineId", machine)
                .put("productiveSeconds", 1800).put("targetMarginPct", "20");
        input.putArray("materials").addObject().put("materialId", material).put("quantity", quantity).put("unit", unit);
        return input;
    }

    private ObjectNode fdm(ObjectNode common) {
        var input = json.createObjectNode().put("printSeconds", 3600).put("laborSeconds", 900)
                .put("partMassGrams", "200").put("supportMassGrams", "50");
        input.set("common", common);
        return input;
    }

    private ObjectNode save(ObjectNode input, JsonNode preview) {
        var request = json.createObjectNode().put("previewToken", preview.path("previewToken").asText());
        request.set("input", input);
        return request;
    }

    private JsonNode profile(String code) throws Exception {
        return body(send(post(BASE + "/profiles"), ADMIN, profilePayload(code)).andExpect(status().isCreated()));
    }

    private ObjectNode profilePayload(String code) {
        return json.createObjectNode().put("code", code).put("name", code).put("active", true)
                .put("electricityRatePerKwh", "2").put("laborRatePerHour", "20").put("indirectRatePerHour", "6");
    }

    private long machine(String code, String technique) throws Exception {
        return body(send(post(BASE + "/machines"), ADMIN, machinePayload(code, technique))
                .andExpect(status().isCreated())).path("id").asLong();
    }

    private ObjectNode machinePayload(String code, String technique) {
        return json.createObjectNode().put("code", code).put("name", code).put("active", true)
                .put("technique", technique).put("nominalPowerWatts", 100).put("machineRatePerHour", "10");
    }

    private long material(String sku, String unit, String cost) throws Exception {
        return body(send(post("/api/materials"), OPERATOR, json.createObjectNode().put("sku", sku)
                .put("name", sku).put("category", "Filamento").put("unit", unit).put("unitCost", cost))
                .andExpect(status().isCreated())).path("id").asLong();
    }

    private Map<String, Long> counts() {
        var counts = new java.util.HashMap<String, Long>();
        for (String table : List.of("production_calculations", "production_fdm_details",
                "production_sublimation_details", "production_cost_lines", "production_price_estimates", "movements")) {
            counts.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
        }
        return counts;
    }

    private ResultActions send(MockHttpServletRequestBuilder request, RequestPostProcessor actor, JsonNode payload)
            throws Exception {
        return api.perform(request.with(actor).contentType(MediaType.APPLICATION_JSON).content(payload.toString()));
    }

    private JsonNode body(ResultActions response) throws Exception {
        return json.readTree(response.andReturn().getResponse().getContentAsString());
    }
}
