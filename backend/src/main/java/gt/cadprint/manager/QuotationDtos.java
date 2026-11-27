package gt.cadprint.manager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

final class QuotationDtos {
    private QuotationDtos() {}

    record Recipient(@Size(max = 255) String name, @Size(max = 255) String contact,
                     @Email @Size(max = 254) String email, @Size(max = 64) String phone,
                     @Size(max = 500) String address) {}

    record Issuer(String name, String email, String phone, String address) {}

    record Discount(@NotNull QuotationDiscountType type, BigDecimal value) {}

    record LineInput(@NotNull QuotationLineType type, @Positive Long productId, @Positive Long variantId,
                     @Positive Long productionCalculationId, @Size(max = 255) String name,
                     @Size(max = 4000) String description, @NotNull @Positive @Digits(integer = 10, fraction = 0)
                     @DecimalMax("2147483647") BigDecimal quantity,
                     @PositiveOrZero @Digits(integer = 25, fraction = 2) BigDecimal unitPrice,
                     @Valid Discount discount) {}

    record DraftInput(@Valid Recipient recipient, @Size(max = 4000) String commercialTerms,
                      @Size(max = 4000) String notes, LocalDate validUntil,
                      @Valid Discount generalDiscount,
                      @PositiveOrZero @Digits(integer = 25, fraction = 2) BigDecimal shippingAmount,
                      @NotNull @Size(max = 200) List<@NotNull @Valid LineInput> lines,
                      @Positive Long customerId) {
        DraftInput(Recipient recipient, String commercialTerms, String notes, LocalDate validUntil,
                   Discount generalDiscount, BigDecimal shippingAmount, List<LineInput> lines) {
            this(recipient, commercialTerms, notes, validUntil, generalDiscount, shippingAmount, lines, null);
        }
    }

    record EditInput(@NotNull @PositiveOrZero Long version, @NotNull @Valid DraftInput draft) {}

    record VersionInput(@NotNull @PositiveOrZero Long version) {}

    record DecisionInput(@Size(max = 1000) String reason) {}

    record Totals(BigDecimal grossAmount, BigDecimal lineDiscountTotal, BigDecimal subtotal,
                  Discount generalDiscount, BigDecimal generalDiscountAmount,
                  BigDecimal shippingAmount, BigDecimal totalAmount) {}

    record CommercialLine(int position, String sku, String name, String description, int quantity,
                          BigDecimal unitPrice, Discount discount, BigDecimal grossAmount,
                          BigDecimal discountAmount, BigDecimal netAmount) {}

    record InternalLine(CommercialLine commercial, QuotationLineType type, Long productId,
                        Long variantId, Long productionCalculationId, boolean belowHistoricalCost) {}

    record CommercialView(String quotationNumber, int revisionNumber, String currency,
                          Recipient recipient, Issuer issuer, Instant issuedAt, LocalDate validUntil,
                          String commercialTerms, Totals totals, List<CommercialLine> lines) {}

    record Summary(Long quotationId, String quotationNumber, long version, int revisionNumber,
                   QuotationStatus status, String recipientName, Instant issuedAt, LocalDate validUntil,
                   Instant replacedAt, BigDecimal totalAmount) {}

    record Detail(Summary revision, Integer sourceRevisionNumber, Integer replacementRevisionNumber,
                  String currency, Recipient recipient, Issuer issuer, String commercialTerms, String notes,
                  String calculationPolicyVersion, Totals totals, List<InternalLine> lines, Long customerId) {}

    record EventView(int revisionNumber, QuotationEventType type, QuotationStatus previousStatus,
                     QuotationStatus newStatus, String actor, Instant occurredAt, String reason, String details) {}

    record QuotationPage(List<Summary> items, int page, int size, long totalElements, int totalPages) {}
}
