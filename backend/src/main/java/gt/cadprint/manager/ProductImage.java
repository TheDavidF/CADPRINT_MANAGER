package gt.cadprint.manager;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_images")
public class ProductImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, unique = true, updatable = false)
    Product product;

    @Column(nullable = false, unique = true, length = 36)
    String storageKey;

    @Column(nullable = false, length = 20)
    String contentType;

    @Column(nullable = false)
    long fileSize;

    @Column(nullable = false)
    Instant createdAt;
}
