package gt.cadprint.manager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DailyCashController {
    private static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");
    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

    private final OrderPaymentRepository payments;

    DailyCashController(OrderPaymentRepository payments) {
        this.payments = payments;
    }

    @GetMapping("/api/cash/daily")
    DailyCash daily(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        var start = date.atStartOfDay(GUATEMALA).toInstant();
        var end = date.plusDays(1).atStartOfDay(GUATEMALA).toInstant();
        var entries = payments.findDailyPayments(start, end);
        BigDecimal cash = ZERO_MONEY;
        BigDecimal transfers = ZERO_MONEY;
        for (var entry : entries) {
            switch (entry.method()) {
                case EFECTIVO -> cash = cash.add(entry.amount());
                case TRANSFERENCIA -> transfers = transfers.add(entry.amount());
            }
        }
        return new DailyCash(date, cash, transfers, cash.add(transfers), entries);
    }

    record DailyCash(LocalDate date, BigDecimal cash, BigDecimal transfers, BigDecimal total,
                     List<DailyCashPayment> payments) {}
}
