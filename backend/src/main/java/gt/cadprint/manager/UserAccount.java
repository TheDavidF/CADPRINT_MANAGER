package gt.cadprint.manager;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_accounts")
class UserAccount {
    enum Role { ADMIN, OPERADOR }

    @Id
    @Column(length = 40)
    String username;

    @Column(nullable = false, length = 60)
    String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    Role role;

    @Column(nullable = false)
    boolean enabled;

    protected UserAccount() {}

    UserAccount(String username, String passwordHash, Role role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = true;
    }
}
