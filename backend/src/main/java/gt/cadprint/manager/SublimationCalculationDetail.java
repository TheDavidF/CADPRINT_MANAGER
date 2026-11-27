package gt.cadprint.manager;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "production_sublimation_details")
public class SublimationCalculationDetail {
    @Id
    @Column(name = "calculation_id")
    Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calculation_id", nullable = false)
    ProductionCalculation calculation;

    @Positive
    @Column(nullable = false)
    long cycleCount;

    @Positive
    @Column(nullable = false)
    long pressSecondsPerCycle;

    @PositiveOrZero
    @Column(nullable = false)
    long preparationSeconds;

    @PositiveOrZero
    @Column(nullable = false)
    long laborSeconds;
}
