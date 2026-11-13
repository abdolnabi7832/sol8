package com.institutional.forex.engine.normalizer;

import com.institutional.forex.model.entity.EconomicEventEntity;
import com.institutional.forex.model.enums.ImpactLevel;
import com.institutional.forex.registry.OmniIndicatorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * CalendarFilterAndNormalizer: Normalizes economic calendar releases, guarantees valid
 * UTC timestamps and impact classifications (HIGH / MED / LOW, never NONE for macro releases),
 * and filters administrative noise.
 */
@Component
public class CalendarFilterAndNormalizer {

    private static final Logger log = LoggerFactory.getLogger(CalendarFilterAndNormalizer.class);

    private final OmniIndicatorRegistry indicatorRegistry;

    private static final List<String> NON_QUANT_KEYWORDS = List.of(
            "daylight saving", "time shift", "dst shift", "clocks forward", "clocks back",
            "bank holiday", "public holiday", "national holiday", "closed for", "market holiday",
            "market closed", "half day", "tentative", "parliamentary recess", "parliament recess"
    );

    private static final List<String> HIGH_IMPACT_KEYWORDS = List.of(
            "interest rate", "rate decision", "cpi", "inflation", "gdp", "non-farm", "nonfarm",
            "employment change", "unemployment rate", "retail sales", "manufacturing pmi",
            "services pmi", "composite pmi", "balance of trade", "trade balance", "fomc", "ecb", "boe", "boj"
    );

    private static final List<String> MED_IMPACT_KEYWORDS = List.of(
            "ppi", "producer price", "industrial production", "capacity utilization", "building permits",
            "housing", "house price", "consumer confidence", "business confidence", "zew", "ifo", "tankan",
            "current account", "exports", "imports", "wage", "earnings", "job vacancies", "jolts", "adp",
            "money supply", "m2", "m1", "leading index", "retail"
    );

    public CalendarFilterAndNormalizer(OmniIndicatorRegistry indicatorRegistry) {
        this.indicatorRegistry = indicatorRegistry;
    }

    public ImpactLevel inferInstitutionalImpact(String title, String mappedIndicator, ImpactLevel currentImpact) {
        if (currentImpact != null && currentImpact != ImpactLevel.NONE) {
            return currentImpact;
        }
        String combined = ((title != null ? title : "") + " " + (mappedIndicator != null ? mappedIndicator : "")).toLowerCase();
        for (String kw : HIGH_IMPACT_KEYWORDS) {
            if (combined.contains(kw)) {
                return ImpactLevel.HIGH;
            }
        }
        if (mappedIndicator != null && !mappedIndicator.isBlank()) {
            return ImpactLevel.MED;
        }
        for (String kw : MED_IMPACT_KEYWORDS) {
            if (combined.contains(kw)) {
                return ImpactLevel.MED;
            }
        }
        return ImpactLevel.LOW;
    }

    public boolean isQuantSimulatable(String title, ImpactLevel impact, Double previous,
                                      Double consensus, Double forecast, String mappedIndicator) {
        if (title == null || title.trim().isEmpty()) {
            return false;
        }
        String lower = title.toLowerCase().trim();
        for (String nonQuant : NON_QUANT_KEYWORDS) {
            if (lower.contains(nonQuant)) {
                return false;
            }
        }
        if (mappedIndicator != null && !mappedIndicator.trim().isEmpty()) {
            return true;
        }
        boolean hasNumericData = (previous != null || consensus != null || forecast != null);
        return hasNumericData && (impact == ImpactLevel.HIGH || impact == ImpactLevel.MED);
    }

    public void classifyAndNormalize(EconomicEventEntity entity) {
        if (entity == null) return;

        String title = entity.getEventName();
        String ccy = entity.getCurrency();

        if (entity.getMappedStandardIndicator() == null || entity.getMappedStandardIndicator().isEmpty()) {
            String mapped = indicatorRegistry.matchStandardIndicator(title, ccy);
            if (mapped != null) {
                entity.setMappedStandardIndicator(mapped);
            }
        }

        // Ensure ImpactLevel is never NONE for valid economic releases
        ImpactLevel resolvedImpact = inferInstitutionalImpact(title, entity.getMappedStandardIndicator(), entity.getImpact());
        entity.setImpact(resolvedImpact);

        // Ensure valid date and time strings
        if (entity.getEventDateStr() == null || entity.getEventDateStr().isBlank() || entity.getEventDateStr().contains("Invalid")) {
            if (entity.getExactEventTimestampUtc() != null) {
                entity.setEventDateStr(entity.getExactEventTimestampUtc().toLocalDate().toString());
            } else {
                entity.setEventDateStr(LocalDate.now().toString());
            }
        }
        if (entity.getTimeUtc() == null || entity.getTimeUtc().isBlank() || entity.getTimeUtc().contains("Invalid")) {
            if (entity.getExactEventTimestampUtc() != null) {
                entity.setTimeUtc(String.format("%02d:%02d", entity.getExactEventTimestampUtc().getHour(), entity.getExactEventTimestampUtc().getMinute()));
            } else {
                entity.setTimeUtc("13:30");
            }
        }
        if (entity.getExactEventTimestampUtc() == null) {
            try {
                LocalDate d = LocalDate.parse(entity.getEventDateStr());
                entity.setExactEventTimestampUtc(LocalDateTime.of(d, LocalTime.of(13, 30)));
            } catch (Exception e) {
                entity.setExactEventTimestampUtc(LocalDateTime.now().withHour(13).withMinute(30).withSecond(0).withNano(0));
            }
        }

        // Cross-populate consensus & forecast if one is missing
        if (entity.getConsensus() == null && entity.getForecast() != null) {
            entity.setConsensus(entity.getForecast());
        } else if (entity.getForecast() == null && entity.getConsensus() != null) {
            entity.setForecast(entity.getConsensus());
        }

        entity.recalculateTehranTime();

        boolean simulatable = isQuantSimulatable(
                title,
                entity.getImpact(),
                entity.getPrevious(),
                entity.getConsensus(),
                entity.getForecast(),
                entity.getMappedStandardIndicator()
        );
        entity.setIsQuantSimulatable(simulatable);
    }

    public List<EconomicEventEntity> filterSimulatable(List<EconomicEventEntity> events) {
        if (events == null) return Collections.emptyList();
        return events.stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsQuantSimulatable()))
                .filter(e -> e.getImpact() == ImpactLevel.HIGH || e.getImpact() == ImpactLevel.MED)
                .toList();
    }
}
