package com.institutional.forex.model.entity;

import com.institutional.forex.model.enums.ImpactLevel;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Economic Event Entity storing institutional macroeconomic calendar releases,
 * providing microsecond UTC precision and Tehran Time (+03:30) conversion.
 */
@Entity
@Table(
    name = "economic_events",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_event_source_id", columnNames = {"eventId"}),
        @UniqueConstraint(name = "uk_event_natural_key", columnNames = {"currency", "eventName", "eventDateStr", "timeUtc"})
    },
    indexes = {
        @Index(name = "idx_event_ccy", columnList = "currency"),
        @Index(name = "idx_event_exact_utc", columnList = "exactEventTimestampUtc"),
        @Index(name = "idx_event_impact", columnList = "impact"),
        @Index(name = "idx_event_mapped_indicator", columnList = "mappedStandardIndicator")
    }
)
public class EconomicEventEntity {

    private static final ZoneOffset TEHRAN_OFFSET = ZoneOffset.ofHoursMinutes(3, 30);
    private static final DateTimeFormatter TEHRAN_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId; // Unique identifier from provider (e.g. te_cpi_20261105 or ff_145236)

    @Column(name = "currency", nullable = false, length = 3)
    private String currency; // USD, EUR, GBP, JPY, AUD, CAD, CHF, NZD

    @Column(name = "country_code", length = 8)
    private String countryCode; // us, ea, gb, jp, au, ca, ch, nz

    @Column(name = "event_name", nullable = false, length = 180)
    private String eventName;

    @Column(name = "mapped_standard_indicator", length = 100)
    private String mappedStandardIndicator; // Links to one of 20 canonical names e.g. "CPI YoY"

    @Column(name = "event_date_str", nullable = false, length = 16)
    private String eventDateStr; // Format: "yyyy-MM-dd"

    @Column(name = "time_utc", length = 16)
    private String timeUtc; // Format: "HH:mm" or "All Day"

    @Column(name = "time_tehran", length = 32)
    private String timeTehran; // Exact +03:30 Tehran time formatted as "hh:mm a"

    @Column(name = "exact_event_timestamp_utc")
    private LocalDateTime exactEventTimestampUtc;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact", nullable = false, length = 16)
    private ImpactLevel impact; // HIGH, MED, LOW, NONE

    @Column(name = "actual")
    private Double actual;

    @Column(name = "previous")
    private Double previous;

    @Column(name = "consensus")
    private Double consensus;

    @Column(name = "forecast")
    private Double forecast;

    @Column(name = "surprise_std_dev")
    private Double surpriseStdDev;

    @Column(name = "is_quant_simulatable")
    private Boolean isQuantSimulatable = Boolean.TRUE;

    @Column(name = "created_at_utc", nullable = false)
    private LocalDateTime createdAtUtc;

    @Column(name = "updated_at_utc", nullable = false)
    private LocalDateTime updatedAtUtc;

    public EconomicEventEntity() {
        this.createdAtUtc = LocalDateTime.now();
        this.updatedAtUtc = LocalDateTime.now();
    }

    public EconomicEventEntity(String eventId, String currency, String countryCode,
                               String eventName, String eventDateStr, String timeUtc,
                               LocalDateTime exactEventTimestampUtc, ImpactLevel impact) {
        this.eventId = eventId;
        this.currency = currency;
        this.countryCode = countryCode;
        this.eventName = eventName;
        this.eventDateStr = eventDateStr;
        this.timeUtc = timeUtc;
        this.exactEventTimestampUtc = exactEventTimestampUtc;
        this.impact = impact != null ? impact : ImpactLevel.NONE;
        this.createdAtUtc = LocalDateTime.now();
        this.updatedAtUtc = LocalDateTime.now();
        recalculateTehranTime();
    }

    @PrePersist
    public void onCreate() {
        this.createdAtUtc = LocalDateTime.now();
        this.updatedAtUtc = LocalDateTime.now();
        if (this.isQuantSimulatable == null) {
            this.isQuantSimulatable = true;
        }
        recalculateTehranTime();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAtUtc = LocalDateTime.now();
        if (this.isQuantSimulatable == null) {
            this.isQuantSimulatable = true;
        }
        recalculateTehranTime();
    }

    /**
     * Accurately converts the exact UTC timestamp to Tehran Standard Time (+03:30) in "hh:mm a" format.
     */
    public void recalculateTehranTime() {
        if (this.exactEventTimestampUtc != null) {
            LocalDateTime tehranDateTime = this.exactEventTimestampUtc.atOffset(ZoneOffset.UTC)
                    .withOffsetSameInstant(TEHRAN_OFFSET)
                    .toLocalDateTime();
            this.timeTehran = tehranDateTime.format(TEHRAN_FORMATTER);
        } else if (this.timeUtc != null && !this.timeUtc.equalsIgnoreCase("all day")) {
            try {
                String[] parts = this.timeUtc.split(":");
                int hours = Integer.parseInt(parts[0].trim());
                int minutes = Integer.parseInt(parts[1].trim());
                int totalMinutes = hours * 60 + minutes + 210; // +03:30 = 210 minutes
                int tehranHour = (totalMinutes / 60) % 24;
                int tehranMinute = totalMinutes % 60;
                String ampm = tehranHour >= 12 ? "PM" : "AM";
                int displayHour = tehranHour % 12;
                if (displayHour == 0) displayHour = 12;
                this.timeTehran = String.format("%02d:%02d %s", displayHour, tehranMinute, ampm);
            } catch (Exception ignored) {
                this.timeTehran = this.timeUtc;
            }
        } else {
            this.timeTehran = "All Day";
        }
    }

    // --- Getters and Setters ---
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getMappedStandardIndicator() {
        return mappedStandardIndicator;
    }

    public void setMappedStandardIndicator(String mappedStandardIndicator) {
        this.mappedStandardIndicator = mappedStandardIndicator;
    }

    public String getEventDateStr() {
        return eventDateStr;
    }

    public void setEventDateStr(String eventDateStr) {
        this.eventDateStr = eventDateStr;
    }

    public String getTimeUtc() {
        return timeUtc;
    }

    public void setTimeUtc(String timeUtc) {
        this.timeUtc = timeUtc;
        recalculateTehranTime();
    }

    public String getTimeTehran() {
        return timeTehran;
    }

    public void setTimeTehran(String timeTehran) {
        this.timeTehran = timeTehran;
    }

    public LocalDateTime getExactEventTimestampUtc() {
        return exactEventTimestampUtc;
    }

    public void setExactEventTimestampUtc(LocalDateTime exactEventTimestampUtc) {
        this.exactEventTimestampUtc = exactEventTimestampUtc;
        recalculateTehranTime();
    }

    public ImpactLevel getImpact() {
        return impact;
    }

    public void setImpact(ImpactLevel impact) {
        this.impact = impact;
    }

    public Double getActual() {
        return actual;
    }

    public void setActual(Double actual) {
        this.actual = actual;
    }

    public Double getPrevious() {
        return previous;
    }

    public void setPrevious(Double previous) {
        this.previous = previous;
    }

    public Double getConsensus() {
        return consensus;
    }

    public void setConsensus(Double consensus) {
        this.consensus = consensus;
    }

    public Double getForecast() {
        return forecast;
    }

    public void setForecast(Double forecast) {
        this.forecast = forecast;
    }

    public Double getSurpriseStdDev() {
        return surpriseStdDev;
    }

    public void setSurpriseStdDev(Double surpriseStdDev) {
        this.surpriseStdDev = surpriseStdDev;
    }

    public LocalDateTime getCreatedAtUtc() {
        return createdAtUtc;
    }

    public void setCreatedAtUtc(LocalDateTime createdAtUtc) {
        this.createdAtUtc = createdAtUtc;
    }

    public LocalDateTime getUpdatedAtUtc() {
        return updatedAtUtc;
    }

    public void setUpdatedAtUtc(LocalDateTime updatedAtUtc) {
        this.updatedAtUtc = updatedAtUtc;
    }

    public Boolean getIsQuantSimulatable() {
        return isQuantSimulatable != null ? isQuantSimulatable : true;
    }

    public void setIsQuantSimulatable(Boolean isQuantSimulatable) {
        this.isQuantSimulatable = isQuantSimulatable != null ? isQuantSimulatable : true;
    }

    @Override
    public String toString() {
        return "EconomicEventEntity{" +
                "id=" + id +
                ", eventId='" + eventId + '\'' +
                ", currency='" + currency + '\'' +
                ", eventName='" + eventName + '\'' +
                ", exactEventTimestampUtc=" + exactEventTimestampUtc +
                ", timeTehran='" + timeTehran + '\'' +
                ", impact=" + impact +
                ", actual=" + actual +
                ", previous=" + previous +
                ", consensus=" + consensus +
                '}';
    }
}
