package gt.cadprint.manager;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface OrderDirectCostRepository extends JpaRepository<OrderDirectCost, Long> {
    Optional<OrderDirectCost> findByCustomerOrderIdAndRequestId(Long orderId, String requestId);

    List<OrderDirectCost> findAllByCustomerOrderIdOrderByIncurredOnDescIdDesc(Long orderId);
}
