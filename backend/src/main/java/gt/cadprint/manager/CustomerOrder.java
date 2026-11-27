package gt.cadprint.manager;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer_orders")
class CustomerOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false)
    Long quotationId;

    @Column(nullable = false)
    int revisionNumber;

    @Column(nullable = false)
    Instant createdAt;

    @Column(nullable = false, length = 255)
    String createdBy;

    protected CustomerOrder() {}

    CustomerOrder(Long quotationId, int revisionNumber, String createdBy) {
        this.quotationId = quotationId;
        this.revisionNumber = revisionNumber;
        this.createdAt = Instant.now();
        this.createdBy = createdBy;
    }
}
