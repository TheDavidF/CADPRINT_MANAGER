package gt.cadprint.manager;

import java.security.Principal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quotations/{quotationId}/revisions/{revisionNumber}/order")
class CustomerOrderController {
    private final CustomerOrderService orders;

    CustomerOrderController(CustomerOrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    CustomerOrderService.OrderView detail(@PathVariable Long quotationId, @PathVariable int revisionNumber) {
        return orders.detail(quotationId, revisionNumber);
    }

    @PostMapping("/production/start")
    CustomerOrderService.OrderView startProduction(@PathVariable Long quotationId, @PathVariable int revisionNumber,
                                                   Principal principal) {
        return orders.startProduction(quotationId, revisionNumber, principal.getName());
    }

    @PostMapping
    CustomerOrderService.OrderView create(@PathVariable Long quotationId, @PathVariable int revisionNumber,
                                          Principal principal) {
        return orders.create(quotationId, revisionNumber, principal.getName());
    }
}
