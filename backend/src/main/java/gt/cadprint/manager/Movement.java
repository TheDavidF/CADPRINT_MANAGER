package gt.cadprint.manager;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "movements")
public class Movement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    public Material material;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_line_id", unique = true)
    public PurchaseLine purchaseLine;

    @Column(nullable = false)
    public String type;

    @Column(nullable = false, precision = 16, scale = 3)
    public BigDecimal quantity;

    @Column(nullable = false, precision = 16, scale = 3)
    public BigDecimal beforeStock;

    @Column(nullable = false, precision = 16, scale = 3)
    public BigDecimal afterStock;

    @Column(nullable = false)
    public String reason;

    @Column(nullable = false)
    public String username;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();
}
