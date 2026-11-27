package gt.cadprint.manager;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

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
@RequestMapping("/api/quotations/{quotationId}/revisions/{revisionNumber}/order/payments")
class OrderPaymentController {
    private final CustomerOrderRepository orders;
    private final OrderPaymentRepository payments;
    private final QuotationService quotations;

    OrderPaymentController(CustomerOrderRepository orders, OrderPaymentRepository payments, QuotationService quotations) {
        this.orders = orders;
        this.payments = payments;
        this.quotations = quotations;
    }

    @GetMapping
    @Transactional(readOnly = true)
    PaymentSummary list(@PathVariable Long quotationId, @PathVariable int revisionNumber) {
        var order = orders.findByQuotationIdAndRevisionNumber(quotationId, revisionNumber)
                .orElseThrow(OrderPaymentController::missingOrder);
        return summary(total(order), payments.findAllByCustomerOrderIdOrderByIdAsc(order.id));
    }

    @PostMapping
    @Transactional(isolation = Isolation.READ_COMMITTED)
    PaymentSummary register(@PathVariable Long quotationId, @PathVariable int revisionNumber,
                            @Valid @RequestBody PaymentInput input, Principal principal) {
        var order = orders.findForUpdateByQuotationIdAndRevisionNumber(quotationId, revisionNumber)
                .orElseThrow(OrderPaymentController::missingOrder);
        var history = new ArrayList<>(payments.findAllByCustomerOrderIdOrderByIdAsc(order.id));
        BigDecimal total = total(order);
        String requestId = input.requestId().toString();
        var existing = history.stream().filter(payment -> payment.requestId.equals(requestId)).findFirst();
        if (existing.isPresent()) {
            var payment = existing.get();
            if (payment.amount.compareTo(input.amount()) != 0 || payment.method != input.method()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud ya registró otro cobro");
            }
            return summary(total, history);
        }
        if (paid(history).add(input.amount()).compareTo(total) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El cobro supera el saldo pendiente");
        }
        history.add(payments.save(new OrderPayment(order.id, requestId,
                input.amount().setScale(2), input.method(), principal.getName())));
        return summary(total, history);
    }

    private BigDecimal total(CustomerOrder order) {
        return quotations.commercial(order.quotationId, order.revisionNumber).totals().totalAmount();
    }

    private PaymentSummary summary(BigDecimal total, List<OrderPayment> history) {
        BigDecimal paid = paid(history);
        return new PaymentSummary(total, paid, total.subtract(paid), history.stream().map(PaymentView::from).toList());
    }

    private BigDecimal paid(List<OrderPayment> history) {
        return history.stream().map(payment -> payment.amount).reduce(new BigDecimal("0.00"), BigDecimal::add);
    }

    private static ResponseStatusException missingOrder() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado");
    }

    record PaymentInput(@NotNull UUID requestId, @NotNull @DecimalMin("0.01")
                        @Digits(integer = 25, fraction = 2) BigDecimal amount, @NotNull OrderPayment.Method method) {}

    record PaymentSummary(BigDecimal total, BigDecimal paid, BigDecimal balance, List<PaymentView> payments) {}

    record PaymentView(Long id, BigDecimal amount, OrderPayment.Method method, Instant receivedAt, String receivedBy) {
        static PaymentView from(OrderPayment payment) {
            return new PaymentView(payment.id, payment.amount, payment.method, payment.receivedAt, payment.receivedBy);
        }
    }
}
