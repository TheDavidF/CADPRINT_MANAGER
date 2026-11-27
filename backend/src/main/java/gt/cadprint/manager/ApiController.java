package gt.cadprint.manager;

import java.util.List;

import gt.cadprint.manager.ApiDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final InventoryService inventory;
    private final DirectoryService directory;

    private final CategoryService categories;

    ApiController(InventoryService inventory, DirectoryService directory, CategoryService categories) {
        this.inventory = inventory;
        this.directory = directory;
        this.categories = categories;
    }

    @GetMapping("/me")
    CurrentUser currentUser(Authentication authentication) {
        return new CurrentUser(authentication.getName(),
                authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }

    @GetMapping("/materials")
    List<MaterialView> materials() {
        return inventory.materials();
    }

    @PostMapping("/materials")
    @ResponseStatus(HttpStatus.CREATED)
    MaterialView createMaterial(@Valid @RequestBody MaterialCreate input) {
        return inventory.create(input);
    }

    @PutMapping("/materials/{id}")
    MaterialView updateMaterial(@PathVariable Long id, @Valid @RequestBody MaterialUpdate input,
                                Authentication authentication) {
        return inventory.update(id, input, authentication.getName());
    }

    @GetMapping("/materials/{id}/changes")
    List<MaterialChangeView> materialChanges(@PathVariable Long id) {
        return inventory.changes(id);
    }

    @GetMapping("/categories")
    List<CategoryView> categories() {
        return categories.list();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    CategoryView createCategory(@Valid @RequestBody CategoryRequest input) {
        return categories.create(input.name());
    }

    @PutMapping("/categories/{id}")
    CategoryView renameCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest input) {
        return categories.rename(id, input.name());
    }

    @GetMapping("/movements")
    List<MovementView> movements() {
        return inventory.movements();
    }

    @PostMapping("/movements")
    @ResponseStatus(HttpStatus.CREATED)
    MovementView move(@Valid @RequestBody MovementRequest input, Authentication authentication) {
        return inventory.move(input, authentication.getName());
    }

    @GetMapping("/suppliers")
    List<SupplierView> suppliers() {
        return directory.suppliers();
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    SupplierView createSupplier(@Valid @RequestBody SupplierRequest input) {
        return directory.createSupplier(input);
    }

    @GetMapping("/dashboard")
    Dashboard dashboard() {
        return directory.dashboard();
    }
}