package gt.cadprint.manager;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class QuotationMigrationTest {
    @Test
    void upgradesAnIsolatedV7CopyAndPreservesExistingRows(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("reference-v7");
        String sourceUrl = "jdbc:h2:file:" + source;
        Flyway.configure().dataSource(sourceUrl, "sa", "")
                .locations("classpath:db/migration/h2", "classpath:db/migration/common")
                .target("7").load().migrate();
        var prior = new JdbcTemplate(new DriverManagerDataSource(sourceUrl, "sa", ""));
        prior.update("INSERT INTO supplier (id, name) VALUES (31, 'Proveedor existente')");
        prior.update("""
                INSERT INTO materials (id, name, sku, category_id, unit, stock, active)
                VALUES (41, 'Material existente', 'EXISTENTE', (SELECT MIN(id) FROM categories), 'g', 10, TRUE)
                """);
        prior.update("""
                INSERT INTO production_calculations (id, technique, formula_version, username, created_at,
                    job_description, expected_good_units, currency, productive_seconds, indirect_rate_per_hour,
                    indirect_cost, total_cost, unit_cost)
                VALUES (101, 'FDM', 'v1', 'operador', CURRENT_TIMESTAMP, 'Referencia', 1, 'GTQ', 3600, 10, 10, 80, 80)
                """);
        prior.update("""
                INSERT INTO production_price_estimates (calculation_id, target_margin_pct, suggested_unit_price,
                    chosen_unit_price, gross_profit_per_unit, actual_margin_pct, below_cost)
                VALUES (101, 0, 80, 0, -80, NULL, TRUE)
                """);
        prior.update("""
                INSERT INTO product_categories (id, name, normalized_name, active, version)
                VALUES (51, 'Textiles', 'textiles', TRUE, 0)
                """);
        prior.update("""
                INSERT INTO catalog_skus (id, sku, kind) VALUES (61, 'TEXTIL', 'PRODUCT'), (62, 'TEXTIL-AZUL', 'VARIANT')
                """);
        prior.update("""
                INSERT INTO products (id, sku_id, name, category_id, production_mode, pricing_mode,
                    base_price, active, created_at, updated_at, version)
                VALUES (71, 61, 'Textil', 51, 'BAJO_PEDIDO', 'PRECIO_BASE', 32.50, TRUE,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """);
        prior.update("""
                INSERT INTO product_variants (id, product_id, sku_id, name, active, version)
                VALUES (81, 71, 62, 'Azul', TRUE, 0)
                """);
        var storedRows = snapshot(prior);
        Path copy = directory.resolve("upgrade-v8");
        Files.copy(Path.of(source + ".mv.db"), Path.of(copy + ".mv.db"));
        String copyUrl = "jdbc:h2:file:" + copy + ";IFEXISTS=TRUE";

        try (var context = new SpringApplicationBuilder(CadprintApplication.class).run(
                "--server.port=0", "--spring.datasource.url=" + copyUrl,
                "--CADPRINT_ADMIN_PASSWORD=test-admin-password",
                "--CADPRINT_OPERATOR_PASSWORD=test-operator-password")) {
            assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            assertThat(context.getBean(Flyway.class).info().current().getVersion().getVersion()).isEqualTo("17");
            var upgraded = context.getBean(JdbcTemplate.class);
            assertThat(snapshot(upgraded)).isEqualTo(storedRows);
            for (String table : List.of("quotations", "quotation_revisions", "quotation_lines", "quotation_events")) {
                assertThat(upgraded.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)).isZero();
            }
        }

        assertThat(prior.queryForObject("""
                SELECT COUNT(*) FROM "flyway_schema_history" WHERE "version" = '8'
                """, Long.class)).isZero();
        assertThat(snapshot(prior)).isEqualTo(storedRows);
    }

    private Map<String, List<Map<String, Object>>> snapshot(JdbcTemplate jdbc) {
        Map<String, List<Map<String, Object>>> rows = new TreeMap<>();
        for (String table : List.of("categories", "supplier", "materials", "production_calculations",
                "production_price_estimates", "product_categories", "catalog_skus", "products", "product_variants")) {
            rows.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1"));
        }
        return rows;
    }
}

