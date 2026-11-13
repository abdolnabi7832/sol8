package com.institutional.forex.model.dto;

import com.institutional.forex.model.entity.EconomicEventEntity;
import com.institutional.forex.model.enums.ImpactLevel;
import java.time.LocalDateTime;

/**
 * Economic Event DTO for API serialization and WebSocket streaming.
 */
public class EconomicEventDto {

    private Long id;
    private String eventId;
    private String currency;
    private String countryCode;
    private String eventName;
    private String mappedStandardIndicator;
    private String eventDateStr;
    private String timeUtc;
    private String timeTehran;
    private LocalDateTime exactEventTimestampUtc;
    private ImpactLevel impact;
    private Double actual;
    private Double previous;
    private Double consensus;
    private Double forecast;
    private Double surpriseStdDev;
    private Boolean isQuantSimulatable;
    private LocalDateTime createdAtUtc;

    public EconomicEventDto() {}

    public static EconomicEventDto fromEntity(EconomicEventEntity entity) {
        if (entity == null) return null;
        EconomicEventDto dto = new EconomicEventDto();
        dto.setId(entity.getId());
        dto.setEventId(entity.getEventId());
        dto.setCurrency(entity.getCurrency());
        dto.setCountryCode(entity.getCountryCode());
        dto.setEventName(entity.getEventName());
        dto.setMappedStandardIndicator(entity.getMappedStandardIndicator());
        dto.setEventDateStr(entity.getEventDateStr());
        dto.setTimeUtc(entity.getTimeUtc());
        dto.setTimeTehran(entity.getTimeTehran());
        dto.setExactEventTimestampUtc(entity.getExactEventTimestampUtc());
        dto.setImpact(entity.getImpact());
        dto.setActual(entity.getActual());
        dto.setPrevious(entity.getPrevious());
        dto.setConsensus(entity.getConsensus());
        dto.setForecast(entity.getForecast());
        dto.setSurpriseStdDev(entity.getSurpriseStdDev());
        dto.setIsQuantSimulatable(entity.getIsQuantSimulatable());
        dto.setCreatedAtUtc(entity.getCreatedAtUtc());
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }
    public String getMappedStandardIndicator() { return mappedStandardIndicator; }
    public void setMappedStandardIndicator(String mappedStandardIndicator) { this.mappedStandardIndicator = mappedStandardIndicator; }
    public String getEventDateStr() { return eventDateStr; }
    public void setEventDateStr(String eventDateStr) { this.eventDateStr = eventDateStr; }
    public String getTimeUtc() { return timeUtc; }
    public void setTimeUtc(String timeUtc) { this.timeUtc = timeUtc; }
    public String getTimeTehran() { return timeTehran; }
    public void setTimeTehran(String timeTehran) { this.timeTehran = timeTehran; }
    public LocalDateTime getExactEventTimestampUtc() { return exactEventTimestampUtc; }
    public void setExactEventTimestampUtc(LocalDateTime exactEventTimestampUtc) { this.exactEventTimestampUtc = exactEventTimestampUtc; }
    public ImpactLevel getImpact() { return impact; }
    public void setImpact(ImpactLevel impact) { this.impact = impact; }
    public Double getActual() { return actual; }
    public void setActual(Double actual) { this.actual = actual; }
    public Double getPrevious() { return previous; }
    public void setPrevious(Double previous) { this.previous = previous; }
    public Double getConsensus() { return consensus; }
    public void setConsensus(Double consensus) { this.consensus = consensus; }
    public Double getForecast() { return forecast; }
    public void setForecast(Double forecast) { this.forecast = forecast; }
    public Double getSurpriseStdDev() { return surpriseStdDev; }
    public void setSurpriseStdDev(Double surpriseStdDev) { this.surpriseStdDev = surpriseStdDev; }
    public Boolean getIsQuantSimulatable() { return isQuantSimulatable != null ? isQuantSimulatable : true; }
    public void setIsQuantSimulatable(Boolean isQuantSimulatable) { this.isQuantSimulatable = isQuantSimulatable; }
    public LocalDateTime getCreatedAtUtc() { return createdAtUtc; }
    public void setCreatedAtUtc(LocalDateTime createdAtUtc) { this.createdAtUtc = createdAtUtc; }
}
