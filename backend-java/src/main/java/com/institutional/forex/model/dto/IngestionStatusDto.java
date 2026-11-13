package com.institutional.forex.model.dto;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Status and diagnostic metrics of the data ingestion and synchronization engine.
 */
public class IngestionStatusDto {

    private boolean isRunning;
    private LocalDateTime lastRunStartTimeUtc;
    private LocalDateTime lastRunEndTimeUtc;
    private long totalIndicatorsUpdated;
    private long totalCalendarEventsProcessed;
    private long totalEventsCrossLinked;
    private String lastStatusMessage;
    private Map<String, Integer> currencyIndicatorCounts = new HashMap<>();
    private Map<String, String> syncFilesStatus = new HashMap<>();

    public IngestionStatusDto() {}

    public boolean isRunning() { return isRunning; }
    public void setRunning(boolean running) { isRunning = running; }
    public LocalDateTime getLastRunStartTimeUtc() { return lastRunStartTimeUtc; }
    public void setLastRunStartTimeUtc(LocalDateTime lastRunStartTimeUtc) { this.lastRunStartTimeUtc = lastRunStartTimeUtc; }
    public LocalDateTime getLastRunEndTimeUtc() { return lastRunEndTimeUtc; }
    public void setLastRunEndTimeUtc(LocalDateTime lastRunEndTimeUtc) { this.lastRunEndTimeUtc = lastRunEndTimeUtc; }
    public long getTotalIndicatorsUpdated() { return totalIndicatorsUpdated; }
    public void setTotalIndicatorsUpdated(long totalIndicatorsUpdated) { this.totalIndicatorsUpdated = totalIndicatorsUpdated; }
    public long getTotalCalendarEventsProcessed() { return totalCalendarEventsProcessed; }
    public void setTotalCalendarEventsProcessed(long totalCalendarEventsProcessed) { this.totalCalendarEventsProcessed = totalCalendarEventsProcessed; }
    public long getTotalEventsCrossLinked() { return totalEventsCrossLinked; }
    public void setTotalEventsCrossLinked(long totalEventsCrossLinked) { this.totalEventsCrossLinked = totalEventsCrossLinked; }
    public String getLastStatusMessage() { return lastStatusMessage; }
    public void setLastStatusMessage(String lastStatusMessage) { this.lastStatusMessage = lastStatusMessage; }
    public Map<String, Integer> getCurrencyIndicatorCounts() { return currencyIndicatorCounts; }
    public void setCurrencyIndicatorCounts(Map<String, Integer> currencyIndicatorCounts) { this.currencyIndicatorCounts = currencyIndicatorCounts; }
    public Map<String, String> getSyncFilesStatus() { return syncFilesStatus; }
    public void setSyncFilesStatus(Map<String, String> syncFilesStatus) { this.syncFilesStatus = syncFilesStatus; }
}
