package gt.cadprint.manager;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "quotations")
public class Quotation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(length = 64)
    String quotationNumber;

    @Column(nullable = false, length = 255)
    String createdBy;

    @Column(nullable = false)
    Instant createdAt = Instant.now();

    @Column(nullable = false)
    Instant updatedAt = createdAt;

    @Version
    @Column(nullable = false)
    long version;
}
