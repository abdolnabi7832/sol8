package com.institutional.forex.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_portfolio")
public class PortfolioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_balance", nullable = false)
    private Double accountBalance = 50000.0;

    @Column(name = "equity", nullable = false)
    private Double equity = 50000.0;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency = "USD";

    @Column(name = "irt_exchange_rate", nullable = false)
    private Double irtExchangeRate = 68000.0; // 1 USD in Toman

    @Column(name = "target_monthly_return_percent")
    private Double targetMonthlyReturnPercent = 5.0;

    @Column(name = "max_allowed_drawdown_percent")
    private Double maxAllowedDrawdownPercent = 10.0;

    @Column(name = "peak_equity")
    private Double peakEquity = 50000.0;

    @Column(name = "current_drawdown_percent")
    private Double currentDrawdownPercent = 0.0;

    @Column(name = "updated_at_utc", nullable = false)
    private LocalDateTime updatedAtUtc;

    public PortfolioEntity() {
        this.updatedAtUtc = LocalDateTime.now();
    }

    @PrePersist
    @PreUpdate
    public void onUpdate() {
        this.updatedAtUtc = LocalDateTime.now();
        if (this.equity != null && this.peakEquity != null) {
            if (this.equity > this.peakEquity) {
                this.peakEquity = this.equity;
            }
            if (this.peakEquity > 0) {
                this.currentDrawdownPercent = Math.max(0.0, (this.peakEquity - this.equity) / this.peakEquity * 100.0);
            }
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Double getAccountBalance() { return accountBalance; }
    public void setAccountBalance(Double accountBalance) { this.accountBalance = accountBalance; }
    public Double getEquity() { return equity; }
    public void setEquity(Double equity) { this.equity = equity; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Double getIrtExchangeRate() { return irtExchangeRate; }
    public void setIrtExchangeRate(Double irtExchangeRate) { this.irtExchangeRate = irtExchangeRate; }
    public Double getTargetMonthlyReturnPercent() { return targetMonthlyReturnPercent; }
    public void setTargetMonthlyReturnPercent(Double targetMonthlyReturnPercent) { this.targetMonthlyReturnPercent = targetMonthlyReturnPercent; }
    public Double getMaxAllowedDrawdownPercent() { return maxAllowedDrawdownPercent; }
    public void setMaxAllowedDrawdownPercent(Double maxAllowedDrawdownPercent) { this.maxAllowedDrawdownPercent = maxAllowedDrawdownPercent; }
    public Double getPeakEquity() { return peakEquity; }
    public void setPeakEquity(Double peakEquity) { this.peakEquity = peakEquity; }
    public Double getCurrentDrawdownPercent() { return currentDrawdownPercent; }
    public void setCurrentDrawdownPercent(Double currentDrawdownPercent) { this.currentDrawdownPercent = currentDrawdownPercent; }
    public LocalDateTime getUpdatedAtUtc() { return updatedAtUtc; }
    public void setUpdatedAtUtc(LocalDateTime updatedAtUtc) { this.updatedAtUtc = updatedAtUtc; }
}
