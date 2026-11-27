package gt.cadprint.manager;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ProductCatalogMigrationTest {
    private static final List<String> LEGACY_TABLES = List.of("categories", "materials", "supplier", "movements",
            "material_changes", "purchases", "purchase_lines", "purchase_receipts", "purchase_events");

    @TempDir
    Path temp;

    @Test
    void upgradesFileFromV3WithoutChangingLegacyRowsAndReopensCatalog() {
        String url = "jdbc:h2:file:" + temp.resolve("cadprint-v3").toAbsolutePath();
        Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration/h2", "classpath:db/migration/common")
                .target("3").load().migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
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
        var legacyRows = snapshot(jdbc);
        long productId;

        try (var context = start(url)) {
            assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            assertThat(jdbc.queryForList("""
                    SELECT "version" FROM "flyway_schema_history"
                    WHERE "success" AND "version" IS NOT NULL ORDER BY "installed_rank"
                    """, String.class)).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17");
            assertThat(snapshot(jdbc)).isEqualTo(legacyRows);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products", Long.class)).isZero();
            ProductCategory category = new ProductCategory();
            category.name = "Catálogo temporal";
            category = context.getBean(ProductCategoryRepo.class).saveAndFlush(category);
            Product product = new Product("CAD-RESTART");
            product.name = "Producto persistente";
            product.category = category;
            product.productionMode = ProductionMode.PARA_EXISTENCIAS;
            product.pricingMode = PricingMode.REQUIERE_COTIZACION;
            productId = context.getBean(ProductRepo.class).saveAndFlush(product).id;
            ProductVariant variant = new ProductVariant(product, "CAD-RESTART-A");
            variant.name = "Presentación A";
            context.getBean(ProductVariantRepo.class).saveAndFlush(variant);
        }

        try (var context = start(url)) {
            assertThat(context.getBean(ProductRepo.class).findBySkuValue("CAD-RESTART").orElseThrow().id)
                    .isEqualTo(productId);
            assertThat(context.getBean(ProductVariantRepo.class).findByProductIdOrderByIdAsc(productId))
                    .extracting(ProductVariant::getSku).containsExactly("CAD-RESTART-A");
            assertThat(snapshot(jdbc)).isEqualTo(legacyRows);
        }
    }

    private ConfigurableApplicationContext start(String url) {
        return new SpringApplicationBuilder(CadprintApplication.class).run(
                "--server.port=0", "--spring.datasource.url=" + url,
                "--CADPRINT_ADMIN_PASSWORD=test-admin-password",
                "--CADPRINT_OPERATOR_PASSWORD=test-operator-password");
    }

    private Map<String, List<Map<String, Object>>> snapshot(JdbcTemplate jdbc) {
        Map<String, List<Map<String, Object>>> snapshot = new TreeMap<>();
        for (String table : LEGACY_TABLES) {
            snapshot.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
        }
        return snapshot;
    }
}
