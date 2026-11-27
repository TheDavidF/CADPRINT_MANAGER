package gt.cadprint.manager;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "materials")
public class Material {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String name;

    @Column(unique = true)
    public String sku;

    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    public Category category;

    public String unit;

    @Column(precision = 16, scale = 3)
    public BigDecimal stock = BigDecimal.ZERO;

    @Column(precision = 16, scale = 3)
    public BigDecimal minStock = BigDecimal.ZERO;

    @Column(precision = 16, scale = 4)
    public BigDecimal unitCost = BigDecimal.ZERO;

    public boolean active = true;

    @Version
    public Long version;

    public Long getId() {
        return id;
    }

    public String getUnit() {
        return unit;
    }

    public boolean isActive() {
        return active;
    }
}
