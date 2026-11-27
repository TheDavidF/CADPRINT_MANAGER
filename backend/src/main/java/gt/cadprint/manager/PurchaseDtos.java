package gt.cadprint.manager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

final class PurchaseDtos {
    private PurchaseDtos() {
    }

    record DraftRequest(
            @PositiveOrZero Long version,
            @Positive Long supplierId,
            @NotNull LocalDate purchaseDate,
            @Size(max = 255) String documentNumber,
            @Size(max = 1000) String notes,
            @Size(max = 200) List<@NotNull @Valid LineRequest> lines,
            boolean authorizeDuplicate) {
    }

    record LineRequest(
            @NotNull @Positive Long materialId,
            @NotNull @DecimalMin("0.001") @Digits(integer = 13, fraction = 3) BigDecimal quantity,
            @NotBlank @Size(max = 255) String purchaseUnit,
            @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal unitPrice) {
    }

    record ConfirmRequest(@NotNull @PositiveOrZero Long version, boolean authorizeDuplicate) {
    }

    record ReasonRequest(@NotBlank @Size(max = 1000) String reason) {
    }

    record Summary(Long id, Long supplierId, String supplierName, LocalDate purchaseDate, String documentNumber,
                   PurchaseStatus status, String currency, BigDecimal subtotal, BigDecimal total, String notes,
                   String createdBy, Instant createdAt, Long version) {
        static Summary from(Purchase purchase) {
            Long supplierId = purchase.supplier == null ? null : purchase.supplier.getId();
            String supplierName = purchase.supplier == null ? null : purchase.supplier.getName();
            return new Summary(purchase.id, supplierId, supplierName, purchase.purchaseDate,
                    purchase.documentNumber, purchase.status, purchase.currency, purchase.subtotal, purchase.total,
                    purchase.notes, purchase.createdBy, purchase.createdAt, purchase.version);
        }
    }

    record LineView(Long id, Long materialId, BigDecimal quantity, String purchaseUnit, BigDecimal unitPrice,
                    BigDecimal subtotal, String inventoryUnit, BigDecimal conversionFactor,
                    BigDecimal inventoryQuantity, BigDecimal inventoryUnitCost) {
        static LineView from(PurchaseLine line) {
            return new LineView(line.id, line.material.getId(), line.quantity, line.purchaseUnit, line.unitPrice,
                    line.subtotal, line.inventoryUnit, line.conversionFactor, line.inventoryQuantity,
                    line.inventoryUnitCost);
        }
    }

    record Detail(Summary purchase, List<LineView> lines, ReceiptView receipt) {
    }

    record ReceiptMovementView(Long id, Long purchaseLineId, Long materialId, String type, BigDecimal quantity,
                               BigDecimal beforeStock, BigDecimal afterStock, Instant createdAt, String username,
                               String reason) {
        static ReceiptMovementView from(Movement movement) {
            return new ReceiptMovementView(movement.id, movement.purchaseLine.getId(), movement.material.getId(),
                    movement.type, movement.quantity, movement.beforeStock, movement.afterStock, movement.createdAt,
                    movement.username, movement.reason);
        }
    }

    record ReceiptView(Long id, Long purchaseId, Instant receivedAt, String receivedBy,
                       List<ReceiptMovementView> movements) {
    }

    record PurchasePage(List<Summary> content, int page, int size, long totalElements, int totalPages) {
    }

    record EventView(Long id, PurchaseEventType eventType, PurchaseStatus previousStatus, PurchaseStatus newStatus,
                     String username, Instant createdAt, String reason) {
        static EventView from(PurchaseEvent event) {
            return new EventView(event.id, event.eventType, event.previousStatus, event.newStatus,
                    event.username, event.createdAt, event.reason);
        }
    }
}
