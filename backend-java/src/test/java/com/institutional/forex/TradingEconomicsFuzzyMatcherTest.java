package com.institutional.forex;

import com.institutional.forex.engine.scraper.TradingEconomicsScraperService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TradingEconomics Fuzzy Matcher Tests")
class TradingEconomicsFuzzyMatcherTest {

    private TradingEconomicsScraperService scraperService;

    @BeforeEach
    void setUp() {
        scraperService = new TradingEconomicsScraperService(null, null, null, null, null, null);
    }

    @Test
    @DisplayName("Should fuzzy match regional inflation and CPI strings")
    void shouldMatchInflationVariants() {
        assertEquals("CPI YoY", scraperService.matchIndicatorFuzzy("Inflation Rate", "USD"));
        assertEquals("CPI YoY", scraperService.matchIndicatorFuzzy("Consumer Price Index (CPI)", "EUR"));
        assertEquals("CPI YoY", scraperService.matchIndicatorFuzzy("Harmonised Inflation Rate YoY", "EUR"));
        assertEquals("CPI MoM", scraperService.matchIndicatorFuzzy("Inflation Rate MoM", "USD"));
        assertEquals("CPI MoM", scraperService.matchIndicatorFuzzy("CPI Month on Month", "GBP"));
        assertEquals("Core CPI", scraperService.matchIndicatorFuzzy("Core Inflation Rate YoY", "USD"));
        assertEquals("Core CPI", scraperService.matchIndicatorFuzzy("Core Consumer Prices", "CAD"));
        assertEquals("PPI YoY", scraperService.matchIndicatorFuzzy("Producer Price Index (PPI) YoY", "USD"));
    }

    @Test
    @DisplayName("Should fuzzy match Central Bank and Money indicators")
    void shouldMatchMoneyVariants() {
        assertEquals("Interest Rate", scraperService.matchIndicatorFuzzy("Deposit Facility Rate", "EUR"));
        assertEquals("Interest Rate", scraperService.matchIndicatorFuzzy("Overnight Target Rate", "CAD"));
        assertEquals("Interest Rate", scraperService.matchIndicatorFuzzy("Cash Rate Target", "AUD"));
        assertEquals("Interbank Rate", scraperService.matchIndicatorFuzzy("Effective Federal Funds Rate SOFR", "USD"));
        assertEquals("Interbank Rate", scraperService.matchIndicatorFuzzy("Euro Short-Term Rate Euribor", "EUR"));
        assertEquals("M1 MoM", scraperService.matchIndicatorFuzzy("Money Supply M1", "USD"));
        assertEquals("CB Balance Sheet YoY", scraperService.matchIndicatorFuzzy("Central Bank Total Balance Sheet", "USD"));
    }

    @Test
    @DisplayName("Should fuzzy match Labour and PMI indicators")
    void shouldMatchLabourAndPmis() {
        assertEquals("Unemployment Rate", scraperService.matchIndicatorFuzzy("Harmonised Unemployment Rate", "EUR"));
        assertEquals("Unemployment Rate", scraperService.matchIndicatorFuzzy("Jobless Rate", "GBP"));
        assertEquals("Employment Change", scraperService.matchIndicatorFuzzy("Non Farm Payrolls Change", "USD"));
        assertEquals("Employment Change", scraperService.matchIndicatorFuzzy("Claimant Count Change", "GBP"));
        assertEquals("Wage Growth YoY", scraperService.matchIndicatorFuzzy("Average Hourly Earnings YoY", "USD"));
        assertEquals("Manufacturing PMI", scraperService.matchIndicatorFuzzy("S&P Global Manufacturing PMI", "USD"));
        assertEquals("Services PMI", scraperService.matchIndicatorFuzzy("ISM Non-Manufacturing Services PMI", "USD"));
        assertEquals("Composite PMI", scraperService.matchIndicatorFuzzy("Composite PMI Flash", "EUR"));
    }
}
