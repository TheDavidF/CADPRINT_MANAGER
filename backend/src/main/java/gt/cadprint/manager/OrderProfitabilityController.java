package gt.cadprint.manager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/quotations/{quotationId}/revisions/{revisionNumber}/order/profitability")
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

    @GetMapping
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    Estimate estimate(@PathVariable Long quotationId, @PathVariable int revisionNumber) {
        var order = orders.findByQuotationIdAndRevisionNumber(quotationId, revisionNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        return estimate(order);
    }

    @PostMapping("/confirm")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    Estimate confirm(@PathVariable Long quotationId, @PathVariable int revisionNumber,
                     @Valid @RequestBody ConfirmationInput input, Principal principal) {
        var order = orders.findForUpdateByQuotationIdAndRevisionNumber(quotationId, revisionNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        var estimate = estimate(order);
        if (estimate.consumptionCount() != input.consumptionCount()
                || estimate.directCosts().size() != input.directCostCount()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Los costos cambiaron. Revisa los importes antes de confirmar");
        }
        if (estimate.unvaluedConsumptions() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hay consumos sin costo registrado");
        }
        if (order.costsConfirmedAt != null) {
            return estimate;
        }
        order.costsConfirmedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        order.costsConfirmedBy = principal.getName();
        return estimate(order);
    }

    private Estimate estimate(CustomerOrder order) {
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
        BigDecimal marginPercent = margin(estimatedProfit, sales);
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
        BigDecimal recordedTotal = recordedMaterialCost.add(directTotal).setScale(2, RoundingMode.HALF_UP);
        BigDecimal realProfit = null;
        BigDecimal differenceExcludingShipping = null;
        if (order.costsConfirmedAt != null && unvaluedConsumptions == 0) {
            realProfit = revision.totalAmount.subtract(recordedTotal);
            if (estimatedProfit != null) {
                var shippingPaid = directCosts.stream().filter(cost -> cost.category == OrderDirectCost.Category.ENVIO)
                        .map(cost -> cost.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
                differenceExcludingShipping = sales.subtract(recordedTotal.subtract(shippingPaid)).subtract(estimatedProfit);
            }
        }
        return new Estimate(sales, coveredCost, quotedLines.size(), missingLines, estimatedProfit, marginPercent,
                recordedMaterialCost.setScale(2, RoundingMode.HALF_UP), recordedConsumptions.size(), unvaluedConsumptions,
                directTotal, recordedTotal, directCosts.stream().map(OrderDirectCostController.CostView::from).toList(),
                order.costsConfirmedAt, order.costsConfirmedBy, realProfit, margin(realProfit, revision.totalAmount),
                differenceExcludingShipping);
    }

    private BigDecimal margin(BigDecimal profit, BigDecimal sales) {
        return profit != null && sales.signum() > 0
                ? profit.movePointRight(2).divide(sales, 2, RoundingMode.HALF_UP)
                : null;
    }

    record ConfirmationInput(@NotNull @PositiveOrZero Integer consumptionCount,
                             @NotNull @PositiveOrZero Integer directCostCount) {}

    record Estimate(BigDecimal salesExcludingShipping, BigDecimal coveredEstimatedCost,
                    int lineCount, int missingCostLines, BigDecimal estimatedProfit, BigDecimal marginPercent,
                    BigDecimal recordedMaterialCost, int consumptionCount, int unvaluedConsumptions,
                    BigDecimal directCostTotal, BigDecimal recordedCostTotal,
                    List<OrderDirectCostController.CostView> directCosts, Instant costsConfirmedAt,
                    String costsConfirmedBy, BigDecimal realProfit, BigDecimal realMarginPercent,
                    BigDecimal profitDifferenceExcludingShipping) {}
}
