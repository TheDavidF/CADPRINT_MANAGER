package gt.cadprint.manager;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProductionConsumptionRepository extends JpaRepository<ProductionConsumption, Long> {
    @EntityGraph(attributePaths = {"movement", "movement.material"})
    List<ProductionConsumption> findAllByCustomerOrderIdOrderByIdAsc(Long orderId);

    @EntityGraph(attributePaths = {"movement", "movement.material"})
    Optional<ProductionConsumption> findByCustomerOrderIdAndRequestId(Long orderId, String requestId);
}
