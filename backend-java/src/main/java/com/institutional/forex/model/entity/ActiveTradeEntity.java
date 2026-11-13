package com.institutional.forex.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "active_trades",
    indexes = {
        @Index(name = "idx_trade_pair", columnList = "pair"),
        @Index(name = "idx_trade_status", columnList = "status")
    }
)
public class ActiveTradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_id", nullable = false, unique = true, length = 64)
    private String tradeId;

    @Column(name = "pair", nullable = false, length = 12)
    private String pair; // e.g. "EURUSD"

    @Column(name = "direction", nullable = false, length = 8)
    private String direction; // "BUY" or "SELL"

    @Column(name = "lot_size", nullable = false)
    private Double lotSize;

    @Column(name = "entry_price", nullable = false)
    private Double entryPrice;

    @Column(name = "stop_loss", nullable = false)
    private Double stopLoss;

    @Column(name = "take_profit")
    private Double takeProfit;

    @Column(name = "current_price")
    private Double currentPrice;

    @Column(name = "open_risk_usd", nullable = false)
    private Double openRiskUsd;

    @Column(name = "open_risk_percent", nullable = false)
    private Double openRiskPercent;

    @Column(name = "unrealized_pnl_usd")
    private Double unrealizedPnlUsd = 0.0;

    @Column(name = "unrealized_pnl_pips")
    private Double unrealizedPnlPips = 0.0;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "OPEN"; // OPEN, CLOSED, PENDING

    @Column(name = "setup_type", length = 64)
    private String setupType = "7-Gate Ultra-Safe";

    @Column(name = "opened_at_utc", nullable = false)
    private LocalDateTime openedAtUtc;

    @Column(name = "closed_at_utc")
    private LocalDateTime closedAtUtc;

    public ActiveTradeEntity() {
        this.openedAtUtc = LocalDateTime.now();
    }

    public ActiveTradeEntity(String tradeId, String pair, String direction, Double lotSize,
                             Double entryPrice, Double stopLoss, Double takeProfit,
                             Double openRiskUsd, Double openRiskPercent) {
        this.tradeId = tradeId;
        this.pair = pair;
        this.direction = direction;
        this.lotSize = lotSize;
        this.entryPrice = entryPrice;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.currentPrice = entryPrice;
        this.openRiskUsd = openRiskUsd;
        this.openRiskPercent = openRiskPercent;
        this.openedAtUtc = LocalDateTime.now();
        this.status = "OPEN";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTradeId() { return tradeId; }
    public void setTradeId(String tradeId) { this.tradeId = tradeId; }
    public String getPair() { return pair; }
    public void setPair(String pair) { this.pair = pair; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public Double getLotSize() { return lotSize; }
    public void setLotSize(Double lotSize) { this.lotSize = lotSize; }
    public Double getEntryPrice() { return entryPrice; }
    public void setEntryPrice(Double entryPrice) { this.entryPrice = entryPrice; }
    public Double getStopLoss() { return stopLoss; }
    public void setStopLoss(Double stopLoss) { this.stopLoss = stopLoss; }
    public Double getTakeProfit() { return takeProfit; }
    public void setTakeProfit(Double takeProfit) { this.takeProfit = takeProfit; }
    public Double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(Double currentPrice) { this.currentPrice = currentPrice; }
    public Double getOpenRiskUsd() { return openRiskUsd; }
    public void setOpenRiskUsd(Double openRiskUsd) { this.openRiskUsd = openRiskUsd; }
    public Double getOpenRiskPercent() { return openRiskPercent; }
    public void setOpenRiskPercent(Double openRiskPercent) { this.openRiskPercent = openRiskPercent; }
    public Double getUnrealizedPnlUsd() { return unrealizedPnlUsd; }
    public void setUnrealizedPnlUsd(Double unrealizedPnlUsd) { this.unrealizedPnlUsd = unrealizedPnlUsd; }
    public Double getUnrealizedPnlPips() { return unrealizedPnlPips; }
    public void setUnrealizedPnlPips(Double unrealizedPnlPips) { this.unrealizedPnlPips = unrealizedPnlPips; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSetupType() { return setupType; }
    public void setSetupType(String setupType) { this.setupType = setupType; }
    public LocalDateTime getOpenedAtUtc() { return openedAtUtc; }
    public void setOpenedAtUtc(LocalDateTime openedAtUtc) { this.openedAtUtc = openedAtUtc; }
    public LocalDateTime getClosedAtUtc() { return closedAtUtc; }
    public void setClosedAtUtc(LocalDateTime closedAtUtc) { this.closedAtUtc = closedAtUtc; }
}
