package gt.cadprint.manager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

final class ProductionCostDtos {
    private ProductionCostDtos() {
    }

    record CommonInput(
            @NotBlank @Size(max = 1000) String jobDescription,
            @Positive long expectedGoodUnits,
            @NotNull @Positive Long costProfileId,
            @NotNull @Positive Long machineId,
            @Positive Long productId,
            @Positive Long variantId,
            @NotNull @PositiveOrZero Long productiveSeconds,
            @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal powerWatts,
            @NotEmpty @Size(max = 200) List<@NotNull @Valid MaterialInput> materials,
            @NotNull @PositiveOrZero @DecimalMax(value = "100", inclusive = false)
            @Digits(integer = 3, fraction = 4) BigDecimal targetMarginPct,
            @PositiveOrZero @Digits(integer = 25, fraction = 8) BigDecimal chosenUnitPrice) {
    }

    record MaterialInput(
            @NotNull @Positive Long materialId,
            @NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal quantity,
            @NotBlank @Size(max = 64) String unit,
            boolean acceptZeroCost, @Size(max = 500) String zeroCostReason) {
    }

    record FdmInput(@NotNull @Valid CommonInput common, @Positive long printSeconds,
                    @NotNull @PositiveOrZero Long laborSeconds,
                    @NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal partMassGrams,
                    @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal supportMassGrams) {
    }

    record SublimationInput(@NotNull @Valid CommonInput common, @Positive long cycleCount,
                            @Positive long pressSecondsPerCycle, @NotNull @PositiveOrZero Long preparationSeconds,
                            @NotNull @PositiveOrZero Long laborSeconds) {
    }

    record FdmSave(@NotNull @Valid FdmInput input, @NotBlank @Size(max = 64) String previewToken) {
    }

    record SublimationSave(@NotNull @Valid SublimationInput input, @NotBlank @Size(max = 64) String previewToken) {
    }

    record CostLine(int position, ProductionCostType costType, String description, BigDecimal quantity, String unit,
                    BigDecimal appliedRate, BigDecimal amount, ProductionCostSource costSource, Long materialId,
                    String materialSku, String materialName, String materialUnit, BigDecimal conversionFactor) {
    }

    record PriceEstimate(BigDecimal targetMarginPct, BigDecimal suggestedUnitPrice, BigDecimal chosenUnitPrice,
                         BigDecimal grossProfitPerUnit, BigDecimal actualMarginPct, boolean belowCost) {
    }

    record Preview(ProductionTechnique technique, String formulaVersion, String currency, long expectedGoodUnits,
                   long productiveSeconds, BigDecimal indirectRatePerHour, BigDecimal indirectCost,
                   BigDecimal totalCost, BigDecimal unitCost, List<CostLine> lines, PriceEstimate price,
                   String previewToken) {
    }

    record HistoryItem(Long id, ProductionTechnique technique, String formulaVersion, String username,
                       Instant createdAt, String jobDescription, long expectedGoodUnits, Long productId,
                       Long variantId, Long costProfileId, Long machineId, String commercialName,
                       String commercialSku, String currency, long productiveSeconds, BigDecimal indirectRatePerHour,
                       BigDecimal indirectCost, BigDecimal totalCost, BigDecimal unitCost) {
    }

    record FdmHistory(long printSeconds, long laborSeconds, BigDecimal partMassGrams, BigDecimal supportMassGrams) {
    }

    record SublimationHistory(long cycleCount, long pressSecondsPerCycle, long preparationSeconds, long laborSeconds) {
    }

    record HistoryDetail(HistoryItem calculation, FdmHistory fdm, SublimationHistory sublimation,
                         List<CostLine> lines, PriceEstimate price) {
    }

    record HistoryPage(List<HistoryItem> content, int page, int size, long totalElements, int totalPages) {
    }

    record ProfileInput(@NotBlank @Size(max = 64) String code, @NotBlank @Size(max = 255) String name,
                        @NotNull @PositiveOrZero @Digits(integer = 19, fraction = 8) BigDecimal electricityRatePerKwh,
                        @NotNull @PositiveOrZero @Digits(integer = 19, fraction = 8) BigDecimal laborRatePerHour,
                        @NotNull @PositiveOrZero @Digits(integer = 19, fraction = 8) BigDecimal indirectRatePerHour,
                        @NotNull Boolean active, @PositiveOrZero Long version) {
    }

    record MachineInput(@NotBlank @Size(max = 64) String code, @NotBlank @Size(max = 255) String name,
                        @NotNull ProductionTechnique technique,
                        @Positive @Digits(integer = 13, fraction = 6) BigDecimal nominalPowerWatts,
                        @NotNull @PositiveOrZero @Digits(integer = 19, fraction = 8) BigDecimal machineRatePerHour,
                        @NotNull Boolean active, @PositiveOrZero Long version) {
    }

    record ProfileView(Long id, String code, String name, String currency, BigDecimal electricityRatePerKwh,
                       BigDecimal laborRatePerHour, BigDecimal indirectRatePerHour, boolean active, Long version) {
    }

    record MachineView(Long id, String code, String name, ProductionTechnique technique, BigDecimal nominalPowerWatts,
                       BigDecimal machineRatePerHour, boolean active, Long version) {
    }
}
