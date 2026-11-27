package gt.cadprint.manager;

import java.math.BigDecimal;

final class CatalogPrice {
    private static final BigDecimal MAX_GTQ_PRICE = new BigDecimal("99999999999999.99");

    private CatalogPrice() {
    }

    static void validateOptional(BigDecimal price) {
        if (price != null && (price.signum() <= 0 || price.scale() > 2 || price.compareTo(MAX_GTQ_PRICE) > 0)) {
            throw new IllegalArgumentException("El precio en GTQ debe ser positivo y admitir hasta dos decimales");
        }
    }
}
