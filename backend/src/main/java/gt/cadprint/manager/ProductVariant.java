package gt.cadprint.manager;

import java.math.BigDecimal;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "product_variants")
public class ProductVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    Product product;

    @Column(nullable = false)
    String name;

    String size;
    String color;
    String presentation;

    @Column(precision = 16, scale = 2)
    BigDecimal ownPrice;

    @Column(nullable = false)
    boolean active = true;

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
        sku.rename(value);
    }

    protected ProductVariant() {
    }

    ProductVariant(Product product, String sku) {
        this.product = product;
        this.sku = new CatalogSku(sku, CatalogSku.Kind.VARIANT);
    }

    @PrePersist
    @PreUpdate
    private void validatePrice() {
        CatalogPrice.validateOptional(ownPrice);
    }
}
