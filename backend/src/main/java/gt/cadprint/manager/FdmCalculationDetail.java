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
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "production_fdm_details")
public class FdmCalculationDetail {
    @Id
    @Column(name = "calculation_id")
    Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calculation_id", nullable = false)
    ProductionCalculation calculation;

    @Positive
    @Column(nullable = false)
    long printSeconds;

    @PositiveOrZero
    @Column(nullable = false)
    long laborSeconds;

    @NotNull
    @Positive
    @Digits(integer = 13, fraction = 6)
    @Column(nullable = false, precision = 19, scale = 6)
    BigDecimal partMassGrams;

    @PositiveOrZero
    @Digits(integer = 13, fraction = 6)
    @Column(precision = 19, scale = 6)
    BigDecimal supportMassGrams;
}
