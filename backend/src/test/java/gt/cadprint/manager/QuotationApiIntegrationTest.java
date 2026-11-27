package gt.cadprint.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
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
        "spring.datasource.url=jdbc:h2:mem:quotation-api-${random.uuid}",
        "cadprint.quotation-documents.worker-enabled=false",
        "CADPRINT_ADMIN_PASSWORD=test-admin-password",
        "CADPRINT_OPERATOR_PASSWORD=test-operator-password"
})
@AutoConfigureMockMvc
class QuotationApiIntegrationTest {
    private static final String BASE = "/api/quotations";
    private static final RequestPostProcessor ADMIN = httpBasic("admin", "test-admin-password");
    private static final RequestPostProcessor OPERATOR = httpBasic("operador", "test-operator-password");
    private static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");

    @Autowired MockMvc api;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProductRepo products;
    @Autowired ProductCategoryRepo categories;
    @Autowired ProductVariantRepo variants;

    @Test
    void recordsOrderPaymentsWithoutDuplicatingOrExceedingTheBalance() throws Exception {
        var issued = emit(create("Cliente de cobros"), 1);
        var revision = path(issued, 1);
        var paymentPath = revision + "/order/payments";
        send(post(revision + "/accept"), OPERATOR, null).andExpect(status().isOk());
        send(post(revision + "/order"), OPERATOR, null).andExpect(status().isOk());
        send(get(paymentPath), OPERATOR, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(25)).andExpect(jsonPath("$.balance").value(25));

        var deposit = json.createObjectNode().put("requestId", java.util.UUID.randomUUID().toString())
                .put("amount", "12.50").put("method", "EFECTIVO");
        Callable<JsonNode> pay = () -> body(send(post(paymentPath), OPERATOR, deposit).andExpect(status().isOk())
                .andExpect(jsonPath("$.paid").value(12.5)).andExpect(jsonPath("$.payments.length()").value(1)));
        var duplicate = race(pay, pay);
        assertThat(duplicate).containsExactly(duplicate.getFirst(), duplicate.getFirst());
        send(post(paymentPath), OPERATOR, deposit.deepCopy().put("method", "TRANSFERENCIA"))
                .andExpect(status().isConflict());

        var first = deposit.deepCopy().put("requestId", java.util.UUID.randomUUID().toString()).put("method", "TRANSFERENCIA");
        var second = first.deepCopy().put("requestId", java.util.UUID.randomUUID().toString());
        var statuses = race(
                () -> send(post(paymentPath), OPERATOR, first).andReturn().getResponse().getStatus(),
                () -> send(post(paymentPath), OPERATOR, second).andReturn().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        send(get(paymentPath), OPERATOR, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.paid").value(25)).andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.payments.length()").value(2));
    }

    @Test
    void recordsProductionConsumptionOnceAndRejectsInsufficientStock() throws Exception {
        var material = json.createObjectNode().put("sku", "PROD-USAGE-PLA").put("name", "PLA prueba")
                .put("category", "Filamento").put("unit", "g");
        long materialId = body(send(post("/api/materials"), OPERATOR, material)
                .andExpect(status().isCreated())).path("id").asLong();
        var entry = json.createObjectNode().put("materialId", materialId).put("type", "ENTRADA")
                .put("quantity", 10).put("reason", "Stock de prueba");
        send(post("/api/movements"), OPERATOR, entry).andExpect(status().isCreated());

        var issued = emit(create("Cliente de consumo"), 1);
        var orderPath = path(issued, 1) + "/order";
        var consumptionPath = orderPath + "/production/consumptions";
        send(post(path(issued, 1) + "/accept"), OPERATOR, null).andExpect(status().isOk());
        send(post(orderPath), OPERATOR, null).andExpect(status().isOk());
        var consumption = json.createObjectNode().put("requestId", java.util.UUID.randomUUID().toString())
                .put("materialId", materialId).put("quantity", "2.500");
        send(post(consumptionPath), OPERATOR, consumption).andExpect(status().isConflict());
        send(post(orderPath + "/production/start"), OPERATOR, null).andExpect(status().isOk());

        Callable<JsonNode> register = () -> body(send(post(consumptionPath), OPERATOR, consumption)
                .andExpect(status().isOk()).andExpect(jsonPath("$.afterStock").value(7.5)));
        var results = race(register, register);
        assertThat(results).containsExactly(results.getFirst(), results.getFirst());
        var different = consumption.deepCopy().put("quantity", "1.000");
        send(post(consumptionPath), OPERATOR, different).andExpect(status().isConflict());
        var tooMuch = consumption.deepCopy().put("requestId", java.util.UUID.randomUUID().toString())
                .put("quantity", "8.000");
        send(post(consumptionPath), OPERATOR, tooMuch).andExpect(status().isConflict());
        send(get(consumptionPath), OPERATOR, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].movementId").value(results.getFirst().path("movementId").asLong()));
        assertThat(jdbc.queryForObject("SELECT stock FROM materials WHERE id = ?", java.math.BigDecimal.class,
                materialId)).isEqualByComparingTo("7.500");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movements WHERE material_id = ? AND type = 'SALIDA'",
                Integer.class, materialId)).isEqualTo(1);
    }

    @Test
    void startsProductionOnceAndPreservesTheOriginalOperator() throws Exception {
        var issued = emit(create("Cliente de producción"), 1);
        var orderPath = path(issued, 1) + "/order";
        var productionPath = orderPath + "/production/start";
        send(post(productionPath), OPERATOR, null).andExpect(status().isNotFound());
        send(post(path(issued, 1) + "/accept"), OPERATOR, null).andExpect(status().isOk());
        send(post(orderPath), OPERATOR, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.productionStartedAt").doesNotExist());

        Callable<JsonNode> start = () -> body(send(post(productionPath), OPERATOR, null)
                .andExpect(status().isOk()));
        var results = race(start, start);
        var started = results.getFirst();
        assertThat(results).containsExactly(started, started);
        assertThat(started.path("productionStartedAt").asText()).isNotBlank();
        assertThat(started.path("productionStartedBy").asText()).isEqualTo("operador");
        assertThat(body(send(post(productionPath), ADMIN, null).andExpect(status().isOk()))).isEqualTo(started);
        assertThat(body(send(get(orderPath), ADMIN, null).andExpect(status().isOk()))).isEqualTo(started);
    }

    @Test
    void createsOneOrderFromAnAcceptedRevisionEvenWithConcurrentRequests() throws Exception {
        var orderDraft = draft("Cliente del pedido");
        ((ObjectNode) orderDraft.path("lines").get(0)).put("description", "Taza personalizada");
        var draft = body(send(post(BASE), OPERATOR, orderDraft).andExpect(status().isCreated()));
        var issued = emit(draft, 1);
        var orderPath = path(issued, 1) + "/order";
        send(get(orderPath), OPERATOR, null).andExpect(status().isNotFound());
        send(post(orderPath), OPERATOR, null).andExpect(status().isConflict());
        revise(issued, 1);
        send(post(path(issued, 1) + "/accept"), OPERATOR, null).andExpect(status().isOk());
        send(get(BASE + "/" + id(issued)), OPERATOR, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.revision.revisionNumber").value(2))
                .andExpect(jsonPath("$.revision.status").value("BORRADOR"));
        send(post(path(issued, 2) + "/order"), OPERATOR, null).andExpect(status().isConflict());

        Callable<JsonNode> createOrder = () -> body(send(post(orderPath), OPERATOR, null)
                .andExpect(status().isOk()));
        var results = race(createOrder, createOrder);
        long orderId = results.getFirst().path("id").asLong();
        assertThat(orderId).isPositive();
        assertThat(results).containsExactly(results.getFirst(), results.getFirst());
        assertThat(createOrder.call()).isEqualTo(results.getFirst());
        var retrieved = body(send(get(orderPath), OPERATOR, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.quotation.revisionNumber").value(1))
                .andExpect(jsonPath("$.quotation.recipient.name").value("Cliente del pedido"))
                .andExpect(jsonPath("$.quotation.lines[0].quantity").value(1))
                .andExpect(jsonPath("$.quotation.lines[0].description").value("Taza personalizada"))
                .andExpect(jsonPath("$.quotation.totals.totalAmount").value(25)));
        assertThat(retrieved).isEqualTo(results.getFirst());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_orders WHERE quotation_id = ?",
                Integer.class, id(issued))).isEqualTo(1);
    }

    @Test
    void linksRegisteredCustomersWithoutChangingIssuedRecipientSnapshots() throws Exception {
        var customerInput = json.createObjectNode().put("name", "Cliente original").put("phone", "55550000");
        var customer = body(send(post("/api/customers"), OPERATOR, customerInput)
                .andExpect(status().isCreated()));
        long customerId = customer.path("id").asLong();
        var linkedDraft = draft("Sin usar");
        linkedDraft.remove("recipient");
        linkedDraft.put("customerId", customerId);
        var created = body(send(post(BASE), OPERATOR, linkedDraft).andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(customerId))
                .andExpect(jsonPath("$.recipient.name").value("Cliente original")));
        var issued = emit(created, 1);

        customerInput.put("name", "Cliente actualizado");
        send(put("/api/customers/" + customerId), OPERATOR, customerInput)
                .andExpect(status().isOk());
        send(get("/api/customers"), OPERATOR, null)
                .andExpect(status().isOk());
        var copy = revise(issued, 1);
        assertThat(copy.path("customerId").asLong()).isEqualTo(customerId);
        send(get(path(created, 1)), OPERATOR, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.recipient.name").value("Cliente original"))
                .andExpect(jsonPath("$.recipient.phone").value("55550000"));

        linkedDraft.put("customerId", Long.MAX_VALUE);
        send(post(BASE), OPERATOR, linkedDraft).andExpect(status().isBadRequest());
    }

    @Test
    void editsAndReordersMultipleLinesWithExactDiscountsAndCatalogSnapshot() throws Exception {
        Product product = product("Q-CATALOG");
        var variant = new ProductVariant(product, "Q-CATALOG-BLUE");
        variant.name = "Azul";
        variants.saveAndFlush(variant);

        var draft = draft("Cliente monetario");
        var items = draft.putArray("lines");
        items.addObject().put("type", "CATALOG").put("variantId", variant.id).put("quantity", 2)
                .putObject("discount").put("type", "PERCENTAGE").put("value", 10);
        items.addObject().put("type", "CUSTOM").put("description", "Personalizado").put("quantity", 1)
                .put("unitPrice", "50.05").putObject("discount").put("type", "FIXED").put("value", "0.05");
        items.addObject().put("type", "CUSTOM").put("description", "Muestra").put("quantity", 1).put("unitPrice", 0);
        draft.putObject("generalDiscount").put("type", "PERCENTAGE").put("value", "5.0022");
        draft.put("shippingAmount", "20.00");
        var created = body(send(post(BASE), OPERATOR, draft).andExpect(status().isCreated())
                .andExpect(jsonPath("$.revision.quotationNumber").doesNotExist())
                .andExpect(jsonPath("$.lines[0].commercial.sku").value("Q-CATALOG-BLUE"))
                .andExpect(jsonPath("$.lines[0].commercial.unitPrice").value(100))
                .andExpect(jsonPath("$.lines[2].commercial.unitPrice").value(0))
                .andExpect(jsonPath("$.totals.grossAmount").value(250.05))
                .andExpect(jsonPath("$.totals.lineDiscountTotal").value(20.05))
                .andExpect(jsonPath("$.totals.subtotal").value(230))
                .andExpect(jsonPath("$.totals.generalDiscountAmount").value(11.51))
                .andExpect(jsonPath("$.totals.totalAmount").value(238.49)));

        var edit = version(created);
        var reordered = items.remove(2);
        items.insert(0, reordered);
        items.remove(2);
        edit.set("draft", draft);
        var updated = body(send(put(path(created, 1) + "/draft"), OPERATOR, edit).andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].commercial.description").value("Muestra"))
                .andExpect(jsonPath("$.lines[1].commercial.position").value(2)));
        assertThat(versionNumber(updated)).isGreaterThan(versionNumber(created));
        send(put(path(created, 1) + "/draft"), OPERATOR, edit).andExpect(status().isConflict());
        assertThat(products.findById(product.id).orElseThrow().basePrice).isEqualByComparingTo("100");
        send(get(BASE).param("recipient", "MONETARIO").param("status", "BORRADOR").param("size", "1"), OPERATOR, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void roundsHalfUpAndRejectsExcessiveDiscountsBeforePersisting() throws Exception {
        var draft = draft("Redondeo");
        var line = (ObjectNode) draft.path("lines").get(0);
        line.put("unitPrice", "0.05");
        line.putObject("discount").put("type", "PERCENTAGE").put("value", "10.0000");
        send(post(BASE), OPERATOR, draft).andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].commercial.discountAmount").value(0.01))
                .andExpect(jsonPath("$.totals.totalAmount").value(0.04));
        line.putObject("discount").put("type", "FIXED").put("value", "0.06");
        send(post(BASE), OPERATOR, draft).andExpect(status().isBadRequest());
        line.remove("discount");
        line.put("quantity", "1.5");
        send(post(BASE), OPERATOR, draft).andExpect(status().isBadRequest());
        line.put("quantity", 1);
        draft.putObject("generalDiscount").put("type", "FIXED").put("value", "0.06");
        send(post(BASE), OPERATOR, draft).andExpect(status().isBadRequest());
        ((ObjectNode) draft.path("generalDiscount")).put("value", "1E+1000000");
        send(post(BASE), OPERATOR, draft).andExpect(status().isBadRequest());
    }

    @Test
    void emitsOnceAndReplacesOnlyWhenTheNewRevisionIsIssued() throws Exception {
        var original = create("Revisión");
        var issued = emit(original, 1);
        String number = issued.at("/revision/quotationNumber").asText();
        assertThat(number).matches("COT-[0-9]{6,}");
        var issuedDay = java.time.Instant.parse(issued.at("/revision/issuedAt").asText()).atZone(GUATEMALA).toLocalDate();
        assertThat(issued.at("/revision/validUntil").asText()).isEqualTo(issuedDay.plusDays(15).toString());
        assertThat(emit(original, 1).at("/revision/quotationNumber").asText()).isEqualTo(number);
        assertThat(eventCount(original, "ISSUED")).isEqualTo(1);
        send(put(path(original, 1) + "/draft"), OPERATOR, version(issued).set("draft", draft("Cambio")))
                .andExpect(status().isConflict());

        var revision = revise(issued, 1);
        send(get(path(original, 1)), OPERATOR, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.revision.replacedAt").doesNotExist());
        send(post(path(original, 1) + "/revise"), OPERATOR, version(revision)).andExpect(status().isConflict());
        var revisedIssue = emit(revision, 2);
        assertThat(revisedIssue.at("/revision/quotationNumber").asText()).isEqualTo(number);
        send(get(path(original, 1)), OPERATOR, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.replacementRevisionNumber").value(2))
                .andExpect(jsonPath("$.lines[0].commercial.description").value("Servicio personalizado"));
        send(post(path(original, 1) + "/accept"), OPERATOR, null).andExpect(status().isConflict());
        send(post(path(original, 2) + "/accept"), OPERATOR, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.revision.status").value("ACEPTADA"));
        send(get(BASE + "/" + id(original) + "/revisions"), OPERATOR, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        send(get(BASE + "/" + id(original) + "/events"), OPERATOR, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actor").value("operador"));
    }

    @Test
    void enforcesPermissionsRejectionCancellationAndExpiry() throws Exception {
        api.perform(get(BASE)).andExpect(status().isUnauthorized());
        send(get(BASE + "/999999"), OPERATOR, null).andExpect(status().isNotFound());
        var cancelled = emit(create("Cancelación"), 1);
        var reason = json.createObjectNode().put("reason", "Cambio solicitado");
        send(post(path(cancelled, 1) + "/cancel"), OPERATOR, reason).andExpect(status().isForbidden());
        send(post(path(cancelled, 1) + "/cancel"), ADMIN, json.createObjectNode()).andExpect(status().isBadRequest());
        send(post(path(cancelled, 1) + "/cancel"), ADMIN, reason).andExpect(status().isOk())
                .andExpect(jsonPath("$.revision.status").value("CANCELADA"));
        var rejected = emit(create("Rechazo"), 1);
        send(post(path(rejected, 1) + "/reject"), OPERATOR, reason).andExpect(status().isOk())
                .andExpect(jsonPath("$.revision.status").value("RECHAZADA"));
        var expired = emit(create("Vencimiento"), 1);
        send(post(path(expired, 1) + "/expire"), OPERATOR, null).andExpect(status().isConflict());
        jdbc.update("UPDATE quotation_revisions SET valid_until = ? WHERE quotation_id = ?",
                LocalDate.now(GUATEMALA).minusDays(1), id(expired));
        send(post(path(expired, 1) + "/accept"), OPERATOR, null).andExpect(status().isConflict());
        send(post(path(expired, 1) + "/expire"), OPERATOR, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.revision.status").value("VENCIDA"));
    }

    @Test
    void retainsButBlocksAnOutstandingDraftAfterAcceptance() throws Exception {
        var issued = emit(create("Cierre"), 1);
        var draft = revise(issued, 1);
        var accepted = body(send(post(path(issued, 1) + "/accept"), OPERATOR, null).andExpect(status().isOk()));
        send(post(path(draft, 2) + "/emit"), OPERATOR, version(accepted)).andExpect(status().isConflict());
        send(post(path(issued, 1) + "/revise"), OPERATOR, version(accepted)).andExpect(status().isConflict());
        send(get(path(draft, 2)), OPERATOR, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.revision.status").value("BORRADOR"));
    }

    @Test
    void warnsOnBelowCostWithoutExposingProductionReferencesInCommercialView() throws Exception {
        Product product = product("Q-COST");
        Product other = product("Q-WRONG-COST");
        jdbc.update("""
                INSERT INTO production_calculations (id, technique, formula_version, username, created_at,
                    product_id, commercial_name, commercial_sku, job_description, expected_good_units, currency, productive_seconds,
                    indirect_rate_per_hour, indirect_cost, total_cost, unit_cost)
                VALUES (81001, 'FDM', 'v1', 'operador', CURRENT_TIMESTAMP, ?, 'Producto', 'Q-COST', 'Pieza', 10, 'GTQ', 3600, 0, 0, 100, 10)
                """, product.id);
        var draft = draft("Costos");
        var line = draft.putArray("lines").addObject().put("type", "CATALOG").put("productId", other.id)
                .put("productionCalculationId", 81001).put("quantity", 2).put("unitPrice", 11)
                .putObject("discount");
        line.put("type", "FIXED").put("value", 3);
        send(post(BASE), OPERATOR, draft).andExpect(status().isBadRequest());
        ((ObjectNode) draft.path("lines").get(0)).put("productId", product.id);
        var created = body(send(post(BASE), OPERATOR, draft).andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].belowHistoricalCost").value(true)));
        var issued = emit(created, 1);
        var commercial = body(send(get(path(issued, 1) + "/commercial"), OPERATOR, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.lines[0].netAmount").value(19)));
        assertThat(commercial.toString()).doesNotContain("productionCalculationId", "belowHistoricalCost",
                "unitCost", "actor", "version", "notes", "margin");
        assertThat(jdbc.queryForObject("SELECT expected_good_units FROM production_calculations WHERE id = 81001",
                Integer.class)).isEqualTo(10);
    }

    @Test
    void simultaneousIssueIsIdempotentAndOtherQuotesReceiveDistinctNumbers() throws Exception {
        var draft = create("Emisión concurrente");
        var outcomes = race(() -> emit(draft, 1).at("/revision/quotationNumber").asText(),
                () -> emit(draft, 1).at("/revision/quotationNumber").asText());
        assertThat(outcomes.getFirst()).isEqualTo(outcomes.getLast());
        assertThat(eventCount(draft, "ISSUED")).isEqualTo(1);
        var second = create("Segundo");
        var third = create("Tercero");
        var distinct = race(() -> emit(second, 1).at("/revision/quotationNumber").asText(),
                () -> emit(third, 1).at("/revision/quotationNumber").asText());
        assertThat(distinct).doesNotHaveDuplicates();
    }

    @Test
    void concurrentAcceptanceAndReplacementCannotBothSucceed() throws Exception {
        var issued = emit(create("Aceptación concurrente"), 1);
        var draft = revise(issued, 1);
        List<Integer> outcomes = race(
                () -> send(post(path(issued, 1) + "/accept"), OPERATOR, null).andReturn().getResponse().getStatus(),
                () -> send(post(path(draft, 2) + "/emit"), OPERATOR, version(draft)).andReturn().getResponse().getStatus());
        assertThat(outcomes).containsExactlyInAnyOrder(200, 409);
        assertThat(eventCount(issued, "ACCEPTED") + eventCount(issued, "REPLACED")).isEqualTo(1);
        assertThat(eventCount(issued, "ISSUED")).isEqualTo(outcomes.getFirst() == 200 ? 1 : 2);
    }

    @Test
    void rollsBackIssuanceWhenTheDatabaseRejectsTheNumber() throws Exception {
        var draft = create("Rollback");
        var blocker = create("Número ocupado");
        jdbc.update("UPDATE quotations SET quotation_number = ? WHERE id = ?",
                String.format(java.util.Locale.ROOT, "COT-%06d", id(draft)), id(blocker));

        send(post(path(draft, 1) + "/emit"), OPERATOR, version(draft)).andExpect(status().isConflict());
        send(get(path(draft, 1)), OPERATOR, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.revision.status").value("BORRADOR"))
                .andExpect(jsonPath("$.revision.issuedAt").doesNotExist())
                .andExpect(jsonPath("$.revision.quotationNumber").doesNotExist())
                .andExpect(jsonPath("$.issuer.name").doesNotExist())
                .andExpect(jsonPath("$.revision.version").value(versionNumber(draft)))
                .andExpect(jsonPath("$.lines.length()").value(1));
        assertThat(eventCount(draft, "ISSUED")).isZero();
        jdbc.update("UPDATE quotations SET quotation_number = NULL WHERE id = ?", id(blocker));
        emit(draft, 1);
    }

    private <T> List<T> race(Callable<T> first, Callable<T> second) throws Exception {
        var gate = new CyclicBarrier(2);
        try (var threads = Executors.newFixedThreadPool(2)) {
            var left = threads.submit(() -> { gate.await(10, TimeUnit.SECONDS); return first.call(); });
            var right = threads.submit(() -> { gate.await(10, TimeUnit.SECONDS); return second.call(); });
            return List.of(left.get(25, TimeUnit.SECONDS), right.get(25, TimeUnit.SECONDS));
        }
    }

    private int eventCount(JsonNode detail, String type) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM quotation_events e JOIN quotation_revisions r ON r.id = e.revision_id
                WHERE r.quotation_id = ? AND e.event_type = ?
                """, Integer.class, id(detail), type);
    }

    private Product product(String sku) {
        var category = new ProductCategory();
        category.name = sku;
        var product = new Product(sku);
        product.name = sku;
        product.category = categories.saveAndFlush(category);
        product.productionMode = ProductionMode.BAJO_PEDIDO;
        product.pricingMode = PricingMode.PRECIO_BASE;
        product.basePrice = new BigDecimal("100.00");
        return products.saveAndFlush(product);
    }

    private JsonNode create(String recipient) throws Exception {
        return body(send(post(BASE), OPERATOR, draft(recipient)).andExpect(status().isCreated()));
    }

    private JsonNode emit(JsonNode current, int number) throws Exception {
        return body(send(post(path(current, number) + "/emit"), OPERATOR, version(current)).andExpect(status().isOk()));
    }

    private JsonNode revise(JsonNode current, int number) throws Exception {
        return body(send(post(path(current, number) + "/revise"), OPERATOR, version(current))
                .andExpect(status().isCreated()));
    }

    private ObjectNode draft(String recipient) {
        var draft = json.createObjectNode();
        draft.putObject("recipient").put("name", recipient);
        draft.putArray("lines").addObject().put("type", "CUSTOM").put("description", "Servicio personalizado")
                .put("quantity", 1).put("unitPrice", 25);
        return draft;
    }

    private ObjectNode version(JsonNode detail) {
        return json.createObjectNode().put("version", versionNumber(detail));
    }

    private long versionNumber(JsonNode detail) {
        return detail.at("/revision/version").asLong();
    }

    private long id(JsonNode detail) {
        return detail.at("/revision/quotationId").asLong();
    }

    private String path(JsonNode detail, int revision) {
        return BASE + "/" + id(detail) + "/revisions/" + revision;
    }

    private ResultActions send(MockHttpServletRequestBuilder request, RequestPostProcessor role, JsonNode input)
            throws Exception {
        if (input != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(input));
        }
        return api.perform(request.with(role));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }
}
