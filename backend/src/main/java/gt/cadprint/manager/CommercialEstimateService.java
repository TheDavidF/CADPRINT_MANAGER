package gt.cadprint.manager;

import java.math.BigDecimal;

import gt.cadprint.manager.ProductionCostDtos.PriceEstimate;
import org.springframework.stereotype.Service;

import static gt.cadprint.manager.ProductionCostMath.divide;
import static gt.cadprint.manager.ProductionCostMath.scale;

@Service
class CommercialEstimateService {
    PriceEstimate estimate(BigDecimal historicalCost, BigDecimal target, BigDecimal requestedPrice) {
        BigDecimal suggested = divide(historicalCost, BigDecimal.ONE.subtract(target.movePointLeft(2)), 27, 2);
        BigDecimal chosen = requestedPrice == null ? suggested : scale(requestedPrice, 27, 2);
        BigDecimal profit = chosen.subtract(historicalCost);
        BigDecimal margin = chosen.signum() == 0 ? null : divide(profit.movePointRight(2), chosen, 27, 4);
        return new PriceEstimate(target, suggested, chosen, scale(profit, 27, 2), margin, profit.signum() < 0);
    }
}
