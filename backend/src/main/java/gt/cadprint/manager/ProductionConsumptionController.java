package gt.cadprint.manager;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

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
@RequestMapping("/api/quotations/{quotationId}/revisions/{revisionNumber}/order/production/consumptions")
class ProductionConsumptionController {
    private final CustomerOrderRepository orders;
    private final ProductionConsumptionRepository consumptions;
    private final InventoryService inventory;

    ProductionConsumptionController(CustomerOrderRepository orders, ProductionConsumptionRepository consumptions,
                                    InventoryService inventory) {
        this.orders = orders;
        this.consumptions = consumptions;
        this.inventory = inventory;
    }

    @GetMapping
    @Transactional(readOnly = true)
    List<ConsumptionView> list(@PathVariable Long quotationId, @PathVariable int revisionNumber) {
        var order = orders.findByQuotationIdAndRevisionNumber(quotationId, revisionNumber)
                .orElseThrow(ProductionConsumptionController::missingOrder);
        return consumptions.findAllByCustomerOrderIdOrderByIdAsc(order.id).stream()
                .map(ConsumptionView::from).toList();
    }

    @PostMapping
    @Transactional(isolation = Isolation.READ_COMMITTED)
    ConsumptionView register(@PathVariable Long quotationId, @PathVariable int revisionNumber,
                             @RequestBody @Valid ConsumptionInput input, Principal principal) {
        var order = orders.findForUpdateByQuotationIdAndRevisionNumber(quotationId, revisionNumber)
                .orElseThrow(ProductionConsumptionController::missingOrder);
        if (order.productionStartedAt == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Inicia la producción antes de registrar consumo");
        }

        String requestId = input.requestId().toString();
        var existing = consumptions.findByCustomerOrderIdAndRequestId(order.id, requestId);
        if (existing.isPresent()) {
            var consumption = existing.get();
            if (!consumption.movement.material.getId().equals(input.materialId())
                    || consumption.movement.quantity.compareTo(input.quantity()) != 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud ya registró otro consumo");
            }
            return ConsumptionView.from(consumption);
        }

        var movement = inventory.consumeForProduction(input.materialId(), input.quantity(), order.id, principal.getName());
        return ConsumptionView.from(consumptions.save(new ProductionConsumption(order.id, movement, requestId)));
    }

    private static ResponseStatusException missingOrder() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado");
    }

    record ConsumptionInput(@NotNull UUID requestId, @NotNull @Positive Long materialId,
                            @NotNull @DecimalMin("0.001") @Digits(integer = 13, fraction = 3) BigDecimal quantity) {}

    record ConsumptionView(Long id, Long movementId, Long materialId, String materialName, String unit,
                           BigDecimal quantity, BigDecimal afterStock, String username, Instant createdAt) {
        static ConsumptionView from(ProductionConsumption consumption) {
            var movement = consumption.movement;
            return new ConsumptionView(consumption.id, movement.id, movement.material.getId(),
                    movement.material.name, movement.material.unit, movement.quantity, movement.afterStock,
                    movement.username, movement.createdAt);
        }
    }
}
