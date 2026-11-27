package gt.cadprint.manager;

import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "catalog_skus")
public class CatalogSku {
    enum Kind {
        PRODUCT,
        VARIANT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "sku", nullable = false, unique = true)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 7, updatable = false)
    private Kind kind;

    protected CatalogSku() {
    }

    CatalogSku(String sku, Kind kind) {
        this.value = normalize(sku);
        this.kind = kind;
    }

    static String normalize(String sku) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("El SKU es obligatorio");
        }
        String value = sku.strip().toUpperCase(Locale.ROOT);
        if (value.length() > 255) {
            throw new IllegalArgumentException("El SKU excede 255 caracteres");
        }
        return value;
    }

    void rename(String sku) {
        value = normalize(sku);
    }

    String getValue() {
        return value;
    }
}
