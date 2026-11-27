package gt.cadprint.manager;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PurchaseRepo extends JpaRepository<Purchase, Long>, JpaSpecificationExecutor<Purchase> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select purchase from Purchase purchase where purchase.id = :id")
    Optional<Purchase> locked(@Param("id") Long id);

    List<Purchase> findBySupplierIdAndDocumentNumberIgnoreCaseOrderByIdAsc(Long supplierId, String documentNumber);
}

interface PurchaseLineRepo extends JpaRepository<PurchaseLine, Long> {
    List<PurchaseLine> findByPurchaseIdOrderByIdAsc(Long purchaseId);
}

interface PurchaseReceiptRepo extends JpaRepository<PurchaseReceipt, Long> {
    Optional<PurchaseReceipt> findByPurchaseId(Long purchaseId);
}

interface PurchaseEventRepo extends JpaRepository<PurchaseEvent, Long> {
    List<PurchaseEvent> findByPurchaseIdOrderByCreatedAtDescIdDesc(Long purchaseId);
}
