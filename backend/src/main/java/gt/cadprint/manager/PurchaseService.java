package gt.cadprint.manager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import gt.cadprint.manager.PurchaseDtos.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;
import org.hibernate.Hibernate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
class PurchaseService {
    record Actor(String username, boolean admin) {
    }

    record ReceiptResult(ReceiptView receipt, boolean created) {
    }

    private final PurchaseRepo purchases;
    private final PurchaseLineRepo lines;
    private final PurchaseEventRepo events;
    private final MaterialRepo materials;
    private final PurchaseReceiptRepo receipts;
    private final MovementRepo movements;
    private final InventoryService inventory;
    private final UnitConversionService units;
    private final EntityManager entityManager;

    PurchaseService(PurchaseRepo purchases, PurchaseLineRepo lines, PurchaseEventRepo events,
                    MaterialRepo materials, UnitConversionService units, EntityManager entityManager,
                    PurchaseReceiptRepo receipts, MovementRepo movements, InventoryService inventory) {
        this.purchases = purchases;
        this.lines = lines;
        this.events = events;
        this.materials = materials;
        this.units = units;
        this.entityManager = entityManager;
        this.receipts = receipts;
        this.movements = movements;
        this.inventory = inventory;
    }

    PurchasePage list(PurchaseStatus status, Long supplierId, LocalDate from, LocalDate to,
                      String documentNumber, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (supplierId != null && supplierId <= 0)
                || (from != null && to != null && from.isAfter(to))) {
            throw badRequest("Filtros o paginación inválidos");
        }
        String document = normalizeDocument(documentNumber);
        var results = purchases.findAll((root, query, criteria) -> {
            List<Predicate> filters = new ArrayList<>();
            if (status != null) filters.add(criteria.equal(root.get("status"), status));
            if (supplierId != null) filters.add(criteria.equal(root.get("supplier").get("id"), supplierId));
            if (from != null) filters.add(criteria.greaterThanOrEqualTo(root.get("purchaseDate"), from));
            if (to != null) filters.add(criteria.lessThanOrEqualTo(root.get("purchaseDate"), to));
            if (document != null) filters.add(criteria.equal(criteria.upper(root.get("documentNumber")), document));
            return criteria.and(filters.toArray(Predicate[]::new));
        }, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "purchaseDate", "id")));
        return new PurchasePage(results.map(Summary::from).getContent(), page, size,
                results.getTotalElements(), results.getTotalPages());
    }

    Detail detail(Long id) {
        Purchase purchase = find(id);
        return detail(purchase, lines.findByPurchaseIdOrderByIdAsc(id));
    }

    List<EventView> events(Long id) {
        find(id);
        return events.findByPurchaseIdOrderByCreatedAtDescIdDesc(id).stream().map(EventView::from).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    Detail create(DraftRequest input, Actor actor) {
        Purchase purchase = new Purchase();
        purchase.createdBy = actor.username();
        return saveDraft(purchase, input, actor, true);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    Detail edit(Long id, DraftRequest input, Actor actor) {
        Purchase purchase = locked(id);
        requireStatus(purchase, PurchaseStatus.BORRADOR);
        checkVersion(purchase, input.version());
        return saveDraft(purchase, input, actor, false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    Detail confirm(Long id, ConfirmRequest input, Actor actor) {
        Purchase purchase = locked(id);
        requireStatus(purchase, PurchaseStatus.BORRADOR);
        checkVersion(purchase, input.version());
        List<PurchaseLine> purchaseLines = lines.findByPurchaseIdOrderByIdAsc(id);
        if (purchase.supplier == null || purchaseLines.isEmpty()) {
            throw badRequest("Indica un proveedor y al menos una línea para confirmar");
        }

        Supplier supplier = lockSupplier(purchase.supplier.getId());
        boolean authorized = validateDocument(purchase, supplier, purchase.documentNumber,
                input.authorizeDuplicate(), actor);
        Map<Long, Material> lockedMaterials = lockMaterials(purchaseLines);

        for (int index = 0; index < purchaseLines.size(); index++) {
            PurchaseLine line = purchaseLines.get(index);
            Material material = lockedMaterials.get(line.material.getId());
            completeConversion(line, material, index + 1);
        }
        calculateTotals(purchase, purchaseLines);
        if (authorized) recordAuthorization(purchase, actor, supplier);
        transition(purchase, PurchaseStatus.PENDIENTE, PurchaseEventType.CONFIRMED, actor, null);
        return detail(purchase, purchaseLines);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    ReceiptResult receive(Long id, Actor actor) {
        Purchase purchase = locked(id);
        if (purchase.status == PurchaseStatus.RECIBIDA) {
            return new ReceiptResult(existingReceipt(id), false);
        }
        requireStatus(purchase, PurchaseStatus.PENDIENTE);

        List<PurchaseLine> purchaseLines = lines.findByPurchaseIdOrderByIdAsc(id);
        if (purchaseLines.isEmpty()) {
            throw conflict("La compra pendiente no tiene líneas confirmadas");
        }
        Map<Long, Material> lockedMaterials = lockMaterials(purchaseLines);
        for (int index = 0; index < purchaseLines.size(); index++) {
            PurchaseLine line = purchaseLines.get(index);
            validateReceiptLine(line, lockedMaterials.get(line.material.getId()), index + 1);
        }

        List<Movement> entries = new ArrayList<>();
        String reason = "Recepción de compra #" + id;
        for (PurchaseLine line : purchaseLines) {
            Material material = lockedMaterials.get(line.material.getId());
            entries.add(inventory.receivePurchaseLine(material, line, reason, actor.username()));
        }

        PurchaseReceipt receipt = new PurchaseReceipt();
        receipt.purchase = purchase;
        receipt.receivedBy = actor.username();
        receipt.receivedAt = receipt.receivedAt.truncatedTo(ChronoUnit.MICROS);
        receipts.save(receipt);
        transition(purchase, PurchaseStatus.RECIBIDA, PurchaseEventType.RECEIVED, actor, reason);
        return new ReceiptResult(receiptView(id, receipt, entries), true);
    }

    private Map<Long, Material> lockMaterials(List<PurchaseLine> purchaseLines) {
        Map<Long, Material> locked = new TreeMap<>();
        var materialIds = purchaseLines.stream().map(line -> line.material.getId()).distinct().sorted().toList();
        for (Long materialId : materialIds) {
            Material material = materials.locked(materialId)
                    .orElseThrow(() -> conflict("Material de compra no encontrado: " + materialId));
            locked.put(materialId, Hibernate.unproxy(material, Material.class));
        }
        return locked;
    }

    private void validateReceiptLine(PurchaseLine line, Material material, int lineNumber) {
        String prefix = "Línea " + lineNumber + ": ";
        if (!material.isActive()) {
            throw conflict(prefix + "el material está inactivo");
        }
        if (line.inventoryUnit == null || line.inventoryQuantity == null || line.conversionFactor == null
                || line.inventoryUnitCost == null) {
            throw conflict(prefix + "faltan datos de conversión confirmados");
        }
        String currentUnit = material.getUnit();
        if (currentUnit == null || !line.inventoryUnit.equals(units.normalize(currentUnit))) {
            throw conflict(prefix + "la unidad de inventario cambió desde la confirmación");
        }
        UnitConversionService.Conversion expected;
        try {
            expected = units.convert(line.quantity, line.purchaseUnit, currentUnit);
        } catch (IllegalArgumentException exception) {
            throw conflict(prefix + exception.getMessage());
        }
        if (expected.quantity().compareTo(line.inventoryQuantity) != 0
                || expected.factor().compareTo(line.conversionFactor) != 0) {
            throw conflict(prefix + "la cantidad confirmada no coincide con la conversión válida");
        }
    }

    private ReceiptView existingReceipt(Long id) {
        PurchaseReceipt receipt = receipts.findByPurchaseId(id)
                .orElseThrow(() -> conflict("La compra recibida no tiene un registro de recepción"));
        return receiptView(id, receipt, movements.findByPurchaseLinePurchaseIdOrderByPurchaseLineIdAsc(id));
    }

    private ReceiptView receiptView(Long id, PurchaseReceipt receipt, List<Movement> entries) {
        return new ReceiptView(receipt.id, id, receipt.receivedAt, receipt.receivedBy,
                entries.stream().map(ReceiptMovementView::from).toList());
    }

    @Transactional
    Detail cancel(Long id, String reason, Actor actor) {
        requireAdmin(actor);
        Purchase purchase = locked(id);
        if (purchase.status != PurchaseStatus.BORRADOR && purchase.status != PurchaseStatus.PENDIENTE) {
            throw conflict("Solo se puede cancelar una compra en borrador o pendiente");
        }
        transition(purchase, PurchaseStatus.CANCELADA, PurchaseEventType.CANCELLED, actor, reason.strip());
        return detail(purchase, lines.findByPurchaseIdOrderByIdAsc(id));
    }

    @Transactional
    Detail returnToDraft(Long id, String reason, Actor actor) {
        requireAdmin(actor);
        Purchase purchase = locked(id);
        requireStatus(purchase, PurchaseStatus.PENDIENTE);
        List<PurchaseLine> purchaseLines = lines.findByPurchaseIdOrderByIdAsc(id);
        for (PurchaseLine line : purchaseLines) {
            line.inventoryUnit = null;
            line.conversionFactor = null;
            line.inventoryQuantity = null;
            line.inventoryUnitCost = null;
        }
        transition(purchase, PurchaseStatus.BORRADOR, PurchaseEventType.RETURNED_TO_DRAFT, actor, reason.strip());
        return detail(purchase, purchaseLines);
    }

    private Detail saveDraft(Purchase purchase, DraftRequest input, Actor actor, boolean creating) {
        Supplier supplier = input.supplierId() == null ? null : lockSupplier(input.supplierId());
        String document = normalizeDocument(input.documentNumber());
        boolean authorized = validateDocument(purchase, supplier, document, input.authorizeDuplicate(), actor);
        purchase.supplier = supplier;
        purchase.documentNumber = document;
        purchase.purchaseDate = input.purchaseDate();
        purchase.notes = input.notes();
        List<PurchaseLine> newLines = new ArrayList<>();
        if (input.lines() != null) {
            for (LineRequest inputLine : input.lines()) {
                newLines.add(draftLine(purchase, inputLine));
            }
        }
        calculateTotals(purchase, newLines);
        Long previousVersion = purchase.version;
        purchases.saveAndFlush(purchase);
        if (!creating) {
            lines.deleteAll(lines.findByPurchaseIdOrderByIdAsc(purchase.id));
            if (purchase.version.equals(previousVersion)) {
                // A line-only edit must also invalidate the version of the purchase.
                entityManager.lock(purchase, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
            }
        }
        List<PurchaseLine> storedLines = lines.saveAllAndFlush(newLines);
        record(purchase, creating ? PurchaseEventType.CREATED : PurchaseEventType.EDITED,
                creating ? null : purchase.status, actor, null);
        if (authorized) recordAuthorization(purchase, actor, supplier);
        return detail(purchase, storedLines);
    }

    private PurchaseLine draftLine(Purchase purchase, LineRequest input) {
        Material material = materials.findById(input.materialId())
                .orElseThrow(() -> badRequest("Material no encontrado: " + input.materialId()));
        PurchaseLine line = new PurchaseLine();
        line.purchase = purchase;
        line.material = material;
        line.quantity = input.quantity();
        line.purchaseUnit = units.normalize(input.purchaseUnit());
        line.unitPrice = input.unitPrice();
        return line;
    }

    private void calculateTotals(Purchase purchase, List<PurchaseLine> purchaseLines) {
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
        for (PurchaseLine line : purchaseLines) {
            line.subtotal = line.quantity.multiply(line.unitPrice).setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(line.subtotal);
        }
        if (subtotal.precision() > 19) {
            throw badRequest("El importe excede el máximo permitido");
        }
        purchase.subtotal = subtotal;
        purchase.total = subtotal;
    }

    private void completeConversion(PurchaseLine line, Material material, int lineNumber) {
        try {
            if (!material.isActive()) throw new IllegalArgumentException("El material está inactivo");
            var conversion = units.convert(line.quantity, line.purchaseUnit, material.getUnit());
            line.inventoryUnit = units.normalize(material.getUnit());
            line.conversionFactor = conversion.factor();
            line.inventoryQuantity = conversion.quantity();
            line.inventoryUnitCost = line.subtotal.divide(conversion.quantity(), 8, RoundingMode.HALF_UP);
        } catch (IllegalArgumentException exception) {
            throw badRequest("Línea " + lineNumber + ": " + exception.getMessage());
        }
    }

    private boolean validateDocument(Purchase purchase, Supplier supplier, String document,
                                     boolean authorizeDuplicate, Actor actor) {
        if (authorizeDuplicate) requireAdmin(actor);
        if (supplier == null || document == null) return false;
        boolean duplicate = purchases.findBySupplierIdAndDocumentNumberIgnoreCaseOrderByIdAsc(supplier.getId(), document)
                .stream().anyMatch(existing -> !existing.id.equals(purchase.id));
        if (duplicate && !authorizeDuplicate) {
            throw conflict("Ya existe una compra con ese documento para el proveedor. Requiere autorización de ADMIN.");
        }
        return duplicate;
    }

    private void recordAuthorization(Purchase purchase, Actor actor, Supplier supplier) {
        record(purchase, PurchaseEventType.DUPLICATE_DOCUMENT_AUTHORIZED, purchase.status, actor,
                "Proveedor " + supplier.getId() + ", documento " + purchase.documentNumber);
    }

    private String normalizeDocument(String document) {
        if (document == null || document.isBlank()) return null;
        String normalized = document.strip().replaceAll("(?U)\\s+", " ").strip().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) return null;
        if (normalized.length() > 255) throw badRequest("El documento excede 255 caracteres");
        return normalized;
    }

    private Supplier lockSupplier(Long id) {
        Supplier supplier = entityManager.find(Supplier.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (supplier == null) throw badRequest("Proveedor no encontrado");
        return supplier;
    }

    private void transition(Purchase purchase, PurchaseStatus next, PurchaseEventType action, Actor actor, String reason) {
        PurchaseStatus previous = purchase.status;
        purchase.status = next;
        purchases.flush();
        record(purchase, action, previous, actor, reason);
    }

    private void record(Purchase purchase, PurchaseEventType action, PurchaseStatus previous, Actor actor, String reason) {
        PurchaseEvent event = new PurchaseEvent();
        event.purchase = purchase;
        event.eventType = action;
        event.previousStatus = previous;
        event.newStatus = purchase.status;
        event.username = actor.username();
        event.reason = reason;
        events.save(event);
    }

    private Detail detail(Purchase purchase, List<PurchaseLine> purchaseLines) {
        ReceiptView receipt = purchase.status == PurchaseStatus.RECIBIDA ? existingReceipt(purchase.id) : null;
        return new Detail(Summary.from(purchase), purchaseLines.stream().map(LineView::from).toList(), receipt);
    }

    private Purchase find(Long id) {
        return purchases.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compra no encontrada"));
    }

    private Purchase locked(Long id) {
        return purchases.locked(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compra no encontrada"));
    }

    private void requireStatus(Purchase purchase, PurchaseStatus expected) {
        if (purchase.status != expected) throw conflict("La compra está en estado " + purchase.status);
    }

    private void checkVersion(Purchase purchase, Long version) {
        if (version == null) throw badRequest("La versión es obligatoria");
        if (!version.equals(purchase.version)) throw conflict("La versión de la compra está desactualizada");
    }

    private void requireAdmin(Actor actor) {
        if (!actor.admin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere el rol ADMIN");
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
