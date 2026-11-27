package gt.cadprint.manager;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "material_changes")
public class MaterialChange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    Material material;

    @Column(nullable = false)
    String username;

    @Column(nullable = false)
    Instant changedAt;

    @Column(nullable = false)
    String fieldName;

    String previousValue;
    String newValue;

    protected MaterialChange() {
    }

    MaterialChange(Material material, String username, Instant changedAt, String fieldName,
                   String previousValue, String newValue) {
        this.material = material;
        this.username = username;
        this.changedAt = changedAt;
        this.fieldName = fieldName;
        this.previousValue = previousValue;
        this.newValue = newValue;
    }
}
