package com.institutional.forex.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "trade_journal")
public class TradeJournalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "journal_id", nullable = false, unique = true, length = 64)
    private String journalId;

    @Column(name = "pair", nullable = false, length = 12)
    private String pair;

    @Column(name = "direction", nullable = false, length = 8)
    private String direction;

    @Column(name = "entry_price")
    private Double entryPrice;

    @Column(name = "exit_price")
    private Double exitPrice;

    @Column(name = "lot_size")
    private Double lotSize;

    @Column(name = "pnl_usd", nullable = false)
    private Double pnlUsd;

    @Column(name = "pnl_r")
    private Double pnlR; // e.g. +2.5R or -1.0R

    @Column(name = "pnl_pips")
    private Double pnlPips;

    @Column(name = "setup_type", length = 64)
    private String setupType;

    @Column(name = "entry_date_utc")
    private LocalDateTime entryDateUtc;

    @Column(name = "exit_date_utc")
    private LocalDateTime exitDateUtc;

    @Column(name = "execution_rating")
    private Integer executionRating = 5; // 1 to 5 stars

    @Column(name = "mistake_tags", length = 128)
    private String mistakeTags = "NONE";

    @Lob
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "screenshot_url", length = 512)
    private String screenshotUrl;

    @Column(name = "created_at_utc", nullable = false)
    private LocalDateTime createdAtUtc;

    public TradeJournalEntity() {
        this.createdAtUtc = LocalDateTime.now();
    }

    @PrePersist
    @PreUpdate
    public void validateRMultipleSign() {
        if (this.pnlUsd != null && this.pnlR != null) {
            if (this.pnlUsd < 0 && this.pnlR > 0) {
                this.pnlR = -Math.abs(this.pnlR);
            } else if (this.pnlUsd > 0 && this.pnlR < 0) {
                this.pnlR = Math.abs(this.pnlR);
            }
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getJournalId() { return journalId; }
    public void setJournalId(String journalId) { this.journalId = journalId; }
    public String getPair() { return pair; }
    public void setPair(String pair) { this.pair = pair; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public Double getEntryPrice() { return entryPrice; }
    public void setEntryPrice(Double entryPrice) { this.entryPrice = entryPrice; }
    public Double getExitPrice() { return exitPrice; }
    public void setExitPrice(Double exitPrice) { this.exitPrice = exitPrice; }
    public Double getLotSize() { return lotSize; }
    public void setLotSize(Double lotSize) { this.lotSize = lotSize; }
    public Double getPnlUsd() { return pnlUsd; }
    public void setPnlUsd(Double pnlUsd) { this.pnlUsd = pnlUsd; }
    public Double getPnlR() { return pnlR; }
    public void setPnlR(Double pnlR) { this.pnlR = pnlR; }
    public Double getPnlPips() { return pnlPips; }
    public void setPnlPips(Double pnlPips) { this.pnlPips = pnlPips; }
    public String getSetupType() { return setupType; }
    public void setSetupType(String setupType) { this.setupType = setupType; }
    public LocalDateTime getEntryDateUtc() { return entryDateUtc; }
    public void setEntryDateUtc(LocalDateTime entryDateUtc) { this.entryDateUtc = entryDateUtc; }
    public LocalDateTime getExitDateUtc() { return exitDateUtc; }
    public void setExitDateUtc(LocalDateTime exitDateUtc) { this.exitDateUtc = exitDateUtc; }
    public Integer getExecutionRating() { return executionRating; }
    public void setExecutionRating(Integer executionRating) { this.executionRating = executionRating; }
    public String getMistakeTags() { return mistakeTags; }
    public void setMistakeTags(String mistakeTags) { this.mistakeTags = mistakeTags; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getScreenshotUrl() { return screenshotUrl; }
    public void setScreenshotUrl(String screenshotUrl) { this.screenshotUrl = screenshotUrl; }
    public LocalDateTime getCreatedAtUtc() { return createdAtUtc; }
    public void setCreatedAtUtc(LocalDateTime createdAtUtc) { this.createdAtUtc = createdAtUtc; }
}
