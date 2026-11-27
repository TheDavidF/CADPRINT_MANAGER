package gt.cadprint.manager;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/accounts")
class UserAccountController {
    private final UserAccountRepository accounts;
    private final EntityManager entities;
    private final PasswordEncoder encoder;

    UserAccountController(UserAccountRepository accounts, EntityManager entities, PasswordEncoder encoder) {
        this.accounts = accounts;
        this.entities = entities;
        this.encoder = encoder;
    }

    @GetMapping
    List<AccountView> list() {
        return accounts.findAllByOrderByUsernameAsc().stream().map(AccountView::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    AccountView create(@Valid @RequestBody CreateAccount input) {
        String name = input.username().toLowerCase(Locale.ROOT);
        if (name.equals("admin") || name.equals("operador") || accounts.existsById(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El nombre de usuario ya está en uso");
        }
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña debe ocupar como máximo 72 bytes");
        }
        var account = new UserAccount(name, encoder.encode(input.password()), input.role());
        try {
            entities.persist(account);
            accounts.flush();
        } catch (DataIntegrityViolationException | jakarta.persistence.PersistenceException failure) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se pudo crear la cuenta");
        }
        return AccountView.from(account);
    }

    @PutMapping("/{username}/status")
    @Transactional
    AccountView status(@PathVariable String username, @Valid @RequestBody AccountStatus input,
                       Principal principal) {
        if (!input.enabled() && principal.getName().equalsIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes desactivar tu propia cuenta");
        }
        var account = accounts.findById(username.toLowerCase(Locale.ROOT)).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));
        account.enabled = input.enabled();
        return AccountView.from(account);
    }

    record CreateAccount(@NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9._-]{2,39}") String username,
                         @NotBlank @Size(min = 12, max = 72) String password,
                         @NotNull UserAccount.Role role) {}

    record AccountStatus(@NotNull Boolean enabled) {}

    record AccountView(String username, UserAccount.Role role, boolean enabled) {
        static AccountView from(UserAccount account) {
            return new AccountView(account.username, account.role, account.enabled);
        }
    }
}
