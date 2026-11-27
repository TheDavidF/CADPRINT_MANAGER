package gt.cadprint.manager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

final class ApiDtos {
    private ApiDtos() {
    }

    record CurrentUser(String username, List<String> roles) {
    }

    record Dashboard(int materials, long lowStock, BigDecimal inventoryValue, long movements) {
    }

    record MaterialCreate(
            @NotBlank @Size(max = 255) String sku,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 255) String category,
            @Positive Long categoryId,
            @NotBlank @Size(max = 255) String unit,
            @DecimalMin("0") @Digits(integer = 13, fraction = 3) BigDecimal minStock,
            @DecimalMin("0") @Digits(integer = 12, fraction = 4) BigDecimal unitCost,
            Boolean active) {
        MaterialCreate {
            minStock = minStock == null ? BigDecimal.ZERO : minStock;
            unitCost = unitCost == null ? BigDecimal.ZERO : unitCost;
        }
    }

    record MaterialUpdate(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 255) String category,
            @Positive Long categoryId,
            @NotBlank @Size(max = 255) String unit,
            @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 3) BigDecimal minStock,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 4) BigDecimal unitCost,
            boolean active) {
    }

    record MovementRequest(
            @NotNull @Positive Long materialId,
            @NotBlank String type,
            @NotNull @DecimalMin("0.001") @Digits(integer = 13, fraction = 3) BigDecimal quantity,
            @NotBlank @Size(max = 255) String reason) {
    }

    record SupplierRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 255) String phone,
            @Email @Size(max = 255) String email,
            @Size(max = 255) String notes) {
    }

    record MaterialView(Long id, String sku, String name, String category, Long categoryId, String unit,
                        BigDecimal stock,
                        BigDecimal minStock, BigDecimal unitCost, boolean active, Long version) {
        static MaterialView from(Material material) {
            return new MaterialView(material.id, material.sku, material.name, material.category.name,
                    material.category.id, material.unit,
                    material.stock, material.minStock, material.unitCost, material.active, material.version);
        }
    }

    record CategoryRequest(@NotBlank @Size(max = 255) String name) {
    }

    record CategoryView(Long id, String name) {
        static CategoryView from(Category category) {
            return new CategoryView(category.id, category.name);
        }
    }

    record MaterialChangeView(Long id, String username, Instant changedAt, String field,
                              String previousValue, String newValue) {
        static MaterialChangeView from(MaterialChange change) {
            return new MaterialChangeView(change.id, change.username, change.changedAt,
                    change.fieldName, change.previousValue, change.newValue);
        }
    }

    record MovementView(Long id, MaterialView material, String type, BigDecimal quantity, BigDecimal beforeStock,
                        BigDecimal afterStock, String reason, String username, Instant createdAt) {
        static MovementView from(Movement movement) {
            return new MovementView(movement.id, MaterialView.from(movement.material), movement.type,
                    movement.quantity, movement.beforeStock, movement.afterStock, movement.reason,
                    movement.username, movement.createdAt);
        }
    }

    record SupplierView(Long id, String name, String phone, String email, String notes) {
        static SupplierView from(Supplier supplier) {
            return new SupplierView(supplier.id, supplier.name, supplier.phone, supplier.email, supplier.notes);
        }
    }
}