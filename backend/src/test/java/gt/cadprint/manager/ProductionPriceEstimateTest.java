package gt.cadprint.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:price-estimate-${random.uuid}",
        "CADPRINT_ADMIN_PASSWORD=test-admin-password",
        "CADPRINT_OPERATOR_PASSWORD=test-operator-password"
})
class ProductionPriceEstimateTest {
    @Autowired ProductionCalculationRepo calculations;
    @Autowired ProductionPriceEstimateRepo estimates;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;

    @Test
    void persistsSignedValuesAndPreservesFullDecimalScaleAndHistory() {
        long calculationId = calculation();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var estimate = new ProductionPriceEstimate();
            estimate.calculation = calculations.findById(calculationId).orElseThrow();
            estimate.targetMarginPct = new BigDecimal("99.9999");
            estimate.suggestedUnitPrice = new BigDecimal("1234567890123456789012345.67");
            estimate.chosenUnitPrice = new BigDecimal("75.00");
            estimate.grossProfitPerUnit = new BigDecimal("-5.00");
            estimate.actualMarginPct = new BigDecimal("-6.6667");
            estimate.belowCost = true;
            estimates.save(estimate);
        });

        var stored = estimates.findById(calculationId).orElseThrow();
        assertThat(stored.id).isEqualTo(calculationId);
        assertThat(stored.targetMarginPct).isEqualTo(new BigDecimal("99.9999"));
        assertThat(stored.suggestedUnitPrice).isEqualTo(new BigDecimal("1234567890123456789012345.67"));
        assertThat(stored.chosenUnitPrice).isEqualTo(new BigDecimal("75.00"));
        assertThat(stored.grossProfitPerUnit).isEqualTo(new BigDecimal("-5.00"));
        assertThat(stored.actualMarginPct).isEqualTo(new BigDecimal("-6.6667"));
        assertThat(stored.belowCost).isTrue();

        new TransactionTemplate(transactions).executeWithoutResult(status ->
                estimates.findById(calculationId).orElseThrow().chosenUnitPrice = new BigDecimal("100.00"));
        assertThat(estimates.findById(calculationId).orElseThrow().chosenUnitPrice).isEqualTo(new BigDecimal("75.00"));
        assertThat(calculations.findById(calculationId).orElseThrow().unitCost).isEqualTo(new BigDecimal("80.00000000"));
    }

    @Test
    void enforcesOneToOneAndPersistsNullMarginForZeroPrice() {
        long id = calculation();
        insert(id, "0", "80", "0", null);
        var stored = estimates.findById(id).orElseThrow();
        assertThat(stored.actualMarginPct).isNull();
        assertThat(stored.grossProfitPerUnit).isEqualTo(new BigDecimal("-80.00"));
        assertThat(stored.belowCost).isTrue();
        assertThatThrownBy(() -> insert(id, "0", "80", "0", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(-1L, "0", "80", "0", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM production_calculations WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "-0.0001, 80, 75, -6.6667",
            "100, 80, 75, -6.6667",
            "15, -0.01, 75, -6.6667",
            "15, 80, -0.01, -6.6667",
            "15, 80, 0, 0",
            "15, 80, 75,"
    })
    void rejectsInvalidValuesWithSqlConstraints(String target, String suggested, String chosen, String actual) {
        long id = calculation();
        assertThatThrownBy(() -> insert(id, target, suggested, chosen, actual))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(estimates.findById(id)).isEmpty();
    }

    @Test
    void migratesV6CopyAndPreservesTheHistoricalCalculationAndBreakdown(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("reference-v6");
        String url = "jdbc:h2:file:" + source;
        Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration/h2", "classpath:db/migration/common")
                .target("6").load().migrate();
        var prior = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        prior.update("""
                INSERT INTO production_calculations (id, technique, formula_version, username, created_at,
                    job_description, expected_good_units, currency, productive_seconds, indirect_rate_per_hour,
                    indirect_cost, total_cost, unit_cost)
                VALUES (101, 'FDM', 'v1', 'operador', CURRENT_TIMESTAMP, 'Pieza histórica', 1, 'GTQ', 3600, 10, 10, 80, 80)
                """);
        prior.update("""
                INSERT INTO production_fdm_details (calculation_id, print_seconds, labor_seconds, part_mass_grams)
                VALUES (101, 3600, 90, 20)
                """);
        prior.update("""
                INSERT INTO production_cost_lines (calculation_id, position, cost_type, description,
                    quantity, unit, applied_rate, amount, cost_source)
                VALUES (101, 1, 'INDIRECT', 'Indirectos', 1, 'h', 10, 10, 'COST_PROFILE')
                """);
        var header = prior.queryForList("SELECT * FROM production_calculations");
        var details = prior.queryForList("SELECT * FROM production_fdm_details");
        var lines = prior.queryForList("SELECT * FROM production_cost_lines");
        Path copy = directory.resolve("upgrade-v7");
        Files.copy(Path.of(source + ".mv.db"), Path.of(copy + ".mv.db"));
        String copyUrl = "jdbc:h2:file:" + copy + ";IFEXISTS=TRUE";
        var migrated = new JdbcTemplate(new DriverManagerDataSource(copyUrl, "sa", ""));

        try (var context = new SpringApplicationBuilder(CadprintApplication.class).run(
                "--server.port=0", "--spring.datasource.url=" + copyUrl,
                "--CADPRINT_ADMIN_PASSWORD=test-admin-password",
                "--CADPRINT_OPERATOR_PASSWORD=test-operator-password")) {
            assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            assertThat(migrated.queryForList("""
                    SELECT "version" FROM "flyway_schema_history"
                    WHERE "success" AND "version" IS NOT NULL ORDER BY "installed_rank"
                    """, String.class)).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");
            assertThat(migrated.queryForList("SELECT * FROM production_calculations")).isEqualTo(header);
            assertThat(migrated.queryForList("SELECT * FROM production_fdm_details")).isEqualTo(details);
            assertThat(migrated.queryForList("SELECT * FROM production_cost_lines")).isEqualTo(lines);
            assertThat(context.getBean(ProductionPriceEstimateRepo.class).findById(101L)).isEmpty();
        }
        assertThat(prior.queryForObject("""
                SELECT COUNT(*) FROM "flyway_schema_history" WHERE "version" = '7'
                """, Long.class)).isZero();
    }

    private long calculation() {
        var calculation = new ProductionCalculation();
        calculation.technique = ProductionTechnique.FDM;
        calculation.formulaVersion = "test";
        calculation.username = "operador";
        calculation.jobDescription = "Prueba de persistencia";
        calculation.expectedGoodUnits = 1;
        calculation.indirectRatePerHour = BigDecimal.ZERO;
        calculation.indirectCost = BigDecimal.ZERO;
        calculation.totalCost = new BigDecimal("80.00");
        calculation.unitCost = new BigDecimal("80.00000000");
        return calculations.save(calculation).id;
    }

    private void insert(long id, String target, String suggested, String chosen, String actual) {
        jdbc.update("""
                INSERT INTO production_price_estimates (calculation_id, target_margin_pct, suggested_unit_price,
                    chosen_unit_price, gross_profit_per_unit, actual_margin_pct, below_cost)
                VALUES (?, ?, ?, ?, -80, ?, TRUE)
                """, id, new BigDecimal(target), new BigDecimal(suggested), new BigDecimal(chosen),
                actual == null ? null : new BigDecimal(actual));
    }
}
