package gt.cadprint.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog-test-${random.uuid}",
        "CADPRINT_ADMIN_PASSWORD=test-admin-password",
        "CADPRINT_OPERATOR_PASSWORD=test-operator-password"
})
class ProductCatalogPersistenceTest {
    @Autowired ProductCategoryRepo categories;
    @Autowired ProductRepo products;
    @Autowired ProductVariantRepo variants;
    @Autowired JdbcTemplate jdbc;

    @Test
    void persistsRelationshipsAndCategoryRenames() {
        ProductCategory category = category("  Textiles  ");
        assertThat(category.name).isEqualTo("Textiles");
        assertThat(categories.findByNormalizedName("textiles").orElseThrow().id).isEqualTo(category.id);
        assertThatThrownBy(() -> category(" TEXTILES ")).isInstanceOf(DataIntegrityViolationException.class);

        Product base = product("  cad-tx-001  ", category, PricingMode.PRECIO_BASE, "125.25");
        ProductVariant variant = variant(base, " cad-tx-001-az ", "130.00");
        ProductVariant inherited = variant(base, "cad-tx-001-default", null);
        assertThat(products.findBySkuValue("CAD-TX-001").orElseThrow().basePrice).isEqualByComparingTo("125.25");
        assertThat(variants.findBySkuValue("CAD-TX-001-AZ").orElseThrow().ownPrice).isEqualByComparingTo("130.00");
        assertThat(variants.findById(inherited.id).orElseThrow().ownPrice).isNull();
        assertThat(variants.findByProductIdAndActiveOrderByIdAsc(base.id, true))
                .extracting(item -> item.id).containsExactly(variant.id, inherited.id);

        category.name = "Ropa personalizada";
        category = categories.saveAndFlush(category);
        assertThat(categories.findByNormalizedName("ropa personalizada").orElseThrow().id).isEqualTo(category.id);
        assertThat(products.findByCategoryIdAndActiveOrderByIdAsc(category.id, true))
                .extracting(item -> item.id).containsExactly(base.id);
        long categoryId = category.id;
        assertThatThrownBy(() -> jdbc.update("DELETE FROM product_categories WHERE id = ?", categoryId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM products WHERE id = ?", base.id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void keepsGlobalSkuReservationsForInactiveProductsAndVariants() {
        ProductCategory category = category("Impresión 3D");
        Product base = product(" cad-global ", category, PricingMode.PRECIO_BASE, "1.00");
        ProductVariant variant = variant(base, "cad-variant", null);
        base.active = false;
        products.saveAndFlush(base);
        variant.active = false;
        variants.saveAndFlush(variant);

        assertThatThrownBy(() -> variant(base, " CAD-GLOBAL ", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> product("CAD-variant", category, PricingMode.PRECIO_BASE, "1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> product("CAD-GLOBAL", category, PricingMode.PRECIO_BASE, "1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> new Product("  ")).isInstanceOf(IllegalArgumentException.class);

        Long baseSku = jdbc.queryForObject("SELECT sku_id FROM products WHERE id = ?", Long.class, base.id);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO product_variants (product_id, sku_id, name, active, version) VALUES (?, ?, 'Cruce', TRUE, 0)
                """, base.id, baseSku)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO catalog_skus (sku, kind) VALUES (' lowercase ', 'PRODUCT')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(products.findBySkuValue("CAD-GLOBAL").orElseThrow().active).isFalse();
        assertThat(variants.findBySkuValue("CAD-VARIANT").orElseThrow().active).isFalse();
    }

    @Test
    void storesQuotationProductsWithAndWithoutReferencePrice() {
        ProductCategory category = category("Artículos promocionales");
        Product quoted = product("COTIZAR", category, PricingMode.REQUIERE_COTIZACION, null);
        Product estimated = product("ESTIMADO", category, PricingMode.REQUIERE_COTIZACION, "27.45");
        assertThat(products.findById(quoted.id).orElseThrow().basePrice).isNull();
        assertThat(products.findById(estimated.id).orElseThrow().basePrice).isEqualByComparingTo("27.45");
        assertThatThrownBy(() -> product("SIN-PRECIO", category, PricingMode.PRECIO_BASE, null))
                .satisfies(error -> assertThat(NestedExceptionUtils.getMostSpecificCause(error))
                        .isInstanceOf(IllegalArgumentException.class));
        assertThatThrownBy(() -> jdbc.update("UPDATE products SET pricing_mode = 'PRECIO_BASE' WHERE id = ?", quoted.id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE products SET base_price = -1 WHERE id = ?", estimated.id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.01", "3.141", "100000000000000.00"})
    void rejectsInvalidPricesWhenPersistingProductsAndVariants(String value) {
        String key = value.replace('.', '_');
        ProductCategory category = category("Precio " + key);
        Product valid = product("PRECIO-" + key, category, PricingMode.PRECIO_BASE, "1");
        assertThatThrownBy(() -> product("INVALIDO-" + key, category, PricingMode.REQUIERE_COTIZACION, value))
                .satisfies(error -> assertThat(NestedExceptionUtils.getMostSpecificCause(error))
                        .isInstanceOf(IllegalArgumentException.class));
        assertThatThrownBy(() -> variant(valid, "VARIANTE-" + key, value))
                .satisfies(error -> assertThat(NestedExceptionUtils.getMostSpecificCause(error))
                        .isInstanceOf(IllegalArgumentException.class));
    }

    @Test
    void rejectsStaleProductUpdatesAndPreservesCreationDate() {
        Product product = product("VERSION-SKU", category("Sublimación"), PricingMode.PRECIO_BASE, "25");
        Product first = products.findById(product.id).orElseThrow();
        Product stale = products.findById(product.id).orElseThrow();
        first.name = "Actualización ganadora";
        var winner = products.saveAndFlush(first);
        assertThat(winner.version).isGreaterThan(stale.version);
        assertThat(winner.createdAt).isEqualTo(stale.createdAt);
        assertThat(winner.updatedAt).isAfterOrEqualTo(winner.createdAt);

        stale.name = "Actualización obsoleta";
        assertThatThrownBy(() -> products.saveAndFlush(stale)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(products.findById(product.id).orElseThrow().name).isEqualTo(winner.name);
    }

    private ProductCategory category(String name) {
        ProductCategory category = new ProductCategory();
        category.name = name;
        return categories.saveAndFlush(category);
    }

    private Product product(String sku, ProductCategory category, PricingMode mode, String value) {
        Product product = new Product(sku);
        product.category = category;
        product.name = sku.strip();
        product.productionMode = ProductionMode.BAJO_PEDIDO;
        product.pricingMode = mode;
        product.basePrice = value == null ? null : new BigDecimal(value);
        return products.saveAndFlush(product);
    }

    private ProductVariant variant(Product product, String sku, String value) {
        ProductVariant variant = new ProductVariant(product, sku);
        variant.name = "Variante";
        variant.color = "Azul";
        variant.ownPrice = value == null ? null : new BigDecimal(value);
        return variants.saveAndFlush(variant);
    }
}
