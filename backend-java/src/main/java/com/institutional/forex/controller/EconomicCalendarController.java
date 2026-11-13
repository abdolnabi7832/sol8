package com.institutional.forex.controller;

import com.institutional.forex.model.dto.EconomicEventDto;
import com.institutional.forex.model.entity.EconomicEventEntity;
import com.institutional.forex.model.enums.ImpactLevel;
import com.institutional.forex.repository.EconomicEventRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * EconomicCalendarController: REST API endpoints delivering institutional calendar events,
 * exact release timestamps, and Tehran time (+03:30) conversions.
 */
@RestController
@RequestMapping("/api/v1/calendar")
public class EconomicCalendarController {

    private final EconomicEventRepository economicEventRepository;

    public EconomicCalendarController(EconomicEventRepository economicEventRepository) {
        this.economicEventRepository = economicEventRepository;
    }

    /**
     * Retrieves calendar events filtered by currency and optional impact level.
     */
    @GetMapping("/events/{currency}")
    public ResponseEntity<List<EconomicEventDto>> getEventsByCurrency(
            @PathVariable String currency,
            @RequestParam(required = false) ImpactLevel impact) {

        String ccy = currency.toUpperCase();
        List<EconomicEventEntity> entities;

        if (impact != null) {
            entities = economicEventRepository.findByCurrencyAndImpactOrderByExactEventTimestampUtcAsc(ccy, impact);
        } else {
            entities = economicEventRepository.findByCurrencyOrderByExactEventTimestampUtcAsc(ccy);
        }

        List<EconomicEventDto> dtos = entities.stream()
                .map(EconomicEventDto::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Retrieves calendar events within a specified UTC timestamp date range.
     */
    @GetMapping("/events")
    public ResponseEntity<List<EconomicEventDto>> getEventsByDateRange(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startUtc,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endUtc,
            @RequestParam(required = false) String currency) {

        LocalDateTime start = startUtc != null ? startUtc : LocalDateTime.now().minusDays(7);
        LocalDateTime end = endUtc != null ? endUtc : LocalDateTime.now().plusDays(14);

        List<EconomicEventEntity> entities;
        if (currency != null && !currency.isEmpty()) {
            entities = economicEventRepository.findByCurrencyAndExactEventTimestampUtcBetweenOrderByExactEventTimestampUtcAsc(
                    currency.toUpperCase(), start, end);
        } else {
            entities = economicEventRepository.findByExactEventTimestampUtcBetweenOrderByExactEventTimestampUtcAsc(start, end);
        }

        List<EconomicEventDto> dtos = entities.stream()
                .map(EconomicEventDto::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Retrieves upcoming high & medium impact releases.
     */
    @GetMapping("/upcoming")
    public ResponseEntity<List<EconomicEventDto>> getUpcomingHighImpactEvents(
            @RequestParam(defaultValue = "48") int hoursAhead) {

        LocalDateTime nowUtc = LocalDateTime.now();
        LocalDateTime targetEndUtc = nowUtc.plusHours(hoursAhead);

        List<EconomicEventEntity> entities = economicEventRepository
                .findByExactEventTimestampUtcBetweenOrderByExactEventTimestampUtcAsc(nowUtc, targetEndUtc);

        List<EconomicEventDto> dtos = entities.stream()
                .filter(e -> e.getImpact() == ImpactLevel.HIGH || e.getImpact() == ImpactLevel.MED)
                .map(EconomicEventDto::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }
}
