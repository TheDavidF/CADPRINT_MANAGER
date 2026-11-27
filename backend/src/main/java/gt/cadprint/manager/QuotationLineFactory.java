package gt.cadprint.manager;

import java.math.BigDecimal;

import gt.cadprint.manager.QuotationDtos.Discount;
import gt.cadprint.manager.QuotationDtos.LineInput;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

@Component
class QuotationLineFactory {
    private final EntityManager entities;
    private final QuotationMoney money;

    QuotationLineFactory(EntityManager entities, QuotationMoney money) {
        this.entities = entities;
        this.money = money;
    }

    QuotationLine create(QuotationRevision revision, int position, LineInput input) {
        var line = new QuotationLine();
        line.revision = revision;
        line.position = position;
        line.lineType = input.type();
        line.quantity = input.quantity().intValueExact();
        BigDecimal suggestedPrice = null;
        if (input.type() == QuotationLineType.CATALOG) {
            suggestedPrice = catalogSnapshot(line, input);
        } else {
            if (input.productId() != null || input.variantId() != null || blank(input.description())) {
                throw QuotationService.invalid("Una línea personalizada requiere descripción y no admite referencias de catálogo");
            }
            line.nameSnapshot = blank(input.name()) ? "Artículo personalizado" : input.name().strip();
            line.descriptionSnapshot = input.description().strip();
        }
        line.unitPrice = input.unitPrice() == null ? suggestedPrice : input.unitPrice();
        if (line.unitPrice == null) {
            throw QuotationService.invalid("Indica un precio unitario para la línea " + position);
        }
        Discount discount = money.normalize(input.discount());
        line.discountType = discount.type();
        line.discountValue = discount.type() == QuotationDiscountType.FIXED ? discount.value() : null;
        line.discountPercent = discount.type() == QuotationDiscountType.PERCENTAGE ? discount.value() : null;
        line.productionCalculation = find(ProductionCalculation.class, input.productionCalculationId(), "Cálculo");
        validateCalculation(line);
        return line;
    }

    private BigDecimal catalogSnapshot(QuotationLine line, LineInput input) {
        var variant = find(ProductVariant.class, input.variantId(), "Variante");
        var product = find(Product.class, input.productId(), "Producto");
        if (variant != null) {
            Product owner = Hibernate.unproxy(variant.product, Product.class);
            if (product != null && !product.id.equals(owner.id)) {
                throw QuotationService.invalid("La variante no pertenece al producto");
            }
            product = owner;
        }
        if (product == null || !product.active || (variant != null && !variant.active)) {
            throw QuotationService.invalid("La línea requiere un producto o variante activa");
        }
        line.product = product;
        line.variant = variant;
        line.skuSnapshot = variant == null ? product.getSku() : variant.getSku();
        line.nameSnapshot = variant == null ? product.name : variant.name;
        line.descriptionSnapshot = product.description;
        return variant != null && variant.ownPrice != null ? variant.ownPrice : product.basePrice;
    }

    private void validateCalculation(QuotationLine line) {
        var calculation = line.productionCalculation;
        if (calculation == null) {
            return;
        }
        boolean compatible;
        if (calculation.variant != null) {
            compatible = line.variant != null && id(calculation.variant).equals(id(line.variant));
        } else if (calculation.product != null) {
            compatible = line.product != null && id(calculation.product).equals(id(line.product));
        } else {
            compatible = line.lineType == QuotationLineType.CUSTOM;
        }
        if (!compatible) {
            throw QuotationService.invalid("El cálculo histórico no corresponde a la línea " + line.position);
        }
    }

    QuotationLine copy(QuotationLine source, QuotationRevision revision) {
        var line = new QuotationLine();
        line.revision = revision;
        line.position = source.position;
        line.lineType = source.lineType;
        line.product = source.product;
        line.variant = source.variant;
        line.productionCalculation = source.productionCalculation;
        line.skuSnapshot = source.skuSnapshot;
        line.nameSnapshot = source.nameSnapshot;
        line.descriptionSnapshot = source.descriptionSnapshot;
        line.quantity = source.quantity;
        line.unitPrice = source.unitPrice;
        line.discountType = source.discountType;
        line.discountValue = source.discountValue;
        line.discountPercent = source.discountPercent;
        line.discountAmount = source.discountAmount;
        line.grossAmount = source.grossAmount;
        line.netAmount = source.netAmount;
        return line;
    }

    Long id(Object entity) {
        return entity == null ? null : (Long) entities.getEntityManagerFactory().getPersistenceUnitUtil()
                .getIdentifier(entity);
    }

    private <T> T find(Class<T> type, Long id, String label) {
        if (id == null) {
            return null;
        }
        T entity = entities.find(type, id);
        if (entity == null) {
            throw QuotationService.missing(label + " no encontrado");
        }
        return Hibernate.unproxy(entity, type);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
