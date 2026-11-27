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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:purchase-receipt-${random.uuid}",
        "CADPRINT_ADMIN_PASSWORD=test-admin-password",
        "CADPRINT_OPERATOR_PASSWORD=test-operator-password"
})
@AutoConfigureMockMvc
class PurchaseReceiptIntegrationTest {
    private static final RequestPostProcessor ADMIN = httpBasic("admin", "test-admin-password");
    private static final RequestPostProcessor OPERATOR = httpBasic("operador", "test-operator-password");
    private static final String PURCHASES = "/api/purchases";
    private static final String RECEIPT = PURCHASES + "/{id}/receipt";

    @Autowired MockMvc api;
    @Autowired ObjectMapper json;
    @Autowired MaterialRepo materials;
    @Autowired MovementRepo movements;
    @Autowired PurchaseReceiptRepo receipts;
    @Autowired PurchaseEventRepo events;
    @Autowired PurchaseLineRepo lines;

    @Test
    void receivesConfirmedMassAndVolumeAndReturnsSameReceiptOnRetry() throws Exception {
        long pla = material("RECEIVE-PLA", "g");
        long ink = material("RECEIVE-INK", "ml");
        manualEntry(pla, "10");

        long purchase = pending(line(pla, "kg", "1.25", "150"), line(ink, "l", "2.5", "30"));
        var confirmedLines = lines.findByPurchaseIdOrderByIdAsc(purchase);
        api.perform(put(RECEIPT, purchase)).andExpect(status().isUnauthorized());
        var received = body(api.perform(put(RECEIPT, purchase).with(OPERATOR)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":99999}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receivedBy").value("operador"))
                .andExpect(jsonPath("$.receivedAt").isString())
                .andExpect(jsonPath("$.movements.length()").value(2))
                .andExpect(jsonPath("$.movements[0].purchaseLineId").value(confirmedLines.getFirst().id))
                .andExpect(jsonPath("$.movements[0].materialId").value(pla))
                .andExpect(jsonPath("$.movements[0].type").value("ENTRADA"))
                .andExpect(jsonPath("$.movements[0].quantity").value(1250))
                .andExpect(jsonPath("$.movements[0].beforeStock").value(10))
                .andExpect(jsonPath("$.movements[0].afterStock").value(1260))
                .andExpect(jsonPath("$.movements[0].createdAt").isString())
                .andExpect(jsonPath("$.movements[0].username").value("operador"))
                .andExpect(jsonPath("$.movements[0].reason").value("Recepción de compra #" + purchase))
                .andExpect(jsonPath("$.movements[1].quantity").value(2500)));
        long receiptId = received.path("id").asLong();
        assertThat(receipts.findByPurchaseId(purchase).orElseThrow().id).isEqualTo(receiptId);
        assertThat(purchaseEntries(purchase)).extracting(entry -> entry.id)
                .containsExactly(received.at("/movements/0/id").asLong(), received.at("/movements/1/id").asLong());
        api.perform(put(RECEIPT, purchase).with(ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(receiptId))
                .andExpect(jsonPath("$.receivedBy").value("operador"))
                .andExpect(jsonPath("$.movements[0].id").value(received.at("/movements/0/id").asLong()));
        api.perform(get(PURCHASES + "/{id}", purchase).with(ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.purchase.status").value("RECIBIDA"))
                .andExpect(jsonPath("$.receipt.movements.length()").value(2))
                .andExpect(jsonPath("$.receipt.id").value(receiptId));
        api.perform(get(PURCHASES + "/{id}/events", purchase).with(ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("RECEIVED"))
                .andExpect(jsonPath("$[0].username").value("operador"))
                .andExpect(jsonPath("$[0].previousStatus").value("PENDIENTE"))
                .andExpect(jsonPath("$[0].newStatus").value("RECIBIDA"));
        assertThat(events.findByPurchaseIdOrderByCreatedAtDescIdDesc(purchase))
                .filteredOn(event -> event.eventType == PurchaseEventType.RECEIVED).hasSize(1);
        assertStock(pla, "1260");
        assertStock(ink, "2500");
        assertThat(materials.findById(pla).orElseThrow().unitCost).isEqualByComparingTo("0.42");
        assertThat(lines.findById(confirmedLines.getFirst().id).orElseThrow().inventoryUnitCost)
                .isEqualByComparingTo(confirmedLines.getFirst().inventoryUnitCost);
        assertThat(movements.findTop100ByOrderByCreatedAtDescIdDesc()).anySatisfy(entry -> {
            assertThat(entry.material.getId()).isEqualTo(pla);
            assertThat(entry.reason).isEqualTo("Entrada manual previa");
            assertThat(entry.purchaseLine).isNull();
        });
    }

    @Test
    void rejectsDraftAndCancelledPurchases() throws Exception {
        long material = material("RECEIVE-STATE", "g");
        long purchase = draft(line(material, "g", "1", "1")).at("/purchase/id").asLong();
        api.perform(put(RECEIPT, purchase).with(OPERATOR)).andExpect(status().isConflict());
        send(post(PURCHASES + "/{id}/cancel", purchase), ADMIN, "{\"reason\":\"Anulada\"}")
                .andExpect(status().isOk());
        api.perform(put(RECEIPT, purchase).with(ADMIN)).andExpect(status().isConflict());
        assertNoReceipt(purchase);
        assertStock(material, "0");
    }

    @Test
    void serializesConcurrentRetriesForTheSamePurchase() throws Exception {
        long material = material("RECEIVE-SAME", "g");
        long purchase = pending(line(material, "g", "20", "1"));
        assertThat(receiveConcurrently(purchase, purchase)).containsExactlyInAnyOrder(200, 201);
        assertStock(material, "20");
        assertThat(purchaseEntries(purchase)).hasSize(1);
        assertThat(events.findByPurchaseIdOrderByCreatedAtDescIdDesc(purchase))
                .filteredOn(event -> event.eventType == PurchaseEventType.RECEIVED).hasSize(1);
    }

    @Test
    void serializesDifferentPurchasesWithOverlappingMaterialsInReverseLineOrder() throws Exception {
        long first = material("RECEIVE-OVERLAP-A", "g");
        long second = material("RECEIVE-OVERLAP-B", "g");
        long left = pending(line(first, "g", "10", "1"), line(second, "g", "20", "1"));
        long right = pending(line(second, "g", "30", "1"), line(first, "g", "40", "1"));
        assertThat(receiveConcurrently(left, right)).containsExactly(201, 201);
        assertStock(first, "50");
        assertStock(second, "50");
        assertThat(purchaseEntries(left)).hasSize(2);
        assertThat(purchaseEntries(right)).hasSize(2);
        var entries = java.util.stream.Stream.concat(purchaseEntries(left).stream(), purchaseEntries(right).stream())
                .filter(entry -> entry.material.getId().equals(first)).toList();
        var starting = entries.stream().filter(entry -> entry.beforeStock.signum() == 0).findFirst().orElseThrow();
        assertThat(entries).anySatisfy(entry -> {
            assertThat(entry.beforeStock).isEqualByComparingTo(starting.afterStock);
            assertThat(entry.afterStock).isEqualByComparingTo("50");
        });
    }

    @Test
    void rollsBackFirstLineWhenSecondLineWouldOverflowStock() throws Exception {
        long first = material("RECEIVE-ROLLBACK-A", "g");
        long overflowing = material("RECEIVE-ROLLBACK-B", "g");
        manualEntry(overflowing, "9999999999999.999");
        long purchase = pending(line(first, "g", "3", "1"), line(overflowing, "g", "1", "1"));
        api.perform(put(RECEIPT, purchase).with(ADMIN)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("máximo")));
        assertPendingWithoutReceipt(purchase);
        assertStock(first, "0");
        assertStock(overflowing, "9999999999999.999");
    }

    @Test
    void rejectsInactiveMaterialsAndAnInventoryUnitChangedAfterConfirmation() throws Exception {
        long material = material("RECEIVE-CHANGED", "g");
        long purchase = pending(line(material, "kg", "1", "150"));
        updateMaterial(material, "g", false);
        api.perform(put(RECEIPT, purchase).with(OPERATOR)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("inactivo")));
        assertPendingWithoutReceipt(purchase);

        updateMaterial(material, "ml", true);
        api.perform(put(RECEIPT, purchase).with(ADMIN)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("unidad")));
        assertPendingWithoutReceipt(purchase);
        assertStock(material, "0");
    }

    private long pending(String... lines) throws Exception {
        JsonNode purchase = draft(lines);
        long id = purchase.at("/purchase/id").asLong();
        send(post(PURCHASES + "/{id}/confirm", id), OPERATOR,
                "{\"version\":" + purchase.at("/purchase/version").asLong() + "}")
                .andExpect(status().isOk());
        return id;
    }

    private JsonNode draft(String... lines) throws Exception {
        long supplier = body(send(post("/api/suppliers"), OPERATOR, "{\"name\":\"Proveedor recepción\"}")
                .andExpect(status().isCreated())).path("id").asLong();
        return body(send(post(PURCHASES), OPERATOR, """
                {"supplierId":%d,"purchaseDate":"2026-01-02","lines":[%s]}
                """.formatted(supplier, String.join(",", lines))).andExpect(status().isCreated()));
    }

    private long material(String sku, String unit) throws Exception {
        return body(send(post("/api/materials"), OPERATOR, """
                {"sku":"%s","name":"Material","category":"Filamento","unit":"%s","unitCost":"0.42"}
                """.formatted(sku, unit)).andExpect(status().isCreated())).path("id").asLong();
    }

    private void updateMaterial(long id, String unit, boolean active) throws Exception {
        send(put("/api/materials/{id}", id), ADMIN, """
                {"name":"Material","category":"Filamento","unit":"%s","unitCost":"0.42","minStock":0,"active":%s}
                """.formatted(unit, active)).andExpect(status().isOk());
    }

    private void manualEntry(long id, String quantity) throws Exception {
        send(post("/api/movements"), OPERATOR, """
                {"materialId":%d,"type":"ENTRADA","quantity":"%s","reason":"Entrada manual previa"}
                """.formatted(id, quantity)).andExpect(status().isCreated());
    }

    private String line(long material, String unit, String quantity, String price) {
        return """
                {"materialId":%d,"purchaseUnit":"%s","quantity":"%s","unitPrice":"%s"}
                """.formatted(material, unit, quantity, price);
    }

    private List<Integer> receiveConcurrently(long first, long second) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var left = workers.submit(receiveAtBarrier(first, ADMIN, barrier));
            var right = workers.submit(receiveAtBarrier(second, OPERATOR, barrier));
            return List.of(left.get(15, TimeUnit.SECONDS), right.get(15, TimeUnit.SECONDS));
        }
    }

    private Callable<Integer> receiveAtBarrier(long purchase, RequestPostProcessor actor, CyclicBarrier barrier) {
        return () -> {
            barrier.await(5, TimeUnit.SECONDS);
            return api.perform(put(RECEIPT, purchase).with(actor)).andReturn().getResponse().getStatus();
        };
    }

    private void assertPendingWithoutReceipt(long purchase) throws Exception {
        api.perform(get(PURCHASES + "/{id}", purchase).with(ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.purchase.status").value("PENDIENTE"))
                .andExpect(jsonPath("$.receipt").isEmpty());
        assertNoReceipt(purchase);
    }

    private void assertNoReceipt(long purchase) {
        assertThat(receipts.findByPurchaseId(purchase)).isEmpty();
        assertThat(purchaseEntries(purchase)).isEmpty();
        assertThat(events.findByPurchaseIdOrderByCreatedAtDescIdDesc(purchase))
                .noneMatch(event -> event.eventType == PurchaseEventType.RECEIVED);
    }

    private List<Movement> purchaseEntries(long purchase) {
        return movements.findByPurchaseLinePurchaseIdOrderByPurchaseLineIdAsc(purchase);
    }

    private void assertStock(long id, String expected) {
        assertThat(materials.findById(id).orElseThrow().stock).isEqualByComparingTo(expected);
    }

    private ResultActions send(MockHttpServletRequestBuilder builder, RequestPostProcessor actor, String payload)
            throws Exception {
        return api.perform(builder.with(actor).contentType(MediaType.APPLICATION_JSON).content(payload));
    }

    private JsonNode body(ResultActions response) throws Exception {
        return json.readTree(response.andReturn().getResponse().getContentAsString());
    }
}
