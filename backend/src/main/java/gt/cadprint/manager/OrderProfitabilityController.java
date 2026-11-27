package gt.cadprint.manager;

import java.math.BigDecimal;
import java.math.RoundingMode;

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

    OrderProfitabilityController(CustomerOrderRepository orders, QuotationRevisionRepo revisions,
                                 QuotationLineRepo lines) {
        this.orders = orders;
        this.revisions = revisions;
        this.lines = lines;
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
        return new Estimate(sales, coveredCost, quotedLines.size(), missingLines, estimatedProfit, marginPercent);
    }

    record Estimate(BigDecimal salesExcludingShipping, BigDecimal coveredEstimatedCost,
                    int lineCount, int missingCostLines, BigDecimal estimatedProfit, BigDecimal marginPercent) {}
}
