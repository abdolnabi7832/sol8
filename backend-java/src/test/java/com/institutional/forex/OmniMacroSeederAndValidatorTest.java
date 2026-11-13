package com.institutional.forex;

import com.institutional.forex.registry.OmniIndicatorRegistry;
import com.institutional.forex.registry.OmniMacroSeederAndValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Omni Macro Seeder & Baseline Validator Tests (336 Indicators)")
class OmniMacroSeederAndValidatorTest {

    private static final List<String> CURRENCIES = List.of("USD", "EUR", "GBP", "JPY", "AUD", "CAD", "CHF", "NZD");

    @Test
    @DisplayName("Should verify master baseline matrix has all 336 indicators with non-null metrics and referencePeriods")
    void shouldVerifyAll336IndicatorsHaveCompleteBaselines() {
        OmniIndicatorRegistry registry = new OmniIndicatorRegistry();
        int count = 0;
        for (String ccy : CURRENCIES) {
            for (var spec : registry.getAllStandardIndicators()) {
                String ind = spec.canonicalName();
                OmniMacroSeederAndValidator.BaselineIndicatorRecord baseline =
                        OmniMacroSeederAndValidator.getBaseline(ccy, ind);

                assertNotNull(baseline, "Baseline missing for " + ccy + " - " + ind);
                assertNotNull(baseline.name());
                assertNotNull(baseline.unit());
                assertNotNull(baseline.frequency());
                assertNotNull(baseline.referencePeriod());
                assertTrue(baseline.stdDev() > 0, "StdDev must be positive for " + ccy + " - " + ind);
                assertTrue(baseline.weight() > 0, "Weight must be positive for " + ccy + " - " + ind);
                count++;
            }
        }

        assertEquals(336, count, "Exactly 336 baseline indicators (8 currencies x 42 indicators) must be defined");
    }

    @Test
    @DisplayName("Should verify USD specific realistic Nov 2026 baselines")
    void shouldVerifyUsdBaselines() {
        var cpi = OmniMacroSeederAndValidator.getBaseline("USD", "CPI YoY");
        assertNotNull(cpi);
        assertEquals(3.70, cpi.last());
        assertEquals(2.00, cpi.target());

        var rate = OmniMacroSeederAndValidator.getBaseline("USD", "Interest Rate");
        assertNotNull(rate);
        assertEquals(4.00, rate.last());

        var unemp = OmniMacroSeederAndValidator.getBaseline("USD", "Unemployment Rate");
        assertNotNull(unemp);
        assertEquals(4.20, unemp.last());
    }

    @Test
    @DisplayName("Should verify JPY and EUR specific realistic Nov 2026 baselines")
    void shouldVerifyJpyAndEurBaselines() {
        var jpyRate = OmniMacroSeederAndValidator.getBaseline("JPY", "Interest Rate");
        assertNotNull(jpyRate);
        assertEquals(1.25, jpyRate.last());

        var eurRate = OmniMacroSeederAndValidator.getBaseline("EUR", "Interest Rate");
        assertNotNull(eurRate);
        assertEquals(2.65, eurRate.last());
    }
}
