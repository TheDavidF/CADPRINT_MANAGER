package gt.cadprint.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.EntityManagerFactory;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:quotations-${random.uuid}",
        "CADPRINT_ADMIN_PASSWORD=test-admin-password",
        "CADPRINT_OPERATOR_PASSWORD=test-operator-password"
})
class QuotationPersistenceTest {
    @Autowired QuotationRepo quotations;
    @Autowired QuotationRevisionRepo revisions;
    @Autowired QuotationLineRepo lines;
    @Autowired QuotationEventRepo events;
    @Autowired ProductCategoryRepo categories;
    @Autowired ProductRepo products;
    @Autowired ProductVariantRepo variants;
    @Autowired ProductionCalculationRepo calculations;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void startsFromAnEmptyDatabaseWithMigrationV10() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("10");
        assertThat(flyway.info().applied()).hasSize(10);
    }

    @Test
    void roundTripsMultipleLinesSnapshotsEventsAndHistoricalCalculation() {
        var quote = quotation("CAD-001");
        var revision = draft(quote, 1);
        revision.recipientName = "Cliente de prueba";
        revision.recipientEmail = "cliente@example.test";
        revision.issuerName = "CADPRINT";
        revision.validUntil = LocalDate.of(2026, 12, 8);
        revisions.save(revision);

        var product = product("TEST-CATALOG");
        var variant = new ProductVariant(product, "TEST-VARIANT");
        variant.name = "Azul";
        variants.saveAndFlush(variant);
        long calculationId = insertHistoricalCalculation();
        var catalog = line(revision, 1);
        catalog.lineType = QuotationLineType.CATALOG;
        catalog.product = product;
        catalog.skuSnapshot = "TEST-CATALOG";
        catalog.nameSnapshot = "Nombre original";
        catalog.quantity = 2;
        catalog.unitPrice = new BigDecimal("20.25");
        catalog.grossAmount = new BigDecimal("40.50");
        catalog.discountType = QuotationDiscountType.PERCENTAGE;
        catalog.discountPercent = new BigDecimal("100.0000");
        catalog.discountAmount = catalog.grossAmount;
        lines.save(catalog);

        var variantLine = line(revision, 2);
        variantLine.lineType = QuotationLineType.CATALOG;
        variantLine.product = product;
        variantLine.variant = variant;
        lines.save(variantLine);

        var custom = line(revision, 3);
        custom.productionCalculation = calculations.findById(calculationId).orElseThrow();
        custom.descriptionSnapshot = "Pieza personalizada";
        lines.save(custom);

        var event = new QuotationEvent();
        event.revision = revision;
        event.actor = "operador";
        event.eventType = QuotationEventType.CREATED;
        event.newStatus = QuotationStatus.BORRADOR;
        events.save(event);
        var issued = new QuotationEvent();
        issued.revision = revision;
        issued.actor = "admin";
        issued.eventType = QuotationEventType.ISSUED;
        issued.previousStatus = QuotationStatus.BORRADOR;
        issued.newStatus = QuotationStatus.EMITIDA;
        issued.occurredAt = event.occurredAt.plusSeconds(1);
        events.save(issued);

        product.name = "Nombre actualizado";
        products.saveAndFlush(product);
        revision.status = QuotationStatus.EMITIDA;
        revision.issuedAt = Instant.now();
        revisions.save(revision);

        assertThat(quotations.findByQuotationNumber("CAD-001").orElseThrow().id).isEqualTo(quote.id);
        var persisted = revisions.findByQuotationIdAndRevisionNumber(quote.id, 1).orElseThrow();
        assertThat(persisted.recipientName).isEqualTo("Cliente de prueba");
        assertThat(persisted.recipientEmail).isEqualTo("cliente@example.test");
        assertThat(persisted.issuerName).isEqualTo("CADPRINT");
        assertThat(revisions.findAllByStatusAndValidUntilBeforeOrderByValidUntilAscIdAsc(
                QuotationStatus.EMITIDA, revision.validUntil.plusDays(1)))
                .extracting(item -> item.id).contains(revision.id);
        var storedLines = lines.findAllByRevisionIdOrderByPositionAsc(revision.id);
        assertThat(storedLines).extracting(item -> item.lineType)
                .containsExactly(QuotationLineType.CATALOG, QuotationLineType.CATALOG, QuotationLineType.CUSTOM);
        assertThat(storedLines.getFirst().nameSnapshot).isEqualTo("Nombre original");
        assertThat(storedLines.getFirst().skuSnapshot).isEqualTo("TEST-CATALOG");
        assertThat(storedLines.getFirst().discountPercent).isEqualTo(new BigDecimal("100.0000"));
        assertThat(storedLines.getLast().unitPrice).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject("SELECT production_calculation_id FROM quotation_lines WHERE id = ?",
                Long.class, custom.id)).isEqualTo(calculationId);
        assertThat(jdbc.queryForObject("SELECT variant_id FROM quotation_lines WHERE id = ?",
                Long.class, variantLine.id)).isEqualTo(variant.id);
        assertThat(events.findAllByRevisionIdOrderByOccurredAtAscIdAsc(revision.id))
                .extracting(item -> item.eventType)
                .containsExactly(QuotationEventType.CREATED, QuotationEventType.ISSUED);
        assertThat(jdbc.queryForObject("SELECT gross_profit_per_unit FROM production_price_estimates WHERE calculation_id = ?",
                BigDecimal.class, calculationId)).isEqualByComparingTo("-80.00");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM quotation_revisions WHERE id = ?", revision.id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateIdentifiersAndCrossQuotationRevisionReferences() {
        var quote = quotation("CAD-UNIQUE");
        assertThatThrownBy(() -> quotation("CAD-UNIQUE")).isInstanceOf(DataIntegrityViolationException.class);
        var first = draft(quote, 1);
        assertThatThrownBy(() -> draft(quote, 1)).isInstanceOf(DataIntegrityViolationException.class);
        var second = draft(quote, 2);
        var foreign = draft(quotation(null), 1);
        second.sourceRevision = first;
        revisions.save(second);
        jdbc.update("UPDATE quotation_revisions SET replaced_by_revision_id = ?, replaced_at = CURRENT_TIMESTAMP WHERE id = ?",
                second.id, first.id);
        assertThat(revisions.findAllByQuotationIdOrderByRevisionNumberDesc(quote.id))
                .extracting(entityManagerFactory.getPersistenceUnitUtil()::getIdentifier)
                .containsExactly(second.id, first.id);
        assertThatThrownBy(() -> jdbc.update("UPDATE quotation_revisions SET source_revision_id = ? WHERE id = ?",
                foreign.id, second.id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE quotation_revisions SET replaced_by_revision_id = ? WHERE id = ?",
                foreign.id, first.id)).isInstanceOf(DataIntegrityViolationException.class);
        lines.save(line(first, 1));
        assertThatThrownBy(() -> lines.save(line(first, 1))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "quantity = 0", "quantity = -1", "position = 0", "unit_price = -1, gross_amount = -1, net_amount = -1",
            "discount_type = 'FIXED', discount_value = -1, discount_amount = -1, net_amount = 11",
            "discount_type = 'FIXED', discount_value = 11, discount_amount = 11, net_amount = -1",
            "discount_type = 'PERCENTAGE', discount_percent = -0.0001",
            "discount_type = 'PERCENTAGE', discount_percent = 100.0001",
            "discount_type = 'PERCENTAGE', discount_percent = NULL",
            "discount_type = 'FIXED', discount_value = 1, discount_percent = 10",
            "discount_value = 1", "line_type = 'CATALOG'", "line_type = 'OTHER'",
            "production_calculation_id = -1"
    })
    void rejectsInvalidLineValuesInSql(String assignment) {
        var line = line(draft(quotation(null), 1), 1);
        line.unitPrice = BigDecimal.TEN;
        line.grossAmount = BigDecimal.TEN;
        line.netAmount = BigDecimal.TEN;
        lines.save(line);
        assertThatThrownBy(() -> jdbc.update("UPDATE quotation_lines SET " + assignment + " WHERE id = ?", line.id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "currency = 'USD'", "status = 'OTHER'", "revision_number = 0", "shipping_amount = -1, total_amount = 9",
            "total_amount = -1", "general_discount_type = 'FIXED', general_discount_value = 11, general_discount_amount = 11",
            "general_discount_type = 'PERCENTAGE', general_discount_percent = 100.0001",
            "general_discount_type = 'NONE', general_discount_percent = 0", "replaced_at = CURRENT_TIMESTAMP"
    })
    void rejectsInvalidRevisionValuesInSql(String assignment) {
        var revision = draft(quotation(null), 1);
        jdbc.update("UPDATE quotation_revisions SET gross_amount = 10, subtotal = 10, total_amount = 10 WHERE id = ?",
                revision.id);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE quotation_revisions SET " + assignment + " WHERE id = ?", revision.id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsMismatchedVariantAndCatalogReferencesOnCustomLines() {
        var product = product("VARIANT-OWNER");
        var variant = new ProductVariant(product, "VARIANT-MISMATCH");
        variant.name = "Azul";
        variants.saveAndFlush(variant);
        var other = product("OTHER-PRODUCT");
        var line = lines.save(line(draft(quotation(null), 1), 1));
        assertThatThrownBy(() -> jdbc.update("UPDATE quotation_lines SET product_id = ? WHERE id = ?",
                product.id, line.id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE quotation_lines SET line_type = 'CATALOG', product_id = ?, variant_id = ? WHERE id = ?",
                other.id, variant.id, line.id)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void preservesMonetaryPrecisionAndAcceptsValidFixedDiscounts() {
        var revision = draft(quotation(null), 1);
        var line = line(revision, 1);
        line.unitPrice = new BigDecimal("1234567890123456789012345.67");
        line.grossAmount = line.unitPrice;
        line.discountType = QuotationDiscountType.FIXED;
        line.discountValue = new BigDecimal("0.01");
        line.discountAmount = line.discountValue;
        line.netAmount = line.grossAmount.subtract(line.discountAmount);
        lines.save(line);

        revision.grossAmount = line.grossAmount;
        revision.lineDiscountTotal = line.discountAmount;
        revision.subtotal = line.netAmount;
        revision.generalDiscountType = QuotationDiscountType.FIXED;
        revision.generalDiscountValue = new BigDecimal("0.02");
        revision.generalDiscountAmount = revision.generalDiscountValue;
        revision.shippingAmount = new BigDecimal("0.50");
        revision.totalAmount = revision.subtotal.subtract(revision.generalDiscountAmount).add(revision.shippingAmount);
        revisions.save(revision);

        var stored = revisions.findByQuotationIdAndRevisionNumber(revision.quotation.id, 1).orElseThrow();
        assertThat(stored.totalAmount).isEqualTo(new BigDecimal("1234567890123456789012346.14"));
        assertThat(lines.findAllByRevisionIdOrderByPositionAsc(revision.id).getFirst().unitPrice)
                .isEqualTo(line.unitPrice);
    }

    @Test
    void rejectsAStaleOptimisticVersion() {
        var quote = quotation(null);
        var first = quotations.findById(quote.id).orElseThrow();
        var stale = quotations.findById(quote.id).orElseThrow();
        first.quotationNumber = "CAD-LATEST";
        assertThat(quotations.save(first).version).isEqualTo(stale.version + 1);
        stale.quotationNumber = "CAD-STALE";
        assertThatThrownBy(() -> quotations.save(stale)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(quotations.findById(quote.id).orElseThrow().quotationNumber).isEqualTo("CAD-LATEST");
    }

    private Quotation quotation(String number) {
        var quote = new Quotation();
        quote.createdBy = "operador";
        quote.quotationNumber = number;
        return quotations.save(quote);
    }

    private QuotationRevision draft(Quotation quotation, int number) {
        var revision = new QuotationRevision();
        revision.quotation = quotation;
        revision.revisionNumber = number;
        revision.calculationPolicyVersion = "v1";
        return revisions.save(revision);
    }

    private QuotationLine line(QuotationRevision revision, int position) {
        var line = new QuotationLine();
        line.revision = revision;
        line.position = position;
        line.lineType = QuotationLineType.CUSTOM;
        line.nameSnapshot = "Articulo";
        line.quantity = 1;
        return line;
    }

    private Product product(String sku) {
        var category = new ProductCategory();
        category.name = sku;
        var product = new Product(sku);
        product.name = sku;
        product.category = categories.saveAndFlush(category);
        product.productionMode = ProductionMode.BAJO_PEDIDO;
        product.pricingMode = PricingMode.REQUIERE_COTIZACION;
        return products.saveAndFlush(product);
    }

    private long insertHistoricalCalculation() {
        long id = 8001;
        jdbc.update("""
                INSERT INTO production_calculations (id, technique, formula_version, username, created_at,
                    job_description, expected_good_units, currency, productive_seconds, indirect_rate_per_hour,
                    indirect_cost, total_cost, unit_cost)
                VALUES (?, 'FDM', 'v1', 'operador', CURRENT_TIMESTAMP, 'Pieza', 1, 'GTQ', 3600, 10, 10, 80, 80)
                """, id);
        jdbc.update("""
                INSERT INTO production_price_estimates (calculation_id, target_margin_pct, suggested_unit_price,
                    chosen_unit_price, gross_profit_per_unit, actual_margin_pct, below_cost)
                VALUES (?, 0, 80, 0, -80, NULL, TRUE)
                """, id);
        return id;
    }
}

