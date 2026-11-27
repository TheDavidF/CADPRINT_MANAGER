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

class ProductionCostMigrationTest {
    private static final List<String> LEGACY_TABLES = List.of("categories", "materials", "supplier", "movements",
            "material_changes", "purchases", "purchase_lines", "purchase_receipts", "purchase_events",
            "product_categories", "catalog_skus", "products", "product_variants", "product_images");

    @TempDir
    Path directory;

    @Test
    void upgradesCopyFromV5AndKeepsPreviousRowsUnchanged() throws Exception {
        Path source = directory.resolve("reference-v5");
        String url = "jdbc:h2:file:" + source;
        Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration/h2", "classpath:db/migration/common")
                .target("5").load().migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        jdbc.update("INSERT INTO supplier (id, name) VALUES (71, 'Proveedor V3')");
        jdbc.update("""
                INSERT INTO materials (id, name, sku, category_id, unit, stock, min_stock, unit_cost, active, version)
                SELECT 72, 'Material V3', 'M-V3', id, 'g', 10, 1, 0.25, TRUE, 0
                FROM categories WHERE name_key = 'filamento'
                """);
        jdbc.update("""
                INSERT INTO purchases (id, supplier_id, purchase_date, status, currency, subtotal, total,
                    created_by, created_at, version)
                VALUES (73, 71, CURRENT_DATE, 'RECIBIDA', 'GTQ', 10, 10, 'admin', CURRENT_TIMESTAMP, 0)
                """);
        jdbc.update("""
                INSERT INTO purchase_lines (id, purchase_id, material_id, quantity, purchase_unit, unit_price, subtotal,
                    inventory_unit, conversion_factor, inventory_quantity, inventory_unit_cost)
                VALUES (74, 73, 72, 10, 'g', 1, 10, 'g', 1, 10, 1)
                """);
        jdbc.update("""
                INSERT INTO purchase_receipts (id, purchase_id, received_at, received_by)
                VALUES (75, 73, CURRENT_TIMESTAMP, 'admin')
                """);
        jdbc.update("""
                INSERT INTO purchase_events (id, purchase_id, event_type, previous_status, new_status, username, created_at)
                VALUES (76, 73, 'RECEIVED', 'PENDIENTE', 'RECIBIDA', 'admin', CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO movements (id, material_id, type, quantity, before_stock, after_stock, reason, username,
                    created_at, purchase_line_id)
                VALUES (77, 72, 'ENTRADA', 10, 0, 10, 'Recepcion V3', 'admin', CURRENT_TIMESTAMP, 74)
                """);
        jdbc.update("""
                INSERT INTO material_changes (id, material_id, username, changed_at, field_name, previous_value, new_value)
                VALUES (78, 72, 'admin', CURRENT_TIMESTAMP, 'name', 'Nombre anterior', 'Material V3')
                """);
        jdbc.update("""
                INSERT INTO product_categories (id, name, normalized_name, active, version)
                VALUES (80, 'Cat?logo anterior', 'cat?logo anterior', TRUE, 0)
                """);
        jdbc.update("INSERT INTO catalog_skus (id, sku, kind) VALUES (81, 'PROD-V5', 'PRODUCT')");
        jdbc.update("""
                INSERT INTO products (id, sku_id, name, category_id, production_mode, pricing_mode,
                    base_price, active, created_at, updated_at, version)
                VALUES (82, 81, 'Producto anterior', 80, 'BAJO_PEDIDO', 'PRECIO_BASE', 50,
                    TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """);
        jdbc.update("""
                INSERT INTO product_images (id, product_id, storage_key, content_type, file_size, created_at)
                VALUES (83, 82, 'd20f474c-dc71-49c9-8ced-aaf39cf4ab45', 'image/png', 100, CURRENT_TIMESTAMP)
                """);
        var previous = snapshot(jdbc);
        Path copy = directory.resolve("upgrade-v6");
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
                    """, String.class)).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11");
            assertThat(snapshot(migrated)).isEqualTo(previous);
            assertThat(context.getBean(ProductionCostProfileRepo.class).count()).isZero();
            assertThat(context.getBean(ProductionMachineRepo.class).count()).isZero();
            assertThat(migrated.queryForObject("SELECT COUNT(*) FROM production_calculations", Long.class)).isZero();
        }
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM "flyway_schema_history" WHERE "version" = '6'
                """, Long.class)).isZero();
        assertThat(snapshot(jdbc)).isEqualTo(previous);
    }

    private Map<String, List<Map<String, Object>>> snapshot(JdbcTemplate jdbc) {
        Map<String, List<Map<String, Object>>> rows = new TreeMap<>();
        for (String table : LEGACY_TABLES) {
            rows.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
        }
        return rows;
    }
}
