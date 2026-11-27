package gt.cadprint.manager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Entity
@Table(name = "production_cost_profiles")
public class ProductionCostProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, unique = true, length = 64)
    @NotBlank
    String code;

    @Column(nullable = false)
    @NotBlank
    String name;

    @Column(nullable = false, length = 3, updatable = false)
    String currency = "GTQ";

    @NotNull
    @PositiveOrZero
    @Digits(integer = 19, fraction = 8)
    @Column(nullable = false, precision = 27, scale = 8)
    BigDecimal electricityRatePerKwh;

    @NotNull
    @PositiveOrZero
    @Digits(integer = 19, fraction = 8)
    @Column(nullable = false, precision = 27, scale = 8)
    BigDecimal laborRatePerHour;

    @NotNull
    @PositiveOrZero
    @Digits(integer = 19, fraction = 8)
    @Column(nullable = false, precision = 27, scale = 8)
    BigDecimal indirectRatePerHour;

    @Column(nullable = false)
    boolean active = true;

    @Column(nullable = false, updatable = false)
    Instant createdAt;

    @Column(nullable = false)
    Instant updatedAt;

    @NotBlank
    @Column(nullable = false)
    String updatedBy;

    @Version
    @Column(nullable = false)
    Long version;

    @PrePersist
    private void createTimestamps() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        updatedAt = createdAt;
    }

    @PreUpdate
    private void updateTimestamp() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
