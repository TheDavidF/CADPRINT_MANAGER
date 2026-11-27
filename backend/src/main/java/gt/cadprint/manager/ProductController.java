package gt.cadprint.manager;

import java.net.URI;
import java.util.List;

import gt.cadprint.manager.ProductDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
class ProductController {
    private final ProductService catalog;

    ProductController(ProductService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    ProductPage list(@RequestParam(required = false) String search,
                     @RequestParam(required = false) Long categoryId,
                     @RequestParam(required = false) ProductionMode productionMode,
                     @RequestParam(required = false) PricingMode pricingMode,
                     @RequestParam(required = false) Boolean active,
                     @RequestParam(defaultValue = "0") int page,
                     @RequestParam(defaultValue = "20") int size) {
        return catalog.list(search, categoryId, productionMode, pricingMode, active, page, size);
    }

    @GetMapping("/{id}")
    ProductDetail detail(@PathVariable Long id) {
        return catalog.detail(id);
    }

    @PostMapping
    ResponseEntity<ProductView> create(@Valid @RequestBody ProductInput input) {
        ProductView product = catalog.createProduct(input);
        return ResponseEntity.created(URI.create("/api/products/" + product.id())).body(product);
    }

    @PutMapping("/{id}")
    ProductView update(@PathVariable Long id, @Valid @RequestBody ProductInput input) {
        return catalog.updateProduct(id, input);
    }

    @PutMapping("/{id}/status")
    ProductView status(@PathVariable Long id, @Valid @RequestBody StatusInput input) {
        return catalog.setProductStatus(id, input);
    }

    @GetMapping("/{id}/variants")
    List<VariantView> variants(@PathVariable Long id) {
        return catalog.variants(id);
    }

    @PostMapping("/{id}/variants")
    ResponseEntity<VariantView> createVariant(@PathVariable Long id, @Valid @RequestBody VariantInput input) {
        VariantView variant = catalog.createVariant(id, input);
        return ResponseEntity.created(URI.create("/api/products/" + id)).body(variant);
    }

    @PutMapping("/{id}/variants/{variantId}")
    VariantView updateVariant(@PathVariable Long id, @PathVariable Long variantId,
                              @Valid @RequestBody VariantInput input) {
        return catalog.updateVariant(id, variantId, input);
    }

    @PutMapping("/{id}/variants/{variantId}/status")
    VariantView variantStatus(@PathVariable Long id, @PathVariable Long variantId,
                              @Valid @RequestBody StatusInput input) {
        return catalog.setVariantStatus(id, variantId, input);
    }
}
