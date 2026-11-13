package com.institutional.forex.engine.resolver;

import com.institutional.forex.model.entity.EconomicEventEntity;
import com.institutional.forex.model.entity.MacroIndicatorEntity;
import com.institutional.forex.registry.OmniIndicatorRegistry;
import com.institutional.forex.repository.EconomicEventRepository;
import com.institutional.forex.repository.MacroIndicatorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CrossLinkTimestampResolver: Eliminates approximate release dates by cross-referencing
 * calendar releases with the 20 institutional macro indicators.
 *
 * Automatically resolves and persists:
 * - exactReleaseTimestampUtc (Latest verified past release time in UTC)
 * - nextReleaseTimestampUtc (Earliest verified upcoming release time in UTC)
 * - consensusValue / forecastValue / referencePeriod
 */
@Service
public class CrossLinkTimestampResolver {

    private static final Logger log = LoggerFactory.getLogger(CrossLinkTimestampResolver.class);

    private static final Pattern PERIOD_PATTERN = Pattern.compile(
            "\\b(Q[1-4]|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec|Prelim|Final|Flash)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final MacroIndicatorRepository macroIndicatorRepository;
    private final EconomicEventRepository economicEventRepository;
    private final OmniIndicatorRegistry indicatorRegistry;

    public CrossLinkTimestampResolver(MacroIndicatorRepository macroIndicatorRepository,
                                      EconomicEventRepository economicEventRepository,
                                      OmniIndicatorRegistry indicatorRegistry) {
        this.macroIndicatorRepository = macroIndicatorRepository;
        this.economicEventRepository = economicEventRepository;
        this.indicatorRegistry = indicatorRegistry;
    }

    /**
     * Executes cross-linking across all 8 G8 currencies and their 20 macro indicators.
     *
     * @return Number of indicator entities enriched with exact timestamps.
     */
    @Transactional
    public int resolveAllCurrencies() {
        log.info("[CrossLink Resolver] Commencing cross-link timestamp resolution for all currencies...");
        int totalEnriched = 0;
        LocalDateTime nowUtc = LocalDateTime.now();

        for (String ccy : indicatorRegistry.getSupportedCurrencies()) {
            List<MacroIndicatorEntity> indicators = macroIndicatorRepository.findByCurrencyOrderByPillarNumberAscIndicatorNameAsc(ccy);

            for (MacroIndicatorEntity indicator : indicators) {
                boolean modified = resolveIndicatorTimestamps(indicator, nowUtc);
                if (modified) {
                    macroIndicatorRepository.save(indicator);
                    totalEnriched++;
                }
            }
        }

        log.info("[CrossLink Resolver Complete] Successfully resolved exact timestamps for {} macro indicators", totalEnriched);
        return totalEnriched;
    }

    /**
     * Resolves exact historical and upcoming release timestamps for a single indicator.
     */
    public boolean resolveIndicatorTimestamps(MacroIndicatorEntity indicator, LocalDateTime referenceTimeUtc) {
        String ccy = indicator.getCurrency();
        String indicatorName = indicator.getIndicatorName();
        boolean changed = false;

        // 1. Find upcoming releases: exact event in future
        List<EconomicEventEntity> upcomingEvents = economicEventRepository
                .findUpcomingByCurrencyAndIndicator(ccy, indicatorName, referenceTimeUtc);

        if (!upcomingEvents.isEmpty()) {
            EconomicEventEntity nextEvent = upcomingEvents.get(0);
            if (nextEvent.getExactEventTimestampUtc() != null) {
                if (!nextEvent.getExactEventTimestampUtc().equals(indicator.getNextReleaseTimestampUtc())) {
                    indicator.setNextReleaseTimestampUtc(nextEvent.getExactEventTimestampUtc());
                    changed = true;
                }
            }

            if (nextEvent.getConsensus() != null && !nextEvent.getConsensus().equals(indicator.getConsensusValue())) {
                indicator.setConsensusValue(nextEvent.getConsensus());
                changed = true;
            }
            if (nextEvent.getForecast() != null && !nextEvent.getForecast().equals(indicator.getForecastValue())) {
                indicator.setForecastValue(nextEvent.getForecast());
                changed = true;
            }

            // Extract reference period if not set
            if (indicator.getReferencePeriod() == null || indicator.getReferencePeriod().isEmpty()) {
                String period = extractReferencePeriod(nextEvent.getEventName());
                if (period != null) {
                    indicator.setReferencePeriod(period);
                    changed = true;
                }
            }
        }

        // 2. Find past releases: most recent past event
        List<EconomicEventEntity> pastEvents = economicEventRepository
                .findPastByCurrencyAndIndicator(ccy, indicatorName, referenceTimeUtc);

        if (!pastEvents.isEmpty()) {
            EconomicEventEntity latestPast = pastEvents.get(0);

            if (latestPast.getExactEventTimestampUtc() != null) {
                if (!latestPast.getExactEventTimestampUtc().equals(indicator.getExactReleaseTimestampUtc())) {
                    indicator.setExactReleaseTimestampUtc(latestPast.getExactEventTimestampUtc());
                    changed = true;
                }
            }

            // Update actual value if fresh from calendar
            if (latestPast.getActual() != null && (indicator.getLastValue() == null || !latestPast.getActual().equals(indicator.getLastValue()))) {
                if (indicator.getLastValue() != null) {
                    indicator.setPreviousValue(indicator.getLastValue());
                }
                indicator.setLastValue(latestPast.getActual());
                changed = true;
            }

            if (latestPast.getPrevious() != null && indicator.getPreviousValue() == null) {
                indicator.setPreviousValue(latestPast.getPrevious());
                changed = true;
            }

            if (indicator.getReferencePeriod() == null || indicator.getReferencePeriod().isEmpty()) {
                String period = extractReferencePeriod(latestPast.getEventName());
                if (period != null) {
                    indicator.setReferencePeriod(period);
                    changed = true;
                }
            }
        }

        if (changed) {
            indicator.setUpdatedAtUtc(LocalDateTime.now());
            log.debug("[CrossLinked] {} - {}: ExactRelease={}, NextRelease={}, Consensus={}, Forecast={}",
                    ccy, indicatorName,
                    indicator.getExactReleaseTimestampUtc(),
                    indicator.getNextReleaseTimestampUtc(),
                    indicator.getConsensusValue(),
                    indicator.getForecastValue());
        }

        return changed;
    }

    /**
     * Extracts reference period identifier (e.g. "Q3", "Oct", "Sep", "Prelim") from raw string.
     */
    private String extractReferencePeriod(String text) {
        if (text == null) return null;
        Matcher m = PERIOD_PATTERN.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
}
