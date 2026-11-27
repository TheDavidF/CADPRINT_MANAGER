package gt.cadprint.manager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Version;

@Entity
@Table(name = "purchases")
public class Purchase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    Supplier supplier;

    @Column(nullable = false)
    LocalDate purchaseDate;

    String documentNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    PurchaseStatus status = PurchaseStatus.BORRADOR;

    @Column(nullable = false, length = 3)
    String currency = "GTQ";

    @Column(nullable = false, precision = 19, scale = 2)
    BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    BigDecimal total = BigDecimal.ZERO;

    @Column(length = 1000)
    String notes;

    @Column(nullable = false)
    String createdBy;

    @Column(nullable = false)
    Instant createdAt = Instant.now();

    @Version
    @Column(nullable = false)
    Long version;
}
