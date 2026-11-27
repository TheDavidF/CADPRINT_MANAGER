package gt.cadprint.manager;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface ProductImageRepo extends JpaRepository<ProductImage, Long> {
    Optional<ProductImage> findByProductId(Long productId);
}
