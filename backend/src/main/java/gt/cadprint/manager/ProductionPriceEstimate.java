package gt.cadprint.manager;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "production_price_estimates")
public class ProductionPriceEstimate {
    @Id
    @Column(name = "calculation_id")
    Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calculation_id", nullable = false)
    ProductionCalculation calculation;

    @NotNull
    @PositiveOrZero
    @DecimalMax(value = "100", inclusive = false)
    @Digits(integer = 3, fraction = 4)
    @Column(nullable = false, precision = 7, scale = 4)
    BigDecimal targetMarginPct;

    @NotNull
    @PositiveOrZero
    @Digits(integer = 25, fraction = 2)
    @Column(nullable = false, precision = 27, scale = 2)
    BigDecimal suggestedUnitPrice;

    @NotNull
    @PositiveOrZero
    @Digits(integer = 25, fraction = 2)
    @Column(nullable = false, precision = 27, scale = 2)
    BigDecimal chosenUnitPrice;

    @NotNull
    @Digits(integer = 25, fraction = 2)
    @Column(nullable = false, precision = 27, scale = 2)
    BigDecimal grossProfitPerUnit;

    @Digits(integer = 23, fraction = 4)
    @Column(precision = 27, scale = 4)
    BigDecimal actualMarginPct;

    @Column(nullable = false)
    boolean belowCost;
}
