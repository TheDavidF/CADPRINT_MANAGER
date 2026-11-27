package gt.cadprint.manager;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class ProductionCostMath {
    static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);
    static final BigDecimal WATT_SECONDS_PER_KWH = BigDecimal.valueOf(3_600_000);

    private ProductionCostMath() {
    }

    static BigDecimal money(BigDecimal value) {
        return scale(value, 19, 2);
    }

    static BigDecimal scale(BigDecimal value, int precision, int fraction) {
        BigDecimal scaled = value.setScale(fraction, RoundingMode.HALF_UP);
        if (scaled.precision() > precision) {
            throw invalid("Un resultado excede el rango decimal permitido");
        }
        return scaled;
    }

    static BigDecimal divide(BigDecimal numerator, BigDecimal denominator, int precision, int fraction) {
        return scale(numerator.divide(denominator, fraction, RoundingMode.HALF_UP), precision, fraction);
    }

    static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    static ResponseStatusException missing(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
