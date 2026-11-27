package gt.cadprint.manager;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.Repository;

interface ProductionCostProfileRepo extends JpaRepository<ProductionCostProfile, Long> {
    Optional<ProductionCostProfile> findByCode(String code);

    List<ProductionCostProfile> findByActiveOrderByCodeAsc(boolean active);
}

interface ProductionMachineRepo extends JpaRepository<ProductionMachine, Long> {
    Optional<ProductionMachine> findByCode(String code);

    List<ProductionMachine> findByTechniqueAndActiveOrderByCodeAsc(ProductionTechnique technique, boolean active);
}

interface ProductionCalculationRepo extends Repository<ProductionCalculation, Long> {
    ProductionCalculation save(ProductionCalculation calculation);

    Optional<ProductionCalculation> findById(Long id);

    Page<ProductionCalculation> findAll(Pageable pageable);

    Page<ProductionCalculation> findByTechnique(ProductionTechnique technique, Pageable pageable);

    List<ProductionCalculation> findByTechniqueOrderByCreatedAtDescIdDesc(ProductionTechnique technique);
}

interface FdmCalculationDetailRepo extends Repository<FdmCalculationDetail, Long> {
    FdmCalculationDetail save(FdmCalculationDetail detail);

    Optional<FdmCalculationDetail> findById(Long id);
}

interface SublimationCalculationDetailRepo extends Repository<SublimationCalculationDetail, Long> {
    SublimationCalculationDetail save(SublimationCalculationDetail detail);

    Optional<SublimationCalculationDetail> findById(Long id);
}

interface ProductionCostLineRepo extends Repository<ProductionCostLine, Long> {
    ProductionCostLine save(ProductionCostLine line);

    List<ProductionCostLine> findByCalculationIdOrderByPositionAsc(Long calculationId);
}
