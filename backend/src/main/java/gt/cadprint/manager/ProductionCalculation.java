package gt.cadprint.manager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

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
@Table(name = "production_calculations")
public class ProductionCalculation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    ProductionTechnique technique;

    @NotBlank
    @Column(nullable = false, length = 64)
    String formulaVersion;

    @NotBlank
    @Column(nullable = false, length = 255)
    String username;

    @Column(nullable = false)
    Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @NotBlank
    @Column(nullable = false, length = 1000)
    String jobDescription;

    @Positive
    @Column(nullable = false)
    long expectedGoodUnits;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cost_profile_id")
    ProductionCostProfile costProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machine_id")
    ProductionMachine machine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_id")
    ProductionCalculation supersedes;

    @Column(length = 255)
    String commercialName;

    @Column(length = 255)
    String commercialSku;

    @Column(nullable = false, length = 3)
    String currency = "GTQ";

    @PositiveOrZero
    @Column(nullable = false)
    long productiveSeconds;

    @NotNull
    @PositiveOrZero
    @Digits(integer = 19, fraction = 8)
    @Column(nullable = false, precision = 27, scale = 8)
    BigDecimal indirectRatePerHour;

    @NotNull
    @PositiveOrZero
    @Digits(integer = 19, fraction = 8)
    @Column(nullable = false, precision = 27, scale = 8)
    BigDecimal unitCost;

    @NotNull
    @PositiveOrZero
    @Digits(integer = 17, fraction = 2)
    @Column(nullable = false, precision = 19, scale = 2)
    BigDecimal indirectCost;

    @NotNull
    @PositiveOrZero
    @Digits(integer = 17, fraction = 2)
    @Column(nullable = false, precision = 19, scale = 2)
    BigDecimal totalCost;
}
