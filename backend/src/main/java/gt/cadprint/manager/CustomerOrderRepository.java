package gt.cadprint.manager;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;

import org.springframework.data.jpa.repository.JpaRepository;

interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    Optional<CustomerOrder> findByQuotationId(Long quotationId);

    Optional<CustomerOrder> findByQuotationIdAndRevisionNumber(Long quotationId, int revisionNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CustomerOrder> findForUpdateByQuotationIdAndRevisionNumber(Long quotationId, int revisionNumber);
}
