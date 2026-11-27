package gt.cadprint.manager;

import java.math.BigDecimal;

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

@Entity
@Table(name = "quotation_lines")
public class QuotationLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revision_id", nullable = false)
    QuotationRevision revision;

    @Column(nullable = false)
    int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    QuotationLineType lineType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_calculation_id")
    ProductionCalculation productionCalculation;

    @Column(length = 255)
    String skuSnapshot;

    @Column(nullable = false, length = 255)
    String nameSnapshot;

    @Column(length = 4000)
    String descriptionSnapshot;

    @Column(nullable = false)
    int quantity;

    @Column(nullable = false, precision = 27, scale = 2)
    BigDecimal unitPrice = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    QuotationDiscountType discountType = QuotationDiscountType.NONE;

    @Column(precision = 27, scale = 2)
    BigDecimal discountValue;

    @Column(precision = 7, scale = 4)
    BigDecimal discountPercent;

    @Column(nullable = false, precision = 27, scale = 2)
    BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 27, scale = 2)
    BigDecimal grossAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 27, scale = 2)
    BigDecimal netAmount = BigDecimal.ZERO;
}
