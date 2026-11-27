package gt.cadprint.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:purchases-test-${random.uuid}",
        "CADPRINT_ADMIN_PASSWORD=test-admin-password",
        "CADPRINT_OPERATOR_PASSWORD=test-operator-password"
})
class PurchasePersistenceTest {
    @Autowired PurchaseRepo purchases;
    @Autowired PurchaseLineRepo lines;
    @Autowired PurchaseReceiptRepo receipts;
    @Autowired PurchaseEventRepo events;
    @Autowired SupplierRepo suppliers;
    @Autowired MaterialRepo materials;
    @Autowired CategoryRepo categories;
    @Autowired MovementRepo movements;
    @Autowired JdbcTemplate jdbc;

    @Test
    void persistsPurchaseGraphAndEnforcesInventoryReferences() {
        Supplier supplier = new Supplier();
        supplier.name = "Proveedor de prueba";
        supplier = suppliers.saveAndFlush(supplier);
        Material pla = material("PLA-TEST");
        Material petg = material("PETG-TEST");
        Purchase purchase = purchase(supplier, "DOC-12");
        Purchase duplicate = purchase(supplier, "doc-12");
        Purchase withoutDocument = purchase(supplier, null);
        assertThat(withoutDocument.id).isNotNull();
        assertThat(purchases.findBySupplierIdAndDocumentNumberIgnoreCaseOrderByIdAsc(supplier.id, "DOC-12"))
                .extracting(found -> found.id).containsExactly(purchase.id, duplicate.id);

        PurchaseLine first = line(purchase, pla, "2.000", "0.1825", "0.37");
        PurchaseLine second = line(purchase, petg, "1.000", "12.0000", "12.00");
        assertThat(lines.findByPurchaseIdOrderByIdAsc(purchase.id))
                .extracting(found -> found.id).containsExactly(first.id, second.id);
        assertThat(lines.findById(first.id).orElseThrow().unitPrice).isEqualByComparingTo("0.1825");
        assertThat(purchases.findById(purchase.id).orElseThrow().currency).isEqualTo("GTQ");
        assertThat(materials.findById(pla.id).orElseThrow().stock).isZero();

        PurchaseEvent event = new PurchaseEvent();
        event.purchase = purchase;
        event.eventType = PurchaseEventType.RETURNED_TO_DRAFT;
        event.previousStatus = PurchaseStatus.PENDIENTE;
        event.newStatus = PurchaseStatus.BORRADOR;
        event.username = "admin";
        event.reason = "Corregir cantidades";
        events.saveAndFlush(event);
        assertThat(events.findByPurchaseIdOrderByCreatedAtDescIdDesc(purchase.id))
                .singleElement().satisfies(found -> {
                    assertThat(found.eventType).isEqualTo(PurchaseEventType.RETURNED_TO_DRAFT);
                    assertThat(found.reason).isEqualTo(event.reason);
                });

        PurchaseReceipt receipt = receipts.saveAndFlush(receipt(purchase));
        assertThat(receipts.findByPurchaseId(purchase.id).orElseThrow().id).isEqualTo(receipt.id);
        assertThatThrownBy(() -> receipts.saveAndFlush(receipt(purchase)))
                .isInstanceOf(DataIntegrityViolationException.class);

        Movement entry = movements.saveAndFlush(movement(pla, first));
        assertThat(jdbc.queryForObject("SELECT purchase_line_id FROM movements WHERE id = ?", Long.class, entry.id))
                .isEqualTo(first.id);
        assertThatThrownBy(() -> movements.saveAndFlush(movement(pla, first)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> movements.saveAndFlush(movement(pla, second)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM materials WHERE id = ?", petg.id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> line(purchase, pla, "0", "1", "0"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(movements.saveAndFlush(movement(pla, null)).id).isNotNull();
        assertThat(movements.saveAndFlush(movement(pla, null)).id).isNotNull();
    }

    private Material material(String sku) {
        Material material = new Material();
        material.sku = sku;
        material.name = sku;
        material.unit = "g";
        material.category = categories.findByNameKey("filamento").orElseThrow();
        return materials.saveAndFlush(material);
    }

    private Purchase purchase(Supplier supplier, String documentNumber) {
        Purchase purchase = new Purchase();
        purchase.supplier = supplier;
        purchase.purchaseDate = LocalDate.of(2026, 1, 1);
        purchase.documentNumber = documentNumber;
        purchase.createdBy = "operador";
        return purchases.saveAndFlush(purchase);
    }

    private PurchaseLine line(Purchase purchase, Material material, String quantity, String price, String subtotal) {
        PurchaseLine line = new PurchaseLine();
        line.purchase = purchase;
        line.material = material;
        line.quantity = new BigDecimal(quantity);
        line.purchaseUnit = "g";
        line.unitPrice = new BigDecimal(price);
        line.subtotal = new BigDecimal(subtotal);
        return lines.saveAndFlush(line);
    }

    private PurchaseReceipt receipt(Purchase purchase) {
        PurchaseReceipt receipt = new PurchaseReceipt();
        receipt.purchase = purchase;
        receipt.receivedBy = "operador";
        return receipt;
    }

    private Movement movement(Material material, PurchaseLine line) {
        Movement movement = new Movement();
        movement.material = material;
        movement.purchaseLine = line;
        movement.type = "ENTRADA";
        movement.quantity = BigDecimal.ONE;
        movement.beforeStock = BigDecimal.ZERO;
        movement.afterStock = BigDecimal.ONE;
        movement.reason = "Prueba de persistencia";
        movement.username = "operador";
        return movement;
    }
}
