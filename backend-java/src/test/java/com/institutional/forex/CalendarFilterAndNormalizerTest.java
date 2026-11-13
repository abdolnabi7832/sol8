package com.institutional.forex;

import com.institutional.forex.engine.normalizer.CalendarFilterAndNormalizer;
import com.institutional.forex.model.entity.EconomicEventEntity;
import com.institutional.forex.model.enums.ImpactLevel;
import com.institutional.forex.registry.OmniIndicatorRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Calendar Filter and Normalizer Tests")
class CalendarFilterAndNormalizerTest {

    private CalendarFilterAndNormalizer normalizer;

    @BeforeEach
    void setUp() {
        OmniIndicatorRegistry registry = new OmniIndicatorRegistry();
        normalizer = new CalendarFilterAndNormalizer(registry);
    }

    @Test
    @DisplayName("Should strictly flag Daylight Saving Time shift and Bank Holidays as non-simulatable")
    void shouldRejectAdministrativeAndHolidayEvents() {
        assertFalse(normalizer.isQuantSimulatable("Daylight Saving Time Shift", ImpactLevel.LOW, null, null, null, null));
        assertFalse(normalizer.isQuantSimulatable("US Clocks Forward DST Shift", ImpactLevel.NONE, null, null, null, null));
        assertFalse(normalizer.isQuantSimulatable("French Bank Holiday", ImpactLevel.NONE, null, null, null, null));
        assertFalse(normalizer.isQuantSimulatable("German Market Holiday - Closed for Labor Day", ImpactLevel.LOW, null, null, null, null));
        assertFalse(normalizer.isQuantSimulatable("UK Bank Holiday", ImpactLevel.LOW, null, null, null, null));
    }

    @Test
    @DisplayName("Should strictly flag pure speeches without forecast numbers as non-simulatable")
    void shouldRejectSpeechesWithoutNumbers() {
        assertFalse(normalizer.isQuantSimulatable("Fed Chair Powell Speaks", ImpactLevel.HIGH, null, null, null, null));
        assertFalse(normalizer.isQuantSimulatable("ECB President Lagarde Press Conference", ImpactLevel.HIGH, null, null, null, null));
        assertFalse(normalizer.isQuantSimulatable("BOE Governor Bailey Testimony", ImpactLevel.HIGH, null, null, null, null));
    }

    @Test
    @DisplayName("Should qualify quantitative macroeconomic releases with numeric forecasts")
    void shouldAcceptQuantReleases() {
        assertTrue(normalizer.isQuantSimulatable("CPI YoY", ImpactLevel.HIGH, 3.2, 3.0, 3.0, "CPI YoY"));
        assertTrue(normalizer.isQuantSimulatable("Non Farm Payrolls", ImpactLevel.HIGH, 114.0, 140.0, 142.0, "Employment Change"));
        assertTrue(normalizer.isQuantSimulatable("GDP Growth Rate Annualized", ImpactLevel.HIGH, 2.8, 2.6, 2.7, "GDP Growth YoY"));
        assertTrue(normalizer.isQuantSimulatable("ISM Services PMI", ImpactLevel.HIGH, 52.8, 53.0, 53.2, "Services PMI"));
        assertTrue(normalizer.isQuantSimulatable("Unemployment Rate", ImpactLevel.HIGH, 4.2, 4.1, 4.1, "Unemployment Rate"));
    }

    @Test
    @DisplayName("Should classify and normalize EconomicEventEntity in-place")
    void shouldClassifyEntityInPlace() {
        EconomicEventEntity holiday = new EconomicEventEntity();
        holiday.setEventName("US Daylight Saving Time Shift");
        holiday.setImpact(ImpactLevel.NONE);
        normalizer.classifyAndNormalize(holiday);
        assertFalse(holiday.getIsQuantSimulatable());

        EconomicEventEntity cpi = new EconomicEventEntity();
        cpi.setEventName("Consumer Price Index (CPI) YoY");
        cpi.setImpact(ImpactLevel.HIGH);
        cpi.setPrevious(3.2);
        cpi.setConsensus(3.0);
        normalizer.classifyAndNormalize(cpi);
        assertTrue(cpi.getIsQuantSimulatable());
        assertEquals("CPI YoY", cpi.getMappedStandardIndicator());
    }
}
