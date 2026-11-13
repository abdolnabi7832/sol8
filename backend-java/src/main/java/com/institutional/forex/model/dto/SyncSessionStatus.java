package com.institutional.forex.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SyncSessionStatus: Real-time telemetry object tracking multi-source data ingestion,
 * per-currency scraping progression, and verified change logs.
 */
public class SyncSessionStatus {

    public enum State {
        IDLE, RUNNING, COMPLETED, PARTIAL_WARNING, FAILED
    }

    public enum CurrencySyncState {
        PENDING, SCANNING, UPDATED, NO_CHANGE, FALLBACK_USED
    }

    public static class CurrencySyncProgress {
        private String currency;
        private CurrencySyncState status;
        private int extractedCount;
        private int totalExpected;
        private String message;

        public CurrencySyncProgress() {
            this.status = CurrencySyncState.PENDING;
            this.extractedCount = 0;
            this.totalExpected = 20;
            this.message = "Queued";
        }

        public CurrencySyncProgress(String currency, CurrencySyncState status, int extractedCount, int totalExpected, String message) {
            this.currency = currency;
            this.status = status;
            this.extractedCount = extractedCount;
            this.totalExpected = totalExpected;
            this.message = message;
        }

        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public CurrencySyncState getStatus() { return status; }
        public void setStatus(CurrencySyncState status) { this.status = status; }
        public int getExtractedCount() { return extractedCount; }
        public void setExtractedCount(int extractedCount) { this.extractedCount = extractedCount; }
        public int getTotalExpected() { return totalExpected; }
        public void setTotalExpected(int totalExpected) { this.totalExpected = totalExpected; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class SyncChangeLog {
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        private LocalDateTime timestamp;
        private String currency;
        private String indicatorName;
        private Double oldValue;
        private Double newValue;
        private String oldReleaseDate;
        private String newReleaseDate;

        public SyncChangeLog() {}

        public SyncChangeLog(LocalDateTime timestamp, String currency, String indicatorName,
                             Double oldValue, Double newValue, String oldReleaseDate, String newReleaseDate) {
            this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
            this.currency = currency;
            this.indicatorName = indicatorName;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.oldReleaseDate = oldReleaseDate;
            this.newReleaseDate = newReleaseDate;
        }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public String getIndicatorName() { return indicatorName; }
        public void setIndicatorName(String indicatorName) { this.indicatorName = indicatorName; }
        public Double getOldValue() { return oldValue; }
        public void setOldValue(Double oldValue) { this.oldValue = oldValue; }
        public Double getNewValue() { return newValue; }
        public void setNewValue(Double newValue) { this.newValue = newValue; }
        public String getOldReleaseDate() { return oldReleaseDate; }
        public void setOldReleaseDate(String oldReleaseDate) { this.oldReleaseDate = oldReleaseDate; }
        public String getNewReleaseDate() { return newReleaseDate; }
        public void setNewReleaseDate(String newReleaseDate) { this.newReleaseDate = newReleaseDate; }
    }

    private State state = State.IDLE;
    private int progressPercent = 0;
    private String currentTaskDescription = "System ready. Institutional telemetry online.";
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime lastRunStartTimeUtc;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime lastCompletedSyncTimeUtc;
    
    private String lastCompletedSyncTimeTehran;
    private Map<String, CurrencySyncProgress> currencySyncMap = new ConcurrentHashMap<>();
    private List<SyncChangeLog> changeLogs = new CopyOnWriteArrayList<>();

    private static final ZoneOffset TEHRAN_OFFSET = ZoneOffset.ofHoursMinutes(3, 30);
    private static final DateTimeFormatter TEHRAN_FORMATTER = DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH);

    public SyncSessionStatus() {
        initializeCurrencies();
    }

    public void initializeCurrencies() {
        List<String> currencies = List.of("USD", "EUR", "GBP", "JPY", "AUD", "CAD", "CHF", "NZD");
        for (String ccy : currencies) {
            currencySyncMap.put(ccy, new CurrencySyncProgress(ccy, CurrencySyncState.PENDING, 0, 20, "Ready"));
        }
    }

    public void markCompleted(boolean hadWarnings) {
        this.state = hadWarnings ? State.PARTIAL_WARNING : State.COMPLETED;
        this.progressPercent = 100;
        this.lastCompletedSyncTimeUtc = LocalDateTime.now();
        this.lastCompletedSyncTimeTehran = this.lastCompletedSyncTimeUtc.atOffset(ZoneOffset.UTC)
                .withOffsetSameInstant(TEHRAN_OFFSET)
                .format(TEHRAN_FORMATTER);
        this.currentTaskDescription = hadWarnings
                ? "Synchronization completed with partial network fallbacks. Institutional baseline preserved."
                : "All 160 macro indicators, calendar events, and dual files synchronized successfully.";
    }

    public void markFailed(String errorMessage) {
        this.state = State.FAILED;
        this.progressPercent = 100;
        this.currentTaskDescription = "Synchronization stopped: " + errorMessage;
        this.lastCompletedSyncTimeUtc = LocalDateTime.now();
        this.lastCompletedSyncTimeTehran = this.lastCompletedSyncTimeUtc.atOffset(ZoneOffset.UTC)
                .withOffsetSameInstant(TEHRAN_OFFSET)
                .format(TEHRAN_FORMATTER);
    }

    // Getters and Setters
    public State getState() { return state; }
    public void setState(State state) { this.state = state; }
    public int getProgressPercent() { return progressPercent; }
    public void setProgressPercent(int progressPercent) { this.progressPercent = Math.max(0, Math.min(100, progressPercent)); }
    public String getCurrentTaskDescription() { return currentTaskDescription; }
    public void setCurrentTaskDescription(String currentTaskDescription) { this.currentTaskDescription = currentTaskDescription; }
    public LocalDateTime getLastRunStartTimeUtc() { return lastRunStartTimeUtc; }
    public void setLastRunStartTimeUtc(LocalDateTime lastRunStartTimeUtc) { this.lastRunStartTimeUtc = lastRunStartTimeUtc; }
    public LocalDateTime getLastCompletedSyncTimeUtc() { return lastCompletedSyncTimeUtc; }
    public void setLastCompletedSyncTimeUtc(LocalDateTime lastCompletedSyncTimeUtc) { this.lastCompletedSyncTimeUtc = lastCompletedSyncTimeUtc; }
    public String getLastCompletedSyncTimeTehran() { return lastCompletedSyncTimeTehran; }
    public void setLastCompletedSyncTimeTehran(String lastCompletedSyncTimeTehran) { this.lastCompletedSyncTimeTehran = lastCompletedSyncTimeTehran; }
    public Map<String, CurrencySyncProgress> getCurrencySyncMap() { return currencySyncMap; }
    public void setCurrencySyncMap(Map<String, CurrencySyncProgress> currencySyncMap) { this.currencySyncMap = currencySyncMap; }
    public List<SyncChangeLog> getChangeLogs() { return changeLogs; }
    public void setChangeLogs(List<SyncChangeLog> changeLogs) { this.changeLogs = changeLogs; }
}
