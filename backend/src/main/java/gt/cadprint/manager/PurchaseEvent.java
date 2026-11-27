package gt.cadprint.manager;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
@Table(name = "purchase_events")
public class PurchaseEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false)
    Purchase purchase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    PurchaseEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    PurchaseStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    PurchaseStatus newStatus;

    @Column(nullable = false)
    String username;

    @Column(nullable = false)
    Instant createdAt = Instant.now();

    @Column(length = 1000)
    String reason;
}
