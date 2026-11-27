package gt.cadprint.manager;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserAccountRepository extends JpaRepository<UserAccount, String> {
    List<UserAccount> findAllByOrderByUsernameAsc();
}
