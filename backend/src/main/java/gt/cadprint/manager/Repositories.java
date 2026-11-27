package gt.cadprint.manager;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MaterialRepo extends JpaRepository<Material, Long> {
    boolean existsBySkuIgnoreCase(String sku);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select material from Material material where material.id = :id")
    Optional<Material> locked(@Param("id") Long id);
}

interface MovementRepo extends JpaRepository<Movement, Long> {
    boolean existsByMaterialId(Long materialId);

    List<Movement> findTop100ByOrderByCreatedAtDescIdDesc();

    List<Movement> findByPurchaseLinePurchaseIdOrderByPurchaseLineIdAsc(Long purchaseId);
}

interface CategoryRepo extends JpaRepository<Category, Long> {
    Optional<Category> findByNameKey(String nameKey);

    List<Category> findAllByOrderByNameKeyAsc();
}

interface MaterialChangeRepo extends JpaRepository<MaterialChange, Long> {
    List<MaterialChange> findByMaterialIdOrderByChangedAtDescIdDesc(Long materialId);
}

interface SupplierRepo extends JpaRepository<Supplier, Long> {
}