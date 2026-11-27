package gt.cadprint.manager;

import java.math.BigDecimal;

import gt.cadprint.manager.ProductionCostDtos.CostLine;
import gt.cadprint.manager.ProductionCostDtos.MaterialInput;
import org.springframework.stereotype.Service;

import static gt.cadprint.manager.ProductionCostMath.invalid;
import static gt.cadprint.manager.ProductionCostMath.money;

@Service
class ProductionMaterialValuation {
    private final UnitConversionService units;

    ProductionMaterialValuation(UnitConversionService units) {
        this.units = units;
    }

    CostLine value(int position, MaterialInput input, Material material) {
        if (!material.active) {
            throw invalid("El material " + material.id + " está inactivo");
        }
        if (material.sku == null || material.sku.isBlank() || material.name == null || material.name.isBlank()
                || material.unitCost == null || material.unitCost.signum() < 0) {
            throw invalid("El material " + material.id + " no tiene datos de costeo válidos");
        }
        BigDecimal factor;
        try {
            factor = units.exactFactor(input.unit(), material.unit);
        } catch (IllegalArgumentException exception) {
            throw invalid("Material " + material.sku + ": " + exception.getMessage());
        }

        String description = material.name;
        if (material.unitCost.signum() == 0) {
            if (!input.acceptZeroCost() || input.zeroCostReason() == null || input.zeroCostReason().isBlank()) {
                throw invalid("Confirma el costo cero del material " + material.sku + " con una justificación");
            }
            description += " · Costo cero aceptado: " + input.zeroCostReason().strip();
        }
        return new CostLine(position, ProductionCostType.MATERIAL, description, input.quantity(),
                units.normalize(input.unit()), material.unitCost,
                money(input.quantity().multiply(factor).multiply(material.unitCost)),
                ProductionCostSource.MATERIAL_REFERENCE, material.id, material.sku, material.name, material.unit, factor);
    }
}
