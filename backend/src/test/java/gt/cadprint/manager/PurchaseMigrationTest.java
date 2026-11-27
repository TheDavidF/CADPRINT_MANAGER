package gt.cadprint.manager;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class PurchaseMigrationTest {
    @TempDir
    Path directory;

    @Test
    void startsFromVersionTwoAndPreservesExistingInventory() {
        String url = "jdbc:h2:file:" + directory.resolve("inventory-v2").toAbsolutePath();
        Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration/h2", "classpath:db/migration/common")
                .target("2").load().migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        assertThat(version(jdbc)).isEqualTo("2");
        jdbc.update("INSERT INTO supplier (id, name) VALUES (71, 'Proveedor anterior')");
        jdbc.update("""
                INSERT INTO materials (id, sku, name, category_id, unit, stock, min_stock, unit_cost, active, version)
                SELECT 91, 'LEGACY', 'PLA anterior', id, 'g', 935, 5, 0.15, TRUE, 2
                FROM categories WHERE name_key = 'filamento'
                """);
        jdbc.update("""
                INSERT INTO movements (id, material_id, type, quantity, before_stock, after_stock,
                    reason, username, created_at)
                VALUES (41, 91, 'ENTRADA', 1000, 0, 1000, 'Entrada anterior', 'admin', CURRENT_TIMESTAMP),
                       (42, 91, 'SALIDA', 65, 1000, 935, 'Salida anterior', 'operador', CURRENT_TIMESTAMP)
                """);

        try (var context = new SpringApplicationBuilder(CadprintApplication.class).run(
                "--server.port=0",
                "--spring.datasource.url=" + url,
                "--CADPRINT_ADMIN_PASSWORD=test-admin-password",
                "--CADPRINT_OPERATOR_PASSWORD=test-operator-password")) {
            assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            assertThat(version(jdbc)).isEqualTo("15");
            assertThat(context.getBean(SupplierRepo.class).findById(71L).orElseThrow().name)
                    .isEqualTo("Proveedor anterior");
            assertThat(context.getBean(MaterialRepo.class).findById(91L).orElseThrow().stock)
                    .isEqualByComparingTo("935");
            var inventory = context.getBean(InventoryService.class);
            assertThat(inventory.movements()).extracting(ApiDtos.MovementView::id).containsExactly(42L, 41L);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movements WHERE purchase_line_id IS NULL", Long.class))
                    .isEqualTo(2);
            inventory.move(new ApiDtos.MovementRequest(91L, "ENTRADA", java.math.BigDecimal.ONE, "Tras V3"), "admin");
            assertThat(context.getBean(MaterialRepo.class).findById(91L).orElseThrow().stock)
                    .isEqualByComparingTo("936");
        }
    }

    private String version(JdbcTemplate jdbc) {
        return jdbc.queryForObject("""
                SELECT "version" FROM "flyway_schema_history"
                WHERE "success" ORDER BY "installed_rank" DESC FETCH FIRST 1 ROW ONLY
                """, String.class);
    }
}
