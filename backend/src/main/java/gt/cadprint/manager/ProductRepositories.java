package gt.cadprint.manager;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ProductCategoryRepo extends JpaRepository<ProductCategory, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select category from ProductCategory category where category.id = :id")
    Optional<ProductCategory> locked(@Param("id") Long id);

    Optional<ProductCategory> findByNormalizedName(String normalizedName);

    List<ProductCategory> findByActiveOrderByNameAsc(boolean active);
}

interface ProductRepo extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    @Override
    @EntityGraph(attributePaths = {"category", "sku"})
    Page<Product> findAll(Specification<Product> specification, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select product from Product product where product.id = :id")
    Optional<Product> locked(@Param("id") Long id);

    boolean existsByCategoryIdAndActiveTrue(Long categoryId);

    Optional<Product> findBySkuValue(String normalizedSku);

    List<Product> findByCategoryIdAndActiveOrderByIdAsc(Long categoryId, boolean active);

    List<Product> findByActiveOrderByIdAsc(boolean active);
}

interface ProductVariantRepo extends JpaRepository<ProductVariant, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select variant from ProductVariant variant where variant.product.id = :productId and variant.id = :id")
    Optional<ProductVariant> locked(@Param("productId") Long productId, @Param("id") Long id);

    Optional<ProductVariant> findBySkuValue(String normalizedSku);

    List<ProductVariant> findByProductIdAndActiveOrderByIdAsc(Long productId, boolean active);

    @EntityGraph(attributePaths = "sku")
    List<ProductVariant> findByProductIdOrderByIdAsc(Long productId);
}
