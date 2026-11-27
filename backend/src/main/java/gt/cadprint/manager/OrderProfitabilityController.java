package gt.cadprint.manager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class OrderProfitabilityController {
    private final CustomerOrderRepository orders;
    private final QuotationRevisionRepo revisions;
    private final QuotationLineRepo lines;
    private final ProductionConsumptionRepository consumptions;
    private final OrderDirectCostRepository costs;

    OrderProfitabilityController(CustomerOrderRepository orders, QuotationRevisionRepo revisions,
                                 QuotationLineRepo lines, ProductionConsumptionRepository consumptions,
                                 OrderDirectCostRepository costs) {
        this.orders = orders;
        this.revisions = revisions;
        this.lines = lines;
        this.consumptions = consumptions;
        this.costs = costs;
    }

    @GetMapping("/api/quotations/{quotationId}/revisions/{revisionNumber}/order/profitability")
    @Transactional(readOnly = true)
    Estimate estimate(@PathVariable Long quotationId, @PathVariable int revisionNumber) {
        var order = orders.findByQuotationIdAndRevisionNumber(quotationId, revisionNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        var revision = revisions.findByQuotationIdAndRevisionNumber(order.quotationId, order.revisionNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Revisión no encontrada"));
        var quotedLines = lines.findAllByRevisionIdOrderByPositionAsc(revision.id);

        BigDecimal coveredCost = BigDecimal.ZERO;
        int missingLines = 0;
        for (var line : quotedLines) {
            if (line.productionCalculation == null) {
                missingLines++;
            } else {
                var calculation = Hibernate.unproxy(line.productionCalculation, ProductionCalculation.class);
                coveredCost = coveredCost.add(calculation.unitCost.multiply(BigDecimal.valueOf(line.quantity)));
            }
        }

        BigDecimal sales = revision.totalAmount.subtract(revision.shippingAmount);
        coveredCost = coveredCost.setScale(2, RoundingMode.HALF_UP);
        BigDecimal estimatedProfit = missingLines == 0 ? sales.subtract(coveredCost) : null;
        BigDecimal marginPercent = estimatedProfit != null && sales.signum() > 0
                ? estimatedProfit.movePointRight(2).divide(sales, 2, RoundingMode.HALF_UP)
                : null;
        var recordedConsumptions = consumptions.findAllByCustomerOrderIdOrderByIdAsc(order.id);
        BigDecimal recordedMaterialCost = BigDecimal.ZERO;
        int unvaluedConsumptions = 0;
        for (var consumption : recordedConsumptions) {
            if (consumption.unitCostAtConsumption == null) {
                unvaluedConsumptions++;
            } else {
                recordedMaterialCost = recordedMaterialCost.add(
                        consumption.unitCostAtConsumption.multiply(consumption.movement.quantity));
            }
        }
        var directCosts = costs.findAllByCustomerOrderIdOrderByIncurredOnDescIdDesc(order.id);
        var directTotal = directCosts.stream().map(cost -> cost.amount).reduce(new BigDecimal("0.00"), BigDecimal::add);
        return new Estimate(sales, coveredCost, quotedLines.size(), missingLines, estimatedProfit, marginPercent,
                recordedMaterialCost.setScale(2, RoundingMode.HALF_UP), recordedConsumptions.size(), unvaluedConsumptions,
                directTotal, recordedMaterialCost.add(directTotal).setScale(2, RoundingMode.HALF_UP),
                directCosts.stream().map(OrderDirectCostController.CostView::from).toList());
    }

    record Estimate(BigDecimal salesExcludingShipping, BigDecimal coveredEstimatedCost,
                    int lineCount, int missingCostLines, BigDecimal estimatedProfit, BigDecimal marginPercent,
                    BigDecimal recordedMaterialCost, int consumptionCount, int unvaluedConsumptions,
                    BigDecimal directCostTotal, BigDecimal recordedCostTotal,
                    List<OrderDirectCostController.CostView> directCosts) {}
}
