package gt.cadprint.manager;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
@Table(name = "purchase_lines")
public class PurchaseLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false)
    Purchase purchase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    Material material;

    @Column(nullable = false, precision = 16, scale = 3)
    BigDecimal quantity;

    @Column(nullable = false)
    String purchaseUnit;

    @Column(nullable = false, precision = 19, scale = 4)
    BigDecimal unitPrice;

    @Column(nullable = false, precision = 19, scale = 2)
    BigDecimal subtotal;

    String inventoryUnit;

    @Column(precision = 20, scale = 9)
    BigDecimal conversionFactor;

    @Column(precision = 16, scale = 3)
    BigDecimal inventoryQuantity;

    @Column(precision = 27, scale = 8)
    BigDecimal inventoryUnitCost;

    public Long getId() {
        return id;
    }
}
