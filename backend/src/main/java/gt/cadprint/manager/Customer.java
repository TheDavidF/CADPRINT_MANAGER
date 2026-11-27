package gt.cadprint.manager;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, length = 255)
    String name;

    @Column(length = 255)
    String contact;

    @Column(length = 254)
    String email;

    @Column(length = 64)
    String phone;

    @Column(length = 500)
    String address;
}
