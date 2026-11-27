package gt.cadprint.manager;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class CustomerOrderService {
    private final CustomerOrderRepository orders;
    private final QuotationRepo quotations;
    private final QuotationRevisionRepo revisions;
    private final QuotationService quotationService;

    CustomerOrderService(CustomerOrderRepository orders, QuotationRepo quotations,
                         QuotationRevisionRepo revisions, QuotationService quotationService) {
        this.orders = orders;
        this.quotations = quotations;
        this.revisions = revisions;
        this.quotationService = quotationService;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    OrderView create(Long quotationId, int revisionNumber, String actor) {
        quotations.findByIdForUpdate(quotationId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cotización no encontrada"));
        var revision = revisions.findByQuotationIdAndRevisionNumber(quotationId, revisionNumber).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Revisión no encontrada"));
        if (revision.status != QuotationStatus.ACEPTADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo puedes crear pedidos desde revisiones aceptadas");
        }
        var order = orders.findByQuotationId(quotationId)
                .orElseGet(() -> orders.save(new CustomerOrder(quotationId, revisionNumber, actor)));
        if (order.revisionNumber != revisionNumber) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cotización ya tiene un pedido");
        }
        return view(order);
    }

    @Transactional(readOnly = true)
    OrderView detail(Long quotationId, int revisionNumber) {
        return orders.findByQuotationIdAndRevisionNumber(quotationId, revisionNumber)
                .map(this::view)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
    }

    private OrderView view(CustomerOrder order) {
        return new OrderView(order.id, order.quotationId, order.createdAt, order.createdBy,
                quotationService.commercial(order.quotationId, order.revisionNumber));
    }

    record OrderView(Long id, Long quotationId, Instant createdAt, String createdBy,
                     QuotationDtos.CommercialView quotation) {}
}
