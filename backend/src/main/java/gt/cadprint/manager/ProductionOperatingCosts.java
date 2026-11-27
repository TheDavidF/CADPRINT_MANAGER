package gt.cadprint.manager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import gt.cadprint.manager.ProductionCostDtos.CostLine;
import org.springframework.stereotype.Service;

import static gt.cadprint.manager.ProductionCostMath.*;

@Service
class ProductionOperatingCosts {
    List<CostLine> append(List<CostLine> materials, ProductionCostProfile profile, ProductionMachine machine,
                          long machineSeconds, long laborSeconds, long productiveSeconds, BigDecimal watts) {
        List<CostLine> result = new ArrayList<>(materials);
        if (watts.signum() > 0) {
            BigDecimal wattSeconds = watts.multiply(BigDecimal.valueOf(machineSeconds));
            result.add(new CostLine(result.size() + 1, ProductionCostType.ENERGY,
                    "Energía: " + watts.toPlainString() + " W × " + machineSeconds + " s; tarifa Q/kWh",
                    scale(wattSeconds, 19, 6), "W·s", profile.electricityRatePerKwh,
                    divide(wattSeconds.multiply(profile.electricityRatePerKwh), WATT_SECONDS_PER_KWH, 19, 2),
                    ProductionCostSource.COST_PROFILE, null, null, null, null, null));
        }
        hourly(result, ProductionCostType.MACHINE, "Uso de máquina; tarifa Q/h", machineSeconds,
                machine.machineRatePerHour, ProductionCostSource.MACHINE_RATE);
        hourly(result, ProductionCostType.LABOR, "Trabajo humano; tarifa Q/h", laborSeconds,
                profile.laborRatePerHour, ProductionCostSource.COST_PROFILE);
        hourly(result, ProductionCostType.INDIRECT, "Tiempo productivo imputable; tarifa Q/h", productiveSeconds,
                profile.indirectRatePerHour, ProductionCostSource.COST_PROFILE);
        return List.copyOf(result);
    }

    private void hourly(List<CostLine> lines, ProductionCostType kind, String description, long seconds,
                        BigDecimal rate, ProductionCostSource source) {
        if (seconds == 0) {
            return;
        }
        BigDecimal secondsValue = scale(BigDecimal.valueOf(seconds), 19, 6);
        lines.add(new CostLine(lines.size() + 1, kind, description, secondsValue, "s", rate,
                divide(secondsValue.multiply(rate), SECONDS_PER_HOUR, 19, 2), source, null, null, null, null, null));
    }
}
