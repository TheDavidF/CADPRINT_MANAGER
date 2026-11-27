package gt.cadprint.manager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import gt.cadprint.manager.QuotationDtos.Discount;
import org.springframework.stereotype.Component;

@Component
class QuotationMoney {
    // v1 rounds discounts to cents before subtraction and excludes shipping from discounts.
    static final String POLICY = "v1";
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal MAX_AMOUNT = BigDecimal.TEN.pow(25).subtract(new BigDecimal("0.01"));

    void calculate(QuotationRevision revision, List<QuotationLine> lines) {
        BigDecimal gross = ZERO;
        BigDecimal discounts = ZERO;
        for (QuotationLine line : lines) {
            if (line.quantity <= 0) {
                throw QuotationService.invalid("La cantidad debe ser positiva");
            }
            line.unitPrice = money(line.unitPrice, "Precio unitario");
            line.grossAmount = money(line.unitPrice.multiply(BigDecimal.valueOf(line.quantity)), "Bruto de línea");
            line.discountAmount = discount(line.discountType, discountValue(line.discountType,
                    line.discountValue, line.discountPercent), line.grossAmount);
            line.netAmount = line.grossAmount.subtract(line.discountAmount);
            gross = gross.add(line.grossAmount);
            discounts = discounts.add(line.discountAmount);
        }
        revision.grossAmount = money(gross, "Bruto general");
        revision.lineDiscountTotal = discounts;
        revision.subtotal = gross.subtract(discounts);
        revision.generalDiscountAmount = discount(revision.generalDiscountType,
                discountValue(revision.generalDiscountType, revision.generalDiscountValue,
                        revision.generalDiscountPercent), revision.subtotal);
        revision.shippingAmount = money(revision.shippingAmount, "Envío");
        revision.totalAmount = money(revision.subtotal.subtract(revision.generalDiscountAmount)
                .add(revision.shippingAmount), "Total general");
        revision.calculationPolicyVersion = POLICY;
    }

    Discount normalize(Discount input) {
        if (input == null) {
            return new Discount(QuotationDiscountType.NONE, null);
        }
        return switch (input.type()) {
            case NONE -> {
                if (input.value() != null) {
                    throw QuotationService.invalid("NONE no admite un valor de descuento");
                }
                yield input;
            }
            case FIXED -> new Discount(input.type(), money(input.value(), "Descuento fijo"));
            case PERCENTAGE -> {
                BigDecimal value = input.value();
                if (value == null || value.signum() < 0 || value.compareTo(HUNDRED) > 0 || value.scale() > 4) {
                    throw QuotationService.invalid("El descuento porcentual debe estar entre 0 y 100 con hasta cuatro decimales");
                }
                yield input;
            }
        };
    }

    static BigDecimal discountValue(QuotationDiscountType type, BigDecimal fixed, BigDecimal percent) {
        return type == QuotationDiscountType.PERCENTAGE ? percent : fixed;
    }

    private BigDecimal discount(QuotationDiscountType type, BigDecimal value, BigDecimal base) {
        BigDecimal result = switch (type) {
            case NONE -> ZERO;
            case FIXED -> value;
            case PERCENTAGE -> base.multiply(value).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
        };
        if (result.compareTo(base) > 0) {
            throw QuotationService.invalid("El descuento no puede superar su base");
        }
        return result;
    }

    private BigDecimal money(BigDecimal value, String field) {
        if (value == null || value.signum() < 0 || value.scale() > 2) {
            throw QuotationService.invalid(field + " debe ser no negativo y tener hasta dos decimales");
        }
        if (value.compareTo(MAX_AMOUNT) > 0) {
            throw QuotationService.invalid(field + " excede la precisión monetaria admitida");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
