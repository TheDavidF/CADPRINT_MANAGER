package gt.cadprint.manager;

import java.util.Optional;

import org.springframework.data.repository.Repository;

interface ProductionPriceEstimateRepo extends Repository<ProductionPriceEstimate, Long> {
    ProductionPriceEstimate save(ProductionPriceEstimate estimate);

    Optional<ProductionPriceEstimate> findById(Long calculationId);
}
