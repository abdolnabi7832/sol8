package com.institutional.forex;

import com.institutional.forex.engine.resolver.CrossLinkTimestampResolver;
import com.institutional.forex.model.entity.EconomicEventEntity;
import com.institutional.forex.model.entity.MacroIndicatorEntity;
import com.institutional.forex.model.enums.Frequency;
import com.institutional.forex.model.enums.ImpactLevel;
import com.institutional.forex.registry.OmniIndicatorRegistry;
import com.institutional.forex.repository.EconomicEventRepository;
import com.institutional.forex.repository.MacroIndicatorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class CrossLinkTimestampResolverTest {

    private MacroIndicatorRepository macroRepo;
    private EconomicEventRepository eventRepo;
    private OmniIndicatorRegistry registry;
    private CrossLinkTimestampResolver resolver;

    @BeforeEach
    public void setUp() {
        macroRepo = Mockito.mock(MacroIndicatorRepository.class);
        eventRepo = Mockito.mock(EconomicEventRepository.class);
        registry = new OmniIndicatorRegistry();
        resolver = new CrossLinkTimestampResolver(macroRepo, eventRepo, registry);
    }

    @Test
    @DisplayName("Verify Exact Timestamp Resolution from Past and Future Events")
    public void testTimestampCrossLinking() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 4, 12, 0);

        MacroIndicatorEntity indicator = new MacroIndicatorEntity(
                "USD", 2, "Prices", "CPI YoY", 0.40, 2.0, Frequency.MONTHLY, "%");

        LocalDateTime pastRelease = LocalDateTime.of(2026, 10, 15, 12, 30);
        EconomicEventEntity pastEvent = new EconomicEventEntity(
                "te_usd_cpi_past", "USD", "us", "CPI YoY (Sep)", "2026-10-15", "12:30", pastRelease, ImpactLevel.HIGH);
        pastEvent.setActual(2.4);
        pastEvent.setPrevious(2.5);

        LocalDateTime nextRelease = LocalDateTime.of(2026, 11, 14, 13, 30);
        EconomicEventEntity nextEvent = new EconomicEventEntity(
                "te_usd_cpi_next", "USD", "us", "CPI YoY (Oct)", "2026-11-14", "13:30", nextRelease, ImpactLevel.HIGH);
        nextEvent.setConsensus(2.3);
        nextEvent.setForecast(2.3);

        when(eventRepo.findPastByCurrencyAndIndicator(eq("USD"), eq("CPI YoY"), any(LocalDateTime.class)))
                .thenReturn(List.of(pastEvent));
        when(eventRepo.findUpcomingByCurrencyAndIndicator(eq("USD"), eq("CPI YoY"), any(LocalDateTime.class)))
                .thenReturn(List.of(nextEvent));

        boolean modified = resolver.resolveIndicatorTimestamps(indicator, now);

        assertTrue(modified);
        assertEquals(pastRelease, indicator.getExactReleaseTimestampUtc());
        assertEquals(nextRelease, indicator.getNextReleaseTimestampUtc());
        assertEquals(2.4, indicator.getLastValue());
        assertEquals(2.5, indicator.getPreviousValue());
        assertEquals(2.3, indicator.getConsensusValue());
        assertEquals(2.3, indicator.getForecastValue());
        assertNotNull(indicator.getReferencePeriod());
    }
}
