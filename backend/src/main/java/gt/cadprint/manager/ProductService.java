package gt.cadprint.manager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import gt.cadprint.manager.ProductDtos.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;
import org.hibernate.Hibernate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
class ProductService {
    private final ProductCategoryRepo categories;
    private final ProductRepo products;
    private final ProductVariantRepo variants;
    private final EntityManager entityManager;
    private final ProductImageService images;

    ProductService(ProductCategoryRepo categories, ProductRepo products, ProductVariantRepo variants,
                   EntityManager entityManager, ProductImageService images) {
        this.categories = categories;
        this.products = products;
        this.variants = variants;
        this.entityManager = entityManager;
        this.images = images;
    }

    List<CategoryView> categories() {
        return categories.findAll(Sort.by("normalizedName", "id")).stream().map(this::categoryView).toList();
    }

    @Transactional
    CategoryView createCategory(CategoryInput input) {
        ProductCategory category = new ProductCategory();
        category.name = categoryName(input.name());
        return categoryView(categories.saveAndFlush(category));
    }

    @Transactional
    CategoryView updateCategory(Long id, CategoryInput input) {
        ProductCategory category = lockCategory(id);
        checkVersion(input.version(), category.version);
        category.name = categoryName(input.name());
        categories.flush();
        return categoryView(category);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    CategoryView setCategoryStatus(Long id, StatusInput input) {
        ProductCategory category = lockCategory(id);
        checkVersion(input.version(), category.version);
        boolean active = requiredActive(input.active());
        if (!active && products.existsByCategoryIdAndActiveTrue(id)) {
            throw conflict("La categoría tiene productos activos");
        }
        category.active = active;
        categories.flush();
        return categoryView(category);
    }

    ProductPage list(String search, Long categoryId, ProductionMode productionMode, PricingMode pricingMode,
                     Boolean active, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (categoryId != null && categoryId <= 0)) {
            throw invalid("Filtros o paginación inválidos");
        }
        String term = optionalText(search, 255, "búsqueda");
        String pattern = term == null || term.isEmpty() ? null : "%" + escapeLike(term.toLowerCase(Locale.ROOT)) + "%";
        var result = products.findAll((root, query, criteria) -> {
            List<Predicate> filters = new ArrayList<>();
            if (pattern != null) {
                filters.add(criteria.or(
                        criteria.like(criteria.lower(root.get("name")), pattern, '!'),
                        criteria.like(criteria.lower(root.get("sku").get("value")), pattern, '!')));
            }
            if (categoryId != null) {
                filters.add(criteria.equal(root.get("category").get("id"), categoryId));
            }
            if (productionMode != null) {
                filters.add(criteria.equal(root.get("productionMode"), productionMode));
            }
            if (pricingMode != null) {
                filters.add(criteria.equal(root.get("pricingMode"), pricingMode));
            }
            if (active != null) {
                filters.add(criteria.equal(root.get("active"), active));
            }
            return criteria.and(filters.toArray(Predicate[]::new));
        }, PageRequest.of(page, size, Sort.by("name", "id")));
        return new ProductPage(result.map(this::productView).getContent(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    ProductDetail detail(Long id) {
        Product product = findProduct(id);
        return new ProductDetail(productView(product), variantViews(product), images.metadata(id));
    }

    @Transactional
    ProductView createProduct(ProductInput input) {
        Product product = new Product(normalizeSku(input.sku()));
        product.category = activeCategory(input.categoryId());
        applyProductFields(product, input);
        return productView(products.saveAndFlush(product));
    }

    @Transactional
    ProductView updateProduct(Long id, ProductInput input) {
        Product product = lockProduct(id);
        checkVersion(input.version(), product.version);
        Long categoryId = requireCategoryId(input.categoryId());
        if (!categoryId.equals(product.category.getId())) {
            product.category = activeCategory(categoryId);
        }
        applyProductFields(product, input);
        product.renameSku(normalizeSku(input.sku()));
        products.flush();
        if (product.version.equals(input.version())) {
            entityManager.lock(product, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        }
        return productView(product);
    }

    @Transactional
    ProductView setProductStatus(Long id, StatusInput input) {
        Product product = lockProduct(id);
        checkVersion(input.version(), product.version);
        boolean active = requiredActive(input.active());
        if (active) {
            activeCategory(product.category.getId());
        }
        product.active = active;
        products.flush();
        return productView(product);
    }

    List<VariantView> variants(Long id) {
        return variantViews(findProduct(id));
    }

    @Transactional
    VariantView createVariant(Long id, VariantInput input) {
        Product product = findProduct(id);
        ProductVariant variant = new ProductVariant(product, normalizeSku(input.sku()));
        applyVariantFields(variant, input);
        return variantView(product, variants.saveAndFlush(variant));
    }

    @Transactional
    VariantView updateVariant(Long id, Long variantId, VariantInput input) {
        Product product = findProduct(id);
        ProductVariant variant = lockVariant(id, variantId);
        checkVersion(input.version(), variant.version);
        applyVariantFields(variant, input);
        variant.renameSku(normalizeSku(input.sku()));
        variants.flush();
        if (variant.version.equals(input.version())) {
            entityManager.lock(variant, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        }
        return variantView(product, variant);
    }

    @Transactional
    VariantView setVariantStatus(Long id, Long variantId, StatusInput input) {
        Product product = findProduct(id);
        ProductVariant variant = lockVariant(id, variantId);
        checkVersion(input.version(), variant.version);
        variant.active = requiredActive(input.active());
        variants.flush();
        return variantView(product, variant);
    }

    private void applyProductFields(Product product, ProductInput input) {
        product.name = requiredText(input.name(), 255, "nombre");
        product.description = optionalText(input.description(), 2000, "descripción");
        if (input.productionMode() == null || input.pricingMode() == null) {
            throw invalid("Las modalidades de producción y precio son obligatorias");
        }
        if (input.pricingMode() == PricingMode.PRECIO_BASE && input.basePrice() == null) {
            throw invalid("El precio base es obligatorio en modalidad PRECIO_BASE");
        }
        validatePrice(input.basePrice());
        product.productionMode = input.productionMode();
        product.pricingMode = input.pricingMode();
        product.basePrice = input.basePrice();
    }

    private void applyVariantFields(ProductVariant variant, VariantInput input) {
        variant.name = requiredText(input.name(), 255, "nombre");
        variant.size = optionalText(input.size(), 255, "tamaño");
        variant.color = optionalText(input.color(), 255, "color");
        variant.presentation = optionalText(input.presentation(), 255, "presentación");
        validatePrice(input.ownPrice());
        variant.ownPrice = input.ownPrice();
    }

    private List<VariantView> variantViews(Product product) {
        return variants.findByProductIdOrderByIdAsc(product.id).stream()
                .map(variant -> variantView(product, variant)).toList();
    }

    private CategoryView categoryView(ProductCategory category) {
        ProductCategory loaded = Hibernate.unproxy(category, ProductCategory.class);
        return new CategoryView(loaded.id, loaded.name, loaded.active, loaded.version);
    }

    private ProductView productView(Product product) {
        return new ProductView(product.id, product.getSku(), product.name, product.description,
                categoryView(product.category), product.productionMode, product.pricingMode,
                product.basePrice, product.basePrice, product.pricingMode == PricingMode.REQUIERE_COTIZACION,
                product.active, product.createdAt, product.updatedAt, product.version);
    }

    private VariantView variantView(Product product, ProductVariant variant) {
        BigDecimal effective = variant.ownPrice == null ? product.basePrice : variant.ownPrice;
        return new VariantView(variant.id, product.id, variant.getSku(), variant.name, variant.size, variant.color,
                variant.presentation, product.pricingMode, product.basePrice, variant.ownPrice, effective,
                product.pricingMode == PricingMode.REQUIERE_COTIZACION, variant.active,
                product.active && variant.active, variant.version);
    }

    private ProductCategory activeCategory(Long id) {
        ProductCategory category = lockCategory(requireCategoryId(id));
        if (!category.active) {
            throw conflict("La categoría comercial está inactiva");
        }
        return category;
    }

    private ProductCategory lockCategory(Long id) {
        return categories.locked(id).map(category -> Hibernate.unproxy(category, ProductCategory.class))
                .orElseThrow(() -> missing("Categoría comercial no encontrada"));
    }

    private Product findProduct(Long id) {
        return products.findById(id).orElseThrow(() -> missing("Producto no encontrado"));
    }

    private Product lockProduct(Long id) {
        return products.locked(id).orElseThrow(() -> missing("Producto no encontrado"));
    }

    private ProductVariant lockVariant(Long productId, Long variantId) {
        return variants.locked(productId, variantId).orElseThrow(() -> missing("Variante no encontrada"));
    }

    private Long requireCategoryId(Long id) {
        if (id == null || id <= 0) {
            throw invalid("La categoría comercial es obligatoria");
        }
        return id;
    }

    private boolean requiredActive(Boolean active) {
        if (active == null) {
            throw invalid("El estado activo es obligatorio");
        }
        return active;
    }

    private void checkVersion(Long requested, Long actual) {
        if (requested == null || requested < 0) {
            throw invalid("La versión es obligatoria");
        }
        if (!requested.equals(actual)) {
            throw conflict("La versión del registro está desactualizada");
        }
    }

    private String categoryName(String name) {
        String normalized = requiredText(name, 255, "nombre").strip();
        if (normalized.toLowerCase(Locale.ROOT).length() > 255) {
            throw invalid("El nombre normalizado excede 255 caracteres");
        }
        return normalized;
    }

    private String requiredText(String value, int max, String field) {
        if (value == null || value.isBlank()) {
            throw invalid("El campo " + field + " es obligatorio");
        }
        return optionalText(value, max, field);
    }

    private String optionalText(String value, int max, String field) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.length() > max) {
            throw invalid("El campo " + field + " excede " + max + " caracteres");
        }
        return trimmed;
    }

    private String normalizeSku(String value) {
        try {
            return CatalogSku.normalize(value);
        } catch (IllegalArgumentException exception) {
            throw invalid(exception.getMessage());
        }
    }

    private void validatePrice(BigDecimal value) {
        try {
            CatalogPrice.validateOptional(value);
        } catch (IllegalArgumentException exception) {
            throw invalid(exception.getMessage());
        }
    }

    private String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private ResponseStatusException missing(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
