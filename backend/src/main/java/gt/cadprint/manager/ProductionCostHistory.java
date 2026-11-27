package gt.cadprint.manager;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

import gt.cadprint.manager.ProductionCostDtos.*;
import gt.cadprint.manager.ProductionCostService.Prepared;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static gt.cadprint.manager.ProductionCostMath.*;

@Service
@Transactional(readOnly = true)
class ProductionCostHistory {
    private final ProductionCalculationRepo calculations;
    private final FdmCalculationDetailRepo fdm;
    private final SublimationCalculationDetailRepo sublimation;
    private final ProductionCostLineRepo costs;
    private final ProductionPriceEstimateRepo estimates;
    private final EntityManager entityManager;

    ProductionCostHistory(ProductionCalculationRepo calculations, FdmCalculationDetailRepo fdm,
                          SublimationCalculationDetailRepo sublimation, ProductionCostLineRepo costs,
                          ProductionPriceEstimateRepo estimates, EntityManager entityManager) {
        this.calculations = calculations;
        this.fdm = fdm;
        this.sublimation = sublimation;
        this.costs = costs;
        this.estimates = estimates;
        this.entityManager = entityManager;
    }

    HistoryPage list(ProductionTechnique technique, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw invalid("Paginación inválida");
        }
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var found = technique == null ? calculations.findAll(pageable) : calculations.findByTechnique(technique, pageable);
        return new HistoryPage(found.map(this::item).getContent(), page, size, found.getTotalElements(), found.getTotalPages());
    }

    HistoryDetail detail(Long id) {
        ProductionCalculation calculation = calculations.findById(id).orElseThrow(() -> missing("Cálculo no encontrado"));
        return detail(calculation);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    HistoryDetail save(Prepared prepared, String username, Consumer<ProductionCalculation> storeTechnicalDetail) {
        Preview preview = prepared.preview();
        var sources = prepared.sources();
        var input = prepared.input();
        BigDecimal sum = preview.lines().stream().map(CostLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(preview.totalCost()) != 0) {
            throw conflict("El desglose no coincide con el costo total");
        }
        var calculation = new ProductionCalculation();
        calculation.technique = preview.technique();
        calculation.formulaVersion = preview.formulaVersion();
        calculation.username = username;
        calculation.jobDescription = input.jobDescription().strip();
        calculation.expectedGoodUnits = preview.expectedGoodUnits();
        calculation.costProfile = sources.profile();
        calculation.machine = sources.machine();
        calculation.product = sources.product();
        calculation.variant = sources.variant();
        calculation.commercialName = sources.name();
        calculation.commercialSku = sources.sku();
        calculation.currency = preview.currency();
        calculation.productiveSeconds = preview.productiveSeconds();
        calculation.indirectRatePerHour = preview.indirectRatePerHour();
        calculation.indirectCost = preview.indirectCost();
        calculation.totalCost = preview.totalCost();
        calculation.unitCost = preview.unitCost();
        calculations.save(calculation);
        storeTechnicalDetail.accept(calculation);
        for (CostLine line : preview.lines()) {
            costs.save(costLine(calculation, line));
        }
        estimates.save(estimate(calculation, preview.price()));
        try {
            entityManager.flush();
        } catch (ConstraintViolationException exception) {
            throw conflict("No se pudo guardar el c?lculo por un conflicto de integridad");
        }
        return detail(calculation);
    }

    // Called inside the transaction opened for the calculation and its history.
    @Transactional(propagation = Propagation.MANDATORY)
    void store(FdmCalculationDetail detail) {
        fdm.save(detail);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void store(SublimationCalculationDetail detail) {
        sublimation.save(detail);
    }

    private HistoryDetail detail(ProductionCalculation calculation) {
        Long id = calculation.id;
        var fdmDetail = fdm.findById(id).map(d -> new FdmHistory(d.printSeconds, d.laborSeconds, d.partMassGrams,
                d.supportMassGrams)).orElse(null);
        var sublimationDetail = sublimation.findById(id).map(d -> new SublimationHistory(d.cycleCount,
                d.pressSecondsPerCycle, d.preparationSeconds, d.laborSeconds)).orElse(null);
        var lines = costs.findByCalculationIdOrderByPositionAsc(id).stream().map(this::lineView).toList();
        var price = estimates.findById(id).map(p -> new PriceEstimate(p.targetMarginPct, p.suggestedUnitPrice,
                p.chosenUnitPrice, p.grossProfitPerUnit, p.actualMarginPct, p.belowCost)).orElse(null);
        return new HistoryDetail(item(calculation), fdmDetail, sublimationDetail, lines, price);
    }

    private HistoryItem item(ProductionCalculation row) {
        return new HistoryItem(row.id, row.technique, row.formulaVersion, row.username, row.createdAt,
                row.jobDescription, row.expectedGoodUnits, id(row.product), id(row.variant), id(row.costProfile),
                id(row.machine), row.commercialName, row.commercialSku, row.currency, row.productiveSeconds,
                row.indirectRatePerHour, row.indirectCost, row.totalCost, row.unitCost);
    }

    private Long id(Object entity) {
        return entity == null ? null : (Long) entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
    }

    private CostLine lineView(ProductionCostLine line) {
        return new CostLine(line.position, line.costType, line.description, line.quantity, line.unit, line.appliedRate,
                line.amount, line.costSource, id(line.material), line.materialSku, line.materialName,
                line.materialUnit, line.conversionFactor);
    }

    private ProductionCostLine costLine(ProductionCalculation calculation, CostLine input) {
        var line = new ProductionCostLine();
        line.calculation = calculation;
        line.position = input.position();
        line.costType = input.costType();
        line.description = input.description();
        line.quantity = input.quantity();
        line.unit = input.unit();
        line.appliedRate = input.appliedRate();
        line.amount = input.amount();
        line.costSource = input.costSource();
        line.material = input.materialId() == null ? null : entityManager.getReference(Material.class, input.materialId());
        line.materialSku = input.materialSku();
        line.materialName = input.materialName();
        line.materialUnit = input.materialUnit();
        line.conversionFactor = input.conversionFactor();
        return line;
    }

    private ProductionPriceEstimate estimate(ProductionCalculation calculation, PriceEstimate input) {
        var price = new ProductionPriceEstimate();
        price.calculation = calculation;
        price.targetMarginPct = input.targetMarginPct();
        price.suggestedUnitPrice = input.suggestedUnitPrice();
        price.chosenUnitPrice = input.chosenUnitPrice();
        price.grossProfitPerUnit = input.grossProfitPerUnit();
        price.actualMarginPct = input.actualMarginPct();
        price.belowCost = input.belowCost();
        return price;
    }
}
