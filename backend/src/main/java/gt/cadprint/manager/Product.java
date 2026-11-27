package gt.cadprint.manager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false)
    String name;

    @Column(length = 2000)
    String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    ProductCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    ProductionMode productionMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    PricingMode pricingMode;

    @Column(precision = 16, scale = 2)
    BigDecimal basePrice;

    @Column(nullable = false)
    boolean active = true;

    @Column(nullable = false, updatable = false)
    Instant createdAt;

    @Column(nullable = false)
    Instant updatedAt;

    @Version
    @Column(nullable = false)
    Long version;

    @OneToOne(optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "sku_id", nullable = false, unique = true, updatable = false)
    private CatalogSku sku;

    String getSku() {
        return sku.getValue();
    }

    void renameSku(String value) {
        if (!getSku().equals(value)) {
            sku.rename(value);
            updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        }
    }

    protected Product() {
    }

    Product(String sku) {
        this.sku = new CatalogSku(sku, CatalogSku.Kind.PRODUCT);
    }

    @PrePersist
    private void beforeInsert() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        updatedAt = createdAt;
        validatePrice();
    }

    @PreUpdate
    private void beforeUpdate() {
        validatePrice();
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private void validatePrice() {
        if (pricingMode == PricingMode.PRECIO_BASE && basePrice == null) {
            throw new IllegalArgumentException("El precio base es obligatorio en modalidad PRECIO_BASE");
        }
        CatalogPrice.validateOptional(basePrice);
    }
}
