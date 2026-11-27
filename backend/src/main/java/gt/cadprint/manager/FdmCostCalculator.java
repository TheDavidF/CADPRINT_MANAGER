package gt.cadprint.manager;

import java.math.BigDecimal;
import java.util.List;

import gt.cadprint.manager.ProductionCostDtos.CostLine;
import gt.cadprint.manager.ProductionCostDtos.FdmInput;
import org.springframework.stereotype.Service;

@Service
class FdmCostCalculator {
    private final ProductionOperatingCosts costs;

    FdmCostCalculator(ProductionOperatingCosts costs) {
        this.costs = costs;
    }

    List<CostLine> calculate(FdmInput input, List<CostLine> materials, ProductionCostProfile profile,
                            ProductionMachine machine, BigDecimal watts) {
        return costs.append(materials, profile, machine, input.printSeconds(), input.laborSeconds(),
                input.common().productiveSeconds(), watts);
    }

    FdmCalculationDetail detail(FdmInput input, ProductionCalculation calculation) {
        var detail = new FdmCalculationDetail();
        detail.calculation = calculation;
        detail.printSeconds = input.printSeconds();
        detail.laborSeconds = input.laborSeconds();
        detail.partMassGrams = input.partMassGrams();
        detail.supportMassGrams = input.supportMassGrams();
        return detail;
    }
}
