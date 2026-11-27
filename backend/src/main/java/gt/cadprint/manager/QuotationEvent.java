package gt.cadprint.manager;

import java.time.Instant;

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

import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "quotation_events")
@Immutable
public class QuotationEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revision_id", nullable = false)
    QuotationRevision revision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    QuotationEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    QuotationStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    QuotationStatus newStatus;

    @Column(nullable = false, length = 255)
    String actor;

    @Column(nullable = false)
    Instant occurredAt = Instant.now();

    @Column(length = 1000)
    String reason;

    @Column(length = 4000)
    String details;
}
