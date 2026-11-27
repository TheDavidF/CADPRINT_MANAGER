package gt.cadprint.manager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_direct_costs")
class OrderDirectCost {
    enum Category { MANO_DE_OBRA, SUBCONTRATACION, ENVIO, OTRO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false)
    Long customerOrderId;

    @Column(nullable = false, length = 36)
    String requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    Category category;

    @Column(nullable = false, length = 255)
    String description;

    @Column(nullable = false, precision = 27, scale = 2)
    BigDecimal amount;

    @Column(nullable = false)
    LocalDate incurredOn;

    @Column(nullable = false)
    Instant recordedAt;

    @Column(nullable = false, length = 255)
    String recordedBy;

    protected OrderDirectCost() {}

    OrderDirectCost(Long orderId, String requestId, Category category, String description,
                    BigDecimal amount, LocalDate incurredOn, String recordedBy) {
        this.customerOrderId = orderId;
        this.requestId = requestId;
        this.category = category;
        this.description = description;
        this.amount = amount;
        this.incurredOn = incurredOn;
        this.recordedBy = recordedBy;
        this.recordedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
