package gt.cadprint.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:production-costs-${random.uuid}",
        "CADPRINT_ADMIN_PASSWORD=test-admin-password",
        "CADPRINT_OPERATOR_PASSWORD=test-operator-password"
})
class ProductionCostPersistenceTest {
    @Autowired ProductionCostProfileRepo profiles;
    @Autowired ProductionMachineRepo machines;
    @Autowired ProductionCalculationRepo calculations;
    @Autowired FdmCalculationDetailRepo fdm;
    @Autowired SublimationCalculationDetailRepo sublimation;
    @Autowired ProductionCostLineRepo costs;
    @Autowired ProductCategoryRepo productCategories;
    @Autowired ProductRepo products;
    @Autowired ProductVariantRepo variants;
    @Autowired MaterialRepo materials;
    @Autowired CategoryRepo categories;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void keepsRatesAndMaterialSnapshotsIndependentFromCurrentValues() {
        var profile = profiles.saveAndFlush(profile("TARIFA-FDM"));
        var machine = machines.saveAndFlush(machine("FDM-01", ProductionTechnique.FDM));
        Material material = new Material();
        material.name = "PLA blanco";
        material.sku = "PLA-HIST";
        material.unit = "g";
        material.unitCost = decimal("0.1200");
        material.category = categories.findByNameKey("filamento").orElseThrow();
        material = materials.saveAndFlush(material);

        var calculation = calculation(ProductionTechnique.FDM);
        calculation.costProfile = profile;
        calculation.machine = machine;
        calculation.indirectRatePerHour = profile.indirectRatePerHour;
        calculation = calculations.save(calculation);

        var detail = new FdmCalculationDetail();
        detail.calculation = calculation;
        detail.printSeconds = 1200;
        detail.laborSeconds = 90;
        detail.partMassGrams = decimal("13.020000");
        detail.supportMassGrams = decimal("0.100000");
        saveFdm(detail);

        var line = line(calculation);
        line.costType = ProductionCostType.MATERIAL;
        line.costSource = ProductionCostSource.MATERIAL_REFERENCE;
        line.material = material;
        line.materialSku = material.sku;
        line.materialName = material.name;
        line.materialUnit = material.unit;
        line.conversionFactor = BigDecimal.ONE;
        line.unit = "g";
        line.appliedRate = material.unitCost;
        costs.save(line);

        profile.indirectRatePerHour = decimal("25");
        profiles.saveAndFlush(profile);
        machine.machineRatePerHour = decimal("39");
        machines.saveAndFlush(machine);
        material.unitCost = decimal("0.99");
        material.name = "PLA actualizado";
        materials.saveAndFlush(material);

        var stored = calculations.findById(calculation.id).orElseThrow();
        assertThat(stored.indirectRatePerHour).isEqualByComparingTo("3.25");
        assertThat(stored.unitCost).isEqualByComparingTo("3.125");
        assertThat(stored.totalCost).isEqualByComparingTo("6.25");
        assertThat(fdm.findById(calculation.id).orElseThrow().partMassGrams).isEqualByComparingTo("13.02");
        assertThat(costs.findByCalculationIdOrderByPositionAsc(calculation.id)).singleElement().satisfies(saved -> {
            assertThat(saved.appliedRate).isEqualByComparingTo("0.12");
            assertThat(saved.materialName).isEqualTo("PLA blanco");
            assertThat(saved.materialUnit).isEqualTo("g");
        });

        stored.totalCost = decimal("500");
        calculations.save(stored);
        assertThat(calculations.findById(stored.id).orElseThrow().totalCost).isEqualByComparingTo("6.25");
    }

    @Test
    void storesSublimationAndAllowsProductVariantOrNoCatalogReference() {
        var category = new ProductCategory();
        category.name = "Sustratos de prueba";
        var product = new Product("PROD-COSTOS");
        product.name = "Taza";
        product.productionMode = ProductionMode.BAJO_PEDIDO;
        product.pricingMode = PricingMode.REQUIERE_COTIZACION;
        product.category = productCategories.saveAndFlush(category);
        product = products.saveAndFlush(product);
        var variant = new ProductVariant(product, "VAR-COSTOS");
        variant.name = "Taza grande";
        variant = variants.saveAndFlush(variant);

        var productCost = calculation(ProductionTechnique.SUBLIMATION);
        productCost.product = product;
        productCost.commercialName = product.name;
        productCost.commercialSku = product.getSku();
        productCost = calculations.save(productCost);

        var variantCost = calculation(ProductionTechnique.SUBLIMATION);
        variantCost.variant = variant;
        variantCost.commercialName = variant.name;
        variantCost.commercialSku = variant.getSku();
        variantCost.supersedes = productCost;
        variantCost = calculations.save(variantCost);
        var standalone = calculations.save(calculation(ProductionTechnique.SUBLIMATION));

        var detail = new SublimationCalculationDetail();
        detail.calculation = variantCost;
        detail.cycleCount = 2;
        detail.pressSecondsPerCycle = 35;
        detail.preparationSeconds = 12;
        detail.laborSeconds = 20;
        saveSublimation(detail);
        assertThat(sublimation.findById(variantCost.id).orElseThrow().cycleCount).isEqualTo(2);

        assertThat(calculations.findById(productCost.id).orElseThrow().commercialSku).isEqualTo("PROD-COSTOS");
        assertThat(calculations.findById(variantCost.id).orElseThrow().commercialSku).isEqualTo("VAR-COSTOS");
        assertThat(calculations.findById(standalone.id).orElseThrow().product).isNull();
        assertThat(calculations.findById(standalone.id).orElseThrow().variant).isNull();
        long variantId = variant.id;
        long productCalculationId = productCost.id;
        assertThatThrownBy(() -> jdbc.update("UPDATE production_calculations SET variant_id = ? WHERE id = ?",
                variantId, productCalculationId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseEnforcesTechniquesReferencesPositionsAndAmounts() {
        var machine = machines.saveAndFlush(machine("ONLY-SUBLIMATION", ProductionTechnique.SUBLIMATION));
        var fdmCalculation = calculations.save(calculation(ProductionTechnique.FDM));
        var wrongMachine = calculation(ProductionTechnique.FDM);
        wrongMachine.machine = machine;
        assertThatThrownBy(() -> calculations.save(wrongMachine)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE production_calculations SET expected_good_units = 0 WHERE id = ?",
                fdmCalculation.id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE production_calculations SET total_cost = -1 WHERE id = ?",
                fdmCalculation.id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE production_calculations SET productive_seconds = -1 WHERE id = ?",
                fdmCalculation.id)).isInstanceOf(DataIntegrityViolationException.class);

        var wrongSublimation = new SublimationCalculationDetail();
        wrongSublimation.calculation = fdmCalculation;
        wrongSublimation.cycleCount = 1;
        wrongSublimation.pressSecondsPerCycle = 10;
        assertThatThrownBy(() -> saveSublimation(wrongSublimation)).isInstanceOf(DataIntegrityViolationException.class);

        var subCalculation = calculation(ProductionTechnique.SUBLIMATION);
        subCalculation.machine = machine;
        var received = calculations.save(subCalculation);
        var wrongFdm = new FdmCalculationDetail();
        wrongFdm.calculation = received;
        wrongFdm.printSeconds = 10;
        wrongFdm.partMassGrams = BigDecimal.ONE;
        assertThatThrownBy(() -> saveFdm(wrongFdm)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM production_machines WHERE id = ?", machine.id))
                .isInstanceOf(DataIntegrityViolationException.class);

        var entry = costs.save(line(fdmCalculation));
        assertThatThrownBy(() -> costs.save(line(fdmCalculation))).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE production_cost_lines SET amount = -1 WHERE id = ?", entry.id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM production_calculations WHERE id = ?", fdmCalculation.id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void protectsMasterCodesRatesAndVersions() {
        var profile = profiles.saveAndFlush(profile("UNIQUE-PROFILE"));
        var machine = machines.saveAndFlush(machine("UNIQUE-MACHINE", ProductionTechnique.FDM));
        assertThatThrownBy(() -> profiles.saveAndFlush(profile(profile.code))).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> machines.saveAndFlush(machine(machine.code, ProductionTechnique.FDM)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE production_cost_profiles SET labor_rate_per_hour = -1 WHERE id = ?",
                profile.id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE production_machines SET machine_rate_per_hour = -1 WHERE id = ?",
                machine.id)).isInstanceOf(DataIntegrityViolationException.class);
        var invalid = profile("TARIFA-PRECISION");
        invalid.electricityRatePerKwh = decimal("0.123456789");
        assertThatThrownBy(() -> profiles.saveAndFlush(invalid)).isInstanceOf(ConstraintViolationException.class);

        var profileWriter = profiles.findById(profile.id).orElseThrow();
        profileWriter.name = "Perfil nuevo";
        assertThat(profiles.saveAndFlush(profileWriter).version).isGreaterThan(profile.version);
        profile.name = "Edición obsoleta";
        assertThatThrownBy(() -> profiles.saveAndFlush(profile)).isInstanceOf(OptimisticLockingFailureException.class);

        var machineWriter = machines.findById(machine.id).orElseThrow();
        machineWriter.name = "Máquina nueva";
        assertThat(machines.saveAndFlush(machineWriter).version).isGreaterThan(machine.version);
        machine.name = "Edición obsoleta";
        assertThatThrownBy(() -> machines.saveAndFlush(machine)).isInstanceOf(OptimisticLockingFailureException.class);
    }

    private void saveFdm(FdmCalculationDetail detail) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            detail.calculation = calculations.findById(detail.calculation.id).orElseThrow();
            fdm.save(detail);
        });
    }

    private void saveSublimation(SublimationCalculationDetail detail) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            detail.calculation = calculations.findById(detail.calculation.id).orElseThrow();
            sublimation.save(detail);
        });
    }

    private ProductionCostProfile profile(String code) {
        var profile = new ProductionCostProfile();
        profile.code = code;
        profile.name = "Perfil";
        profile.electricityRatePerKwh = decimal("1.20");
        profile.laborRatePerHour = decimal("15");
        profile.indirectRatePerHour = decimal("3.25");
        profile.updatedBy = "admin";
        return profile;
    }

    private ProductionMachine machine(String code, ProductionTechnique technique) {
        var machine = new ProductionMachine();
        machine.code = code;
        machine.name = "Máquina";
        machine.technique = technique;
        machine.machineRatePerHour = decimal("4.50");
        machine.updatedBy = "admin";
        return machine;
    }

    private ProductionCalculation calculation(ProductionTechnique technique) {
        var calculation = new ProductionCalculation();
        calculation.technique = technique;
        calculation.formulaVersion = "fixture";
        calculation.username = "operador";
        calculation.jobDescription = "Trabajo de prueba";
        calculation.expectedGoodUnits = 2L;
        calculation.productiveSeconds = 3600L;
        calculation.indirectRatePerHour = decimal("3.25");
        calculation.indirectCost = decimal("3.25");
        calculation.totalCost = decimal("6.25");
        calculation.unitCost = decimal("3.125");
        return calculation;
    }

    private ProductionCostLine line(ProductionCalculation calculation) {
        var line = new ProductionCostLine();
        line.calculation = calculation;
        line.position = 1;
        line.costType = ProductionCostType.INDIRECT;
        line.costSource = ProductionCostSource.COST_PROFILE;
        line.description = "Costo histórico de prueba";
        line.quantity = BigDecimal.ONE;
        line.unit = "h";
        line.appliedRate = decimal("3.25");
        line.amount = decimal("3.25");
        return line;
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
