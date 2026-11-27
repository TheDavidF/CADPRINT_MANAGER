package gt.cadprint.manager;

import java.math.BigDecimal;
import java.util.List;

import gt.cadprint.manager.ProductionCostDtos.CostLine;
import gt.cadprint.manager.ProductionCostDtos.SublimationInput;
import org.springframework.stereotype.Service;

@Service
class SublimationCostCalculator {
    private final ProductionOperatingCosts costs;

    SublimationCostCalculator(ProductionOperatingCosts costs) {
        this.costs = costs;
    }

    List<CostLine> calculate(SublimationInput input, List<CostLine> materials, ProductionCostProfile profile,
                            ProductionMachine machine, BigDecimal watts) {
        long pressSeconds;
        try {
            pressSeconds = Math.multiplyExact(input.cycleCount(), input.pressSecondsPerCycle());
        } catch (ArithmeticException exception) {
            throw ProductionCostMath.invalid("El tiempo total de prensado excede el rango permitido");
        }
        return costs.append(materials, profile, machine, pressSeconds, input.laborSeconds(),
                input.common().productiveSeconds(), watts);
    }

    SublimationCalculationDetail detail(SublimationInput input, ProductionCalculation calculation) {
        var detail = new SublimationCalculationDetail();
        detail.calculation = calculation;
        detail.cycleCount = input.cycleCount();
        detail.pressSecondsPerCycle = input.pressSecondsPerCycle();
        detail.preparationSeconds = input.preparationSeconds();
        detail.laborSeconds = input.laborSeconds();
        return detail;
    }
}
