package gt.cadprint.manager;

import java.math.BigDecimal;
import java.time.Instant;

record DailyCashPayment(Long id, Long orderId, String quotationNumber, BigDecimal amount,
                        OrderPayment.Method method, Instant receivedAt, String receivedBy) {}
