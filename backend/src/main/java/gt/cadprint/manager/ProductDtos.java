package gt.cadprint.manager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

final class ProductDtos {
    private ProductDtos() {
    }

    record CategoryInput(@NotBlank @Size(max = 255) String name, @PositiveOrZero Long version) {
    }

    record ProductInput(@NotBlank @Size(max = 255) String sku, @NotBlank @Size(max = 255) String name,
                        @Size(max = 2000) String description, @NotNull @Positive Long categoryId,
                        @NotNull ProductionMode productionMode, @NotNull PricingMode pricingMode,
                        BigDecimal basePrice, @PositiveOrZero Long version) {
    }

    record VariantInput(@NotBlank @Size(max = 255) String sku, @NotBlank @Size(max = 255) String name,
                        @Size(max = 255) String size, @Size(max = 255) String color,
                        @Size(max = 255) String presentation, BigDecimal ownPrice, @PositiveOrZero Long version) {
    }

    record StatusInput(@NotNull Boolean active, @NotNull @PositiveOrZero Long version) {
    }

    record CategoryView(Long id, String name, boolean active, Long version) {
    }

    record ProductView(Long id, String sku, String name, String description, CategoryView category,
                       ProductionMode productionMode, PricingMode pricingMode, BigDecimal basePrice,
                       BigDecimal effectivePrice, boolean requiresQuotation, boolean active, Instant createdAt,
                       Instant updatedAt, Long version) {
    }

    record VariantView(Long id, Long productId, String sku, String name, String size, String color,
                       String presentation, PricingMode pricingMode, BigDecimal basePrice, BigDecimal ownPrice,
                       BigDecimal effectivePrice, boolean requiresQuotation, boolean active,
                       boolean commerciallyActive, Long version) {
    }

    record ProductDetail(ProductView product, List<VariantView> variants, ProductImageService.ImageView image) {
    }

    record ProductPage(List<ProductView> content, int page, int size, long totalElements, int totalPages) {
    }
}
