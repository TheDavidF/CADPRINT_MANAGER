package gt.cadprint.manager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import gt.cadprint.manager.ProductionCostDtos.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static gt.cadprint.manager.ProductionCostMath.*;

@Service
class ProductionCostService {
    record Sources(ProductionCostProfile profile, ProductionMachine machine, Product product, ProductVariant variant,
                   String name, String sku, BigDecimal watts, List<CostLine> materials) {
    }

    record Prepared(CommonInput input, Sources sources, Preview preview) {
    }

    private record Signature(String username, Object input, Long profileVersion, Long machineVersion,
                             String name, String sku, BigDecimal watts, Preview totals) {
    }

    private static final String FORMULA_VERSION = "1";
    private final EntityManager entityManager;
    private final ProductionMaterialValuation valuation;
    private final CommercialEstimateService commercial;
    private final FdmCostCalculator fdm;
    private final SublimationCostCalculator sublimation;
    private final ProductionPreviewToken tokens;
    private final ProductionCostHistory history;

    ProductionCostService(EntityManager entityManager, ProductionMaterialValuation valuation,
                          CommercialEstimateService commercial, FdmCostCalculator fdm,
                          SublimationCostCalculator sublimation, ProductionPreviewToken tokens,
                          ProductionCostHistory history) {
        this.entityManager = entityManager;
        this.valuation = valuation;
        this.commercial = commercial;
        this.fdm = fdm;
        this.sublimation = sublimation;
        this.tokens = tokens;
        this.history = history;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    Preview previewFdm(FdmInput input, String actor) {
        return prepareFdm(input, actor, false).preview();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    Preview previewSublimation(SublimationInput input, String actor) {
        return prepareSublimation(input, actor, false).preview();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    HistoryDetail saveFdm(FdmSave request, String actor) {
        Prepared prepared = verify(request.previewToken(), prepareForSave(() -> prepareFdm(request.input(), actor, true)));
        return history.save(prepared, actor, calculation -> history.store(fdm.detail(request.input(), calculation)));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    HistoryDetail saveSublimation(SublimationSave request, String actor) {
        Prepared prepared = verify(request.previewToken(),
                prepareForSave(() -> prepareSublimation(request.input(), actor, true)));
        return history.save(prepared, actor, calculation -> history.store(sublimation.detail(request.input(), calculation)));
    }

    private Prepared prepareForSave(java.util.function.Supplier<Prepared> operation) {
        try {
            return operation.get();
        } catch (ResponseStatusException exception) {
            // A previously valid preview can lose an active or compatible source before saving.
            if (exception.getStatusCode().isSameCodeAs(org.springframework.http.HttpStatus.NOT_FOUND)
                    || exception.getStatusCode().isSameCodeAs(org.springframework.http.HttpStatus.BAD_REQUEST)) {
                throw conflict("No se pudo validar la vista previa. Recalcula: " + exception.getReason());
            }
            throw exception;
        }
    }

    private Prepared prepareFdm(FdmInput input, String actor, boolean lock) {
        var sources = sources(input.common(), ProductionTechnique.FDM, lock);
        return summarize(input.common(), input, actor, ProductionTechnique.FDM, sources,
                fdm.calculate(input, sources.materials(), sources.profile(), sources.machine(), sources.watts()));
    }

    private Prepared prepareSublimation(SublimationInput input, String actor, boolean lock) {
        var sources = sources(input.common(), ProductionTechnique.SUBLIMATION, lock);
        return summarize(input.common(), input, actor, ProductionTechnique.SUBLIMATION, sources,
                sublimation.calculate(input, sources.materials(), sources.profile(), sources.machine(), sources.watts()));
    }

    private Sources sources(CommonInput input, ProductionTechnique technique, boolean lock) {
        if (input.productId() != null && input.variantId() != null) {
            throw invalid("Indica un producto o una variante");
        }
        ProductionCostProfile profile = find(ProductionCostProfile.class, input.costProfileId(), lock, "Perfil de costos");
        ProductionMachine machine = find(ProductionMachine.class, input.machineId(), lock, "Máquina");
        if (!profile.active || !machine.active || machine.technique != technique) {
            throw invalid("El perfil y la máquina deben estar activos y la máquina debe corresponder a la técnica");
        }
        BigDecimal watts = input.powerWatts() == null ? machine.nominalPowerWatts : input.powerWatts();
        if (watts == null) {
            throw invalid("Indica la potencia estimada o configura la potencia nominal de la máquina");
        }

        Product product = input.productId() == null ? null : find(Product.class, input.productId(), lock, "Producto");
        ProductVariant variant = input.variantId() == null ? null
                : find(ProductVariant.class, input.variantId(), lock, "Variante");
        String name = variant != null ? variant.name : product != null ? product.name : null;
        String sku = variant != null ? variant.getSku() : product != null ? product.getSku() : null;
        return new Sources(profile, machine, product, variant, name, sku, watts, materialLines(input.materials(), lock));
    }

    private List<CostLine> materialLines(List<MaterialInput> inputs, boolean lock) {
        Map<Long, Material> materials = new TreeMap<>();
        for (Long id : inputs.stream().map(MaterialInput::materialId).distinct().sorted().toList()) {
            materials.put(id, find(Material.class, id, lock, "Material"));
        }
        List<CostLine> lines = new ArrayList<>();
        for (MaterialInput input : inputs) {
            lines.add(valuation.value(lines.size() + 1, input, materials.get(input.materialId())));
        }
        return lines;
    }

    private Prepared summarize(CommonInput common, Object input, String actor, ProductionTechnique technique,
                               Sources sources, List<CostLine> lines) {
        BigDecimal indirect = lines.stream().filter(line -> line.costType() == ProductionCostType.INDIRECT)
                .map(CostLine::amount).findFirst().orElse(BigDecimal.ZERO.setScale(2));
        BigDecimal total = money(lines.stream().map(CostLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal unit = divide(total, BigDecimal.valueOf(common.expectedGoodUnits()), 27, 8);
        var price = commercial.estimate(unit, common.targetMarginPct(), common.chosenUnitPrice());
        var unsigned = new Preview(technique, FORMULA_VERSION, sources.profile().currency, common.expectedGoodUnits(),
                common.productiveSeconds(), sources.profile().indirectRatePerHour, indirect, total, unit, lines, price, null);
        String token = tokens.sign(new Signature(actor, input, sources.profile().version, sources.machine().version,
                sources.name(), sources.sku(), sources.watts(), unsigned));
        return new Prepared(common, sources, new Preview(technique, FORMULA_VERSION, unsigned.currency(),
                unsigned.expectedGoodUnits(), unsigned.productiveSeconds(), unsigned.indirectRatePerHour(), indirect,
                total, unit, lines, price, token));
    }

    private Prepared verify(String token, Prepared prepared) {
        if (!tokens.matches(token, prepared.preview().previewToken())) {
            throw conflict("La vista previa cambió o dejó de ser válida. Recalcula antes de guardar");
        }
        return prepared;
    }

    private <T> T find(Class<T> type, Long id, boolean lock, String label) {
        T entity = entityManager.find(type, id, lock ? LockModeType.PESSIMISTIC_WRITE : LockModeType.NONE);
        if (entity == null) {
            throw missing(label + " no encontrado: " + id);
        }
        return Hibernate.unproxy(entity, type);
    }
}
