package gt.cadprint.manager;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class OrderDirectCostController {
    private final CustomerOrderRepository orders;
    private final OrderDirectCostRepository costs;

    OrderDirectCostController(CustomerOrderRepository orders, OrderDirectCostRepository costs) {
        this.orders = orders;
        this.costs = costs;
    }

    @PostMapping("/api/quotations/{quotationId}/revisions/{revisionNumber}/order/direct-costs")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    CostView record(@PathVariable Long quotationId, @PathVariable int revisionNumber,
                    @Valid @RequestBody CostInput input, Principal principal) {
        if (input.incurredOn().getYear() < 1000 || input.incurredOn().getYear() > 9999) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha del costo está fuera del rango admitido");
        }
        var order = orders.findForUpdateByQuotationIdAndRevisionNumber(quotationId, revisionNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        String requestId = input.requestId().toString();
        String description = input.description().strip();
        var existing = costs.findByCustomerOrderIdAndRequestId(order.id, requestId);
        if (existing.isPresent()) {
            var cost = existing.get();
            if (cost.category != input.category() || !cost.description.equals(description)
                    || cost.amount.compareTo(input.amount()) != 0 || !cost.incurredOn.equals(input.incurredOn())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud ya registró otro costo");
            }
            return CostView.from(cost);
        }
        order.clearCostConfirmation();
        return CostView.from(costs.save(new OrderDirectCost(order.id, requestId, input.category(), description,
                input.amount().setScale(2), input.incurredOn(), principal.getName())));
    }

    record CostInput(@NotNull UUID requestId, @NotNull OrderDirectCost.Category category,
                     @NotBlank @Size(max = 255) String description,
                     @NotNull @DecimalMin("0.01") @Digits(integer = 25, fraction = 2) BigDecimal amount,
                     @NotNull LocalDate incurredOn) {}

    record CostView(Long id, OrderDirectCost.Category category, String description, BigDecimal amount,
                    LocalDate incurredOn, Instant recordedAt, String recordedBy) {
        static CostView from(OrderDirectCost cost) {
            return new CostView(cost.id, cost.category, cost.description, cost.amount, cost.incurredOn,
                    cost.recordedAt, cost.recordedBy);
        }
    }
}
