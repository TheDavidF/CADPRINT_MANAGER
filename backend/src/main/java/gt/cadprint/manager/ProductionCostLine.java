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
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "production_cost_lines")
public class ProductionCostLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calculation_id", nullable = false)
    ProductionCalculation calculation;

    @Positive
    @Column(nullable = false)
    int position;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    ProductionCostType costType;

    @NotBlank
    @Column(nullable = false, length = 1000)
    String description;

    @NotNull
    @Positive
    @Digits(integer = 13, fraction = 6)
    @Column(nullable = false, precision = 19, scale = 6)
    BigDecimal quantity;

    @NotBlank
    @Column(nullable = false, length = 64)
    String unit;

    @NotNull
    @PositiveOrZero
    @Digits(integer = 19, fraction = 8)
    @Column(nullable = false, precision = 27, scale = 8)
    BigDecimal appliedRate;

    @NotNull
    @PositiveOrZero
    @Digits(integer = 17, fraction = 2)
    @Column(nullable = false, precision = 19, scale = 2)
    BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id")
    Material material;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    ProductionCostSource costSource;

    String materialSku;
    String materialName;
    String materialUnit;

    @Positive
    @Digits(integer = 11, fraction = 9)
    @Column(precision = 20, scale = 9)
    BigDecimal conversionFactor;
}
