package gt.cadprint.manager;

import java.math.BigDecimal;
import java.time.Instant;
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
@Table(name = "order_payments")
class OrderPayment {
    enum Method { EFECTIVO, TRANSFERENCIA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false)
    Long customerOrderId;

    @Column(nullable = false, length = 36)
    String requestId;

    @Column(nullable = false, precision = 27, scale = 2)
    BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    Method method;

    @Column(nullable = false)
    Instant receivedAt;

    @Column(nullable = false, length = 255)
    String receivedBy;

    protected OrderPayment() {}

    OrderPayment(Long orderId, String requestId, BigDecimal amount, Method method, String user) {
        this.customerOrderId = orderId;
        this.requestId = requestId;
        this.amount = amount;
        this.method = method;
        this.receivedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.receivedBy = user;
    }
}
