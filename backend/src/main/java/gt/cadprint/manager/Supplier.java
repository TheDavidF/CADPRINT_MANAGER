package gt.cadprint.manager;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Supplier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String name;
    public String phone;
    public String email;
    public String notes;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
