package gt.cadprint.manager;

import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "product_categories")
public class ProductCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false)
    String name;

    @Column(nullable = false, unique = true)
    String normalizedName;

    @Column(nullable = false)
    boolean active = true;

    @Version
    @Column(nullable = false)
    Long version;

    public Long getId() {
        return id;
    }

    @PrePersist
    @PreUpdate
    private void normalizeName() {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre de la categoría comercial es obligatorio");
        }
        name = name.strip();
        normalizedName = name.toLowerCase(Locale.ROOT);
        if (name.length() > 255 || normalizedName.length() > 255) {
            throw new IllegalArgumentException("El nombre de la categoría comercial excede 255 caracteres");
        }
    }
}
