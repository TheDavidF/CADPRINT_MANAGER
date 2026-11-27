package gt.cadprint.manager;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "production_consumptions")
class ProductionConsumption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false)
    Long customerOrderId;

    @OneToOne(optional = false)
    @JoinColumn(name = "movement_id", nullable = false, unique = true)
    Movement movement;

    @Column(nullable = false, length = 36)
    String requestId;

    protected ProductionConsumption() {}

    ProductionConsumption(Long customerOrderId, Movement movement, String requestId) {
        this.customerOrderId = customerOrderId;
        this.movement = movement;
        this.requestId = requestId;
    }
}
