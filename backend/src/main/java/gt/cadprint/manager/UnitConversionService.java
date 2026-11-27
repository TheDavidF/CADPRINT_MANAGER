package gt.cadprint.manager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
class UnitConversionService {
    private record Unit(String family, BigDecimal baseFactor) {
    }

    record Conversion(BigDecimal factor, BigDecimal quantity) {
    }

    private static final BigDecimal MAX_QUANTITY = new BigDecimal("9999999999999.999");
    private static final Map<String, Unit> UNITS = Map.of(
            "kg", new Unit("mass", new BigDecimal("1000")),
            "g", new Unit("mass", BigDecimal.ONE),
            "l", new Unit("volume", new BigDecimal("1000")),
            "ml", new Unit("volume", BigDecimal.ONE),
            "metro", new Unit("length", new BigDecimal("100")),
            "cm", new Unit("length", BigDecimal.ONE));

    String normalize(String unit) {
        return unit.strip().toLowerCase(Locale.ROOT);
    }

    Conversion convert(BigDecimal quantity, String from, String to) {
        BigDecimal factor = exactFactor(from, to);
        BigDecimal converted;
        try {
            converted = quantity.multiply(factor).setScale(3, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("La cantidad convertida requiere más de tres decimales");
        }
        if (converted.signum() <= 0 || converted.compareTo(MAX_QUANTITY) > 0) {
            throw new IllegalArgumentException("La cantidad convertida excede el rango de inventario");
        }
        return new Conversion(factor, converted);
    }

    BigDecimal exactFactor(String from, String to) {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("El material no tiene unidad de inventario");
        }
        String source = normalize(from);
        String target = normalize(to);
        return source.equals(target) ? BigDecimal.ONE : factor(source, target);
    }

    private BigDecimal factor(String source, String target) {
        Unit from = UNITS.get(source);
        Unit to = UNITS.get(target);
        if (from == null || to == null || !from.family().equals(to.family())) {
            throw new IllegalArgumentException("Unidades incompatibles: " + source + " y " + target);
        }
        return from.baseFactor().divide(to.baseFactor());
    }
}
