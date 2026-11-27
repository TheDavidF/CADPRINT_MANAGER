package gt.cadprint.manager;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import gt.cadprint.manager.ApiDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
class InventoryService {
    private static final BigDecimal MAX_STOCK = new BigDecimal("9999999999999.999");

    private static final Set<String> UNITS = Set.of("unidad", "g", "kg", "ml", "l", "hoja", "metro", "cm");

    private final MaterialRepo materials;
    private final MovementRepo movements;

    private final CategoryService categories;
    private final MaterialAuditService audit;

    InventoryService(MaterialRepo materials, MovementRepo movements, CategoryService categories,
                     MaterialAuditService audit) {
        this.materials = materials;
        this.movements = movements;
        this.categories = categories;
        this.audit = audit;
    }

    List<MaterialView> materials() {
        return materials.findAll().stream().map(MaterialView::from).toList();
    }

    @Transactional
    MaterialView create(MaterialCreate input) {
        if (materials.existsBySkuIgnoreCase(input.sku())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU duplicado");
        }
        Material material = new Material();
        material.sku = input.sku();
        String unit = normalizedUnit(input.unit());
        requireNormalizedUnit(unit);
        material.name = input.name();
        material.category = categories.resolve(input.categoryId(), input.category());
        material.unit = unit;
        material.minStock = input.minStock();
        material.unitCost = input.unitCost();
        material.active = input.active() == null || input.active();
        return MaterialView.from(materials.saveAndFlush(material));
    }

    @Transactional
    MaterialView update(Long id, MaterialUpdate input, String username) {
        Material material = findLockedMaterial(id);
        MaterialView before = MaterialView.from(material);
        String requestedUnit = normalizedUnit(input.unit());
        if (!requestedUnit.equals(normalizedUnit(material.unit))) {
            if (movements.existsByMaterialId(id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Cambiar la unidad de un material con movimientos requiere una conversión auditada");
            }
            requireNormalizedUnit(requestedUnit);
            material.unit = requestedUnit;
        }
        material.name = input.name();
        material.category = categories.resolve(input.categoryId(), input.category());
        material.minStock = input.minStock();
        material.unitCost = input.unitCost();
        material.active = input.active();
        MaterialView after = MaterialView.from(materials.saveAndFlush(material));
        audit.record(material, username, before, after);
        return after;
    }

    List<MaterialChangeView> changes(Long materialId) {
        if (!materials.existsById(materialId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Material no encontrado");
        }
        return audit.history(materialId);
    }

    @Transactional
    MovementView move(MovementRequest input, String username) {
        Material material = findLockedMaterial(input.materialId());
        String type = input.type().toUpperCase(Locale.ROOT);
        return MovementView.from(postMovement(material, type, input.quantity(), input.reason(), username, null));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    Movement receivePurchaseLine(Material material, PurchaseLine line, String reason, String username) {
        return postMovement(material, "ENTRADA", line.inventoryQuantity, reason, username, line);
    }

    private Movement postMovement(Material material, String type, BigDecimal quantity, String reason,
                                  String username, PurchaseLine purchaseLine) {
        if (!material.active) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Material inactivo");
        }
        BigDecimal delta = switch (type) {
            case "ENTRADA", "AJUSTE_MAS" -> quantity;
            case "SALIDA", "AJUSTE_MENOS" -> quantity.negate();
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo inválido");
        };
        BigDecimal beforeStock = material.stock;
        BigDecimal afterStock = beforeStock.add(delta);
        if (afterStock.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuficiente");
        }
        if (afterStock.compareTo(MAX_STOCK) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El stock supera el máximo permitido");
        }

        material.stock = afterStock;
        Movement movement = new Movement();
        movement.material = material;
        movement.type = type;
        movement.quantity = quantity;
        movement.purchaseLine = purchaseLine;
        if (purchaseLine != null) {
            movement.createdAt = movement.createdAt.truncatedTo(ChronoUnit.MICROS);
        }
        movement.beforeStock = beforeStock;
        movement.afterStock = afterStock;
        movement.reason = reason;
        movement.username = username;
        return movements.saveAndFlush(movement);
    }

    List<MovementView> movements() {
        return movements.findTop100ByOrderByCreatedAtDescIdDesc().stream()
                .map(MovementView::from)
                .toList();
    }

    private String normalizedUnit(String unit) {
        return unit == null ? "" : unit.strip().toLowerCase(Locale.ROOT);
    }

    private void requireNormalizedUnit(String unit) {
        if (!UNITS.contains(unit)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unidad de medida inválida");
        }
    }

    private Material findLockedMaterial(Long id) {
        return materials.locked(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material no encontrado"));
    }
}