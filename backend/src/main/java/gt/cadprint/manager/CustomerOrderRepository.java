package gt.cadprint.manager;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    Optional<CustomerOrder> findByQuotationId(Long quotationId);

    Optional<CustomerOrder> findByQuotationIdAndRevisionNumber(Long quotationId, int revisionNumber);
}
