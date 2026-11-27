package gt.cadprint.manager;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import gt.cadprint.manager.ProductionCostDtos.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class ProductionCostRestartTest {
    @Test
    void preservesTheSavedEstimateAndItsBreakdownAcrossRestarts(@TempDir Path directory) {
        String url = "jdbc:h2:file:" + directory.resolve("production-cost-restart");
        long id;
        try (var context = start(url)) {
            var settings = context.getBean(ProductionCostSettings.class);
            var profile = settings.saveProfile(null, new ProfileInput("P", "Perfil", BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, true, null), "admin");
            var machine = settings.saveMachine(null, new MachineInput("M", "Impresora", ProductionTechnique.FDM,
                    BigDecimal.ONE, BigDecimal.ZERO, true, null), "admin");
            Material material = new Material();
            material.name = "PLA";
            material.sku = "PLA-RESTART";
            material.unit = "g";
            material.unitCost = new BigDecimal("0.08");
            material.category = context.getBean(CategoryRepo.class).findByNameKey("filamento").orElseThrow();
            long materialId = context.getBean(MaterialRepo.class).saveAndFlush(material).id;
            var common = new CommonInput("Pieza", 1, profile.id(), machine.id(), null, null, 0L, null,
                    List.of(new MaterialInput(materialId, new BigDecimal("1000"), "g", false, null)),
                    new BigDecimal("20"), null);
            var input = new FdmInput(common, 3600, 0L, new BigDecimal("1000"), null);
            var service = context.getBean(ProductionCostService.class);
            var preview = service.previewFdm(input, "operador");
            id = service.saveFdm(new FdmSave(input, preview.previewToken()), "operador").calculation().id();
        }
        try (var context = start(url + ";IFEXISTS=TRUE")) {
            var result = context.getBean(ProductionCostHistory.class).detail(id);
            assertThat(result.calculation().totalCost()).isEqualByComparingTo("80.00");
            assertThat(result.price().chosenUnitPrice()).isEqualByComparingTo("100.00");
            assertThat(result.price().actualMarginPct()).isEqualByComparingTo("20.0000");
            assertThat(result.fdm().printSeconds()).isEqualTo(3600);
            assertThat(result.lines().getFirst().materialSku()).isEqualTo("PLA-RESTART");
        }
    }

    private ConfigurableApplicationContext start(String url) {
        return new SpringApplicationBuilder(CadprintApplication.class).run(
                "--server.port=0", "--spring.datasource.url=" + url,
                "--CADPRINT_ADMIN_PASSWORD=test-admin-password",
                "--CADPRINT_OPERATOR_PASSWORD=test-operator-password");
    }
}
