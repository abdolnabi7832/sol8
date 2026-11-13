package com.institutional.forex.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "market_structure",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_structure_pair_tf", columnNames = {"pair", "timeframe"})
    }
)
public class MarketStructureEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pair", nullable = false, length = 12)
    private String pair; // e.g. "EURUSD"

    @Column(name = "timeframe", nullable = false, length = 8)
    private String timeframe; // "DAILY", "H4", "H1"

    @Column(name = "trend", nullable = false, length = 16)
    private String trend = "BULLISH"; // "BULLISH", "BEARISH", "RANGE"

    @Column(name = "last_structure_event", length = 32)
    private String lastStructureEvent = "BOS"; // "BOS", "CHoCH", "SWING_HIGH", "SWING_LOW"

    @Column(name = "key_support_level")
    private Double keySupportLevel;

    @Column(name = "key_resistance_level")
    private Double keyResistanceLevel;

    @Column(name = "current_price")
    private Double currentPrice;

    @Column(name = "last_event_timestamp_utc")
    private LocalDateTime lastEventTimestampUtc;

    @Column(name = "updated_at_utc", nullable = false)
    private LocalDateTime updatedAtUtc;

    public MarketStructureEntity() {
        this.updatedAtUtc = LocalDateTime.now();
        this.lastEventTimestampUtc = LocalDateTime.now();
    }

    public MarketStructureEntity(String pair, String timeframe, String trend, String lastStructureEvent,
                                 Double keySupportLevel, Double keyResistanceLevel, Double currentPrice) {
        this.pair = pair;
        this.timeframe = timeframe;
        this.trend = trend;
        this.lastStructureEvent = lastStructureEvent;
        this.keySupportLevel = keySupportLevel;
        this.keyResistanceLevel = keyResistanceLevel;
        this.currentPrice = currentPrice;
        this.lastEventTimestampUtc = LocalDateTime.now();
        this.updatedAtUtc = LocalDateTime.now();
    }

    @PrePersist
    @PreUpdate
    public void onUpdate() {
        this.updatedAtUtc = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPair() { return pair; }
    public void setPair(String pair) { this.pair = pair; }
    public String getTimeframe() { return timeframe; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe; }
    public String getTrend() { return trend; }
    public void setTrend(String trend) { this.trend = trend; }
    public String getLastStructureEvent() { return lastStructureEvent; }
    public void setLastStructureEvent(String lastStructureEvent) { this.lastStructureEvent = lastStructureEvent; }
    public Double getKeySupportLevel() { return keySupportLevel; }
    public void setKeySupportLevel(Double keySupportLevel) { this.keySupportLevel = keySupportLevel; }
    public Double getKeyResistanceLevel() { return keyResistanceLevel; }
    public void setKeyResistanceLevel(Double keyResistanceLevel) { this.keyResistanceLevel = keyResistanceLevel; }
    public Double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(Double currentPrice) { this.currentPrice = currentPrice; }
    public LocalDateTime getLastEventTimestampUtc() { return lastEventTimestampUtc; }
    public void setLastEventTimestampUtc(LocalDateTime lastEventTimestampUtc) { this.lastEventTimestampUtc = lastEventTimestampUtc; }
    public LocalDateTime getUpdatedAtUtc() { return updatedAtUtc; }
    public void setUpdatedAtUtc(LocalDateTime updatedAtUtc) { this.updatedAtUtc = updatedAtUtc; }
}
