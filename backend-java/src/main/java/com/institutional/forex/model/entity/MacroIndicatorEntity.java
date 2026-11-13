package com.institutional.forex.model.entity;

import com.institutional.forex.model.enums.Frequency;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Macro Indicator Entity representing one of the 20 institutional macroeconomic
 * indicators across the 6 Anton Kreil pillars for a specific G8 currency.
 */
@Entity
@Table(
    name = "macro_indicators",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_ccy_indicator", columnNames = {"currency", "indicatorName"})
    },
    indexes = {
        @Index(name = "idx_macro_ccy", columnList = "currency"),
        @Index(name = "idx_macro_pillar", columnList = "pillarNumber"),
        @Index(name = "idx_macro_exact_release", columnList = "exactReleaseTimestampUtc"),
        @Index(name = "idx_macro_next_release", columnList = "nextReleaseTimestampUtc")
    }
)
public class MacroIndicatorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency; // USD, EUR, GBP, JPY, AUD, CAD, CHF, NZD

    @Column(name = "pillar_number", nullable = false)
    private Integer pillarNumber; // 1 to 6

    @Column(name = "category", nullable = false, length = 64)
    private String category; // Money, Prices, GDP & Business, Labour, Trade, Consumer & Housing

    @Column(name = "indicator_name", nullable = false, length = 100)
    private String indicatorName; // Standard canonical name (e.g. CPI YoY, Interest Rate)

    // --- Numeric Metric Values ---
    @Column(name = "last_value")
    private Double lastValue;

    @Column(name = "previous_value")
    private Double previousValue;

    @Column(name = "revised_previous_value")
    private Double revisedPreviousValue;

    @Column(name = "consensus_value")
    private Double consensusValue;

    @Column(name = "forecast_value")
    private Double forecastValue;

    @Column(name = "highest_value")
    private Double highestValue;

    @Column(name = "lowest_value")
    private Double lowestValue;

    @Column(name = "target_rate")
    private Double targetRate;

    @Column(name = "historical_mean")
    private Double historicalMean;

    @Column(name = "historical_std_dev")
    private Double historicalStdDev;

    @Column(name = "weight", nullable = false)
    private Double weight; // Intra-pillar weight

    @Column(name = "unit", length = 32)
    private String unit; // %, Index, Points, K, etc.

    // --- Critical Date and Time Tracking ---
    @Column(name = "release_date_raw", length = 64)
    private String releaseDateRaw; // Raw string from provider e.g. "Jul/26" or "2026-10-15"

    @Column(name = "exact_release_timestamp_utc")
    private LocalDateTime exactReleaseTimestampUtc; // Exact timestamp of last release in UTC

    @Column(name = "next_release_timestamp_utc")
    private LocalDateTime nextReleaseTimestampUtc; // Exact linked timestamp of next release in UTC

    @Column(name = "reference_period", length = 32)
    private String referencePeriod; // e.g. "Q3", "Oct", "Sep"

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 16)
    private Frequency frequency; // DAILY, WEEKLY, MONTHLY, QUARTERLY

    @Column(name = "updated_at_utc", nullable = false)
    private LocalDateTime updatedAtUtc;

    // --- Constructors ---
    public MacroIndicatorEntity() {
        this.updatedAtUtc = LocalDateTime.now();
    }

    public MacroIndicatorEntity(String currency, Integer pillarNumber, String category,
                                String indicatorName, Double weight, Double targetRate,
                                Frequency frequency, String unit) {
        this.currency = currency;
        this.pillarNumber = pillarNumber;
        this.category = category;
        this.indicatorName = indicatorName;
        this.weight = weight;
        this.targetRate = targetRate;
        this.frequency = frequency;
        this.unit = unit;
        this.updatedAtUtc = LocalDateTime.now();
    }

    @PrePersist
    @PreUpdate
    public void onUpdate() {
        this.updatedAtUtc = LocalDateTime.now();
    }

    // --- Getters and Setters ---
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Integer getPillarNumber() {
        return pillarNumber;
    }

    public void setPillarNumber(Integer pillarNumber) {
        this.pillarNumber = pillarNumber;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getIndicatorName() {
        return indicatorName;
    }

    public void setIndicatorName(String indicatorName) {
        this.indicatorName = indicatorName;
    }

    public Double getLastValue() {
        return lastValue;
    }

    public void setLastValue(Double lastValue) {
        this.lastValue = lastValue;
    }

    public Double getPreviousValue() {
        return previousValue;
    }

    public void setPreviousValue(Double previousValue) {
        this.previousValue = previousValue;
    }

    public Double getRevisedPreviousValue() {
        return revisedPreviousValue;
    }

    public void setRevisedPreviousValue(Double revisedPreviousValue) {
        this.revisedPreviousValue = revisedPreviousValue;
    }

    public Double getConsensusValue() {
        return consensusValue;
    }

    public void setConsensusValue(Double consensusValue) {
        this.consensusValue = consensusValue;
    }

    public Double getForecastValue() {
        return forecastValue;
    }

    public void setForecastValue(Double forecastValue) {
        this.forecastValue = forecastValue;
    }

    public Double getHighestValue() {
        return highestValue;
    }

    public void setHighestValue(Double highestValue) {
        this.highestValue = highestValue;
    }

    public Double getLowestValue() {
        return lowestValue;
    }

    public void setLowestValue(Double lowestValue) {
        this.lowestValue = lowestValue;
    }

    public Double getTargetRate() {
        return targetRate;
    }

    public void setTargetRate(Double targetRate) {
        this.targetRate = targetRate;
    }

    public Double getHistoricalMean() {
        return historicalMean;
    }

    public void setHistoricalMean(Double historicalMean) {
        this.historicalMean = historicalMean;
    }

    public Double getHistoricalStdDev() {
        return historicalStdDev;
    }

    public void setHistoricalStdDev(Double historicalStdDev) {
        this.historicalStdDev = historicalStdDev;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getReleaseDateRaw() {
        return releaseDateRaw;
    }

    public void setReleaseDateRaw(String releaseDateRaw) {
        this.releaseDateRaw = releaseDateRaw;
    }

    public LocalDateTime getExactReleaseTimestampUtc() {
        return exactReleaseTimestampUtc;
    }

    public void setExactReleaseTimestampUtc(LocalDateTime exactReleaseTimestampUtc) {
        this.exactReleaseTimestampUtc = exactReleaseTimestampUtc;
    }

    public LocalDateTime getNextReleaseTimestampUtc() {
        return nextReleaseTimestampUtc;
    }

    public void setNextReleaseTimestampUtc(LocalDateTime nextReleaseTimestampUtc) {
        this.nextReleaseTimestampUtc = nextReleaseTimestampUtc;
    }

    public String getReferencePeriod() {
        return referencePeriod;
    }

    public void setReferencePeriod(String referencePeriod) {
        this.referencePeriod = referencePeriod;
    }

    public Frequency getFrequency() {
        return frequency;
    }

    public void setFrequency(Frequency frequency) {
        this.frequency = frequency;
    }

    public LocalDateTime getUpdatedAtUtc() {
        return updatedAtUtc;
    }

    public void setUpdatedAtUtc(LocalDateTime updatedAtUtc) {
        this.updatedAtUtc = updatedAtUtc;
    }

    @Override
    public String toString() {
        return "MacroIndicatorEntity{" +
                "id=" + id +
                ", currency='" + currency + '\'' +
                ", pillarNumber=" + pillarNumber +
                ", indicatorName='" + indicatorName + '\'' +
                ", lastValue=" + lastValue +
                ", previousValue=" + previousValue +
                ", exactReleaseTimestampUtc=" + exactReleaseTimestampUtc +
                ", nextReleaseTimestampUtc=" + nextReleaseTimestampUtc +
                '}';
    }
}
