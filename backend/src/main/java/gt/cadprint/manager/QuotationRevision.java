package gt.cadprint.manager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "quotation_revisions")
public class QuotationRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quotation_id", nullable = false)
    Quotation quotation;

    @Column(nullable = false)
    int revisionNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_revision_id")
    QuotationRevision sourceRevision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    QuotationStatus status = QuotationStatus.BORRADOR;

    @Column(nullable = false, length = 3)
    String currency = "GTQ";

    @Column(length = 255)
    String recipientName;

    @Column(length = 255)
    String recipientContact;

    @Column(length = 254)
    String recipientEmail;

    @Column(length = 64)
    String recipientPhone;

    @Column(length = 500)
    String recipientAddress;

    @Column(length = 255)
    String issuerName;

    @Column(length = 254)
    String issuerEmail;

    @Column(length = 64)
    String issuerPhone;

    @Column(length = 500)
    String issuerAddress;

    @Column
    Instant issuedAt;

    @Column
    LocalDate validUntil;

    @Column(length = 4000)
    String commercialTerms;

    @Column(length = 4000)
    String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    QuotationDiscountType generalDiscountType = QuotationDiscountType.NONE;

    @Column(precision = 27, scale = 2)
    BigDecimal generalDiscountValue;

    @Column(precision = 7, scale = 4)
    BigDecimal generalDiscountPercent;

    @Column(nullable = false, precision = 27, scale = 2)
    BigDecimal generalDiscountAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 27, scale = 2)
    BigDecimal shippingAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 27, scale = 2)
    BigDecimal grossAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 27, scale = 2)
    BigDecimal lineDiscountTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 27, scale = 2)
    BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 27, scale = 2)
    BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 32)
    String calculationPolicyVersion;

    @Column(nullable = false)
    Instant createdAt = Instant.now();

    @Column(nullable = false)
    Instant updatedAt = createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_revision_id")
    QuotationRevision replacedByRevision;

    @Column
    Instant replacedAt;
}
