package gt.cadprint.manager;

import java.net.URI;
import java.util.List;

import gt.cadprint.manager.ProductDtos.CategoryInput;
import gt.cadprint.manager.ProductDtos.CategoryView;
import gt.cadprint.manager.ProductDtos.StatusInput;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/product-categories")
class ProductCategoryController {
    private final ProductService catalog;

    ProductCategoryController(ProductService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    List<CategoryView> list() {
        return catalog.categories();
    }

    @PostMapping
    ResponseEntity<CategoryView> create(@Valid @RequestBody CategoryInput input) {
        CategoryView category = catalog.createCategory(input);
        return ResponseEntity.created(URI.create("/api/product-categories")).body(category);
    }

    @PutMapping("/{id}")
    CategoryView update(@PathVariable Long id, @Valid @RequestBody CategoryInput input) {
        return catalog.updateCategory(id, input);
    }

    @PutMapping("/{id}/status")
    CategoryView status(@PathVariable Long id, @Valid @RequestBody StatusInput input) {
        return catalog.setCategoryStatus(id, input);
    }
}
