package gt.cadprint.manager;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface OrderPaymentRepository extends JpaRepository<OrderPayment, Long> {
    List<OrderPayment> findAllByCustomerOrderIdOrderByIdAsc(Long orderId);
}
