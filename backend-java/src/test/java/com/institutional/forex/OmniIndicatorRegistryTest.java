package com.institutional.forex;

import com.institutional.forex.registry.OmniIndicatorRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class OmniIndicatorRegistryTest {

    private OmniIndicatorRegistry registry;

    @BeforeEach
    public void setUp() {
        registry = new OmniIndicatorRegistry();
    }

    @Test
    @DisplayName("Verify 42 Institutional Indicators and 6 Pillars exist")
    public void testRegistryCompleteness() {
        assertEquals(42, registry.getAllStandardIndicators().size(), "Must contain 42 institutional macro indicators per currency");
        assertEquals(8, registry.getSupportedCurrencies().size(), "Must support exactly 8 G8 currencies");
        assertEquals(6, OmniIndicatorRegistry.PILLAR_MAP.size(), "Must have exactly 6 Anton Kreil pillars");
    }

    @Test
    @DisplayName("Verify Fuzzy and Alias Mapping for Complex Scraped Titles")
    public void testIndicatorMatching() {
        assertEquals("Interest Rate", registry.matchStandardIndicator("Fed Interest Rate Decision", "USD"));
        assertEquals("Interest Rate", registry.matchStandardIndicator("Deposit Facility Rate", "EUR"));
        assertEquals("Interbank Rate", registry.matchStandardIndicator("Effective Federal Funds Rate", "USD"));
        assertEquals("Interbank Rate", registry.matchStandardIndicator("EURIBOR 3M", "EUR"));

        assertEquals("CPI YoY", registry.matchStandardIndicator("US Consumer Price Index YoY", "USD"));
        assertEquals("CPI MoM", registry.matchStandardIndicator("Inflation Rate MoM", "USD"));
        assertEquals("Core CPI", registry.matchStandardIndicator("Core PCE Price Index Annual", "USD"));
        assertEquals("PPI YoY", registry.matchStandardIndicator("Producer Prices Change YoY", "EUR"));

        assertEquals("GDP Growth YoY", registry.matchStandardIndicator("GDP Annual Growth Rate", "USD"));
        assertEquals("GDP Growth YoY", registry.matchStandardIndicator("Gross Domestic Product MoM", "CAD"));
        assertEquals("Manufacturing PMI", registry.matchStandardIndicator("ISM Manufacturing PMI", "USD"));
        assertEquals("Manufacturing PMI", registry.matchStandardIndicator("HCOB Manufacturing PMI", "EUR"));
        assertEquals("Services PMI", registry.matchStandardIndicator("S&P Global Services PMI", "GBP"));
        assertEquals("Composite PMI", registry.matchStandardIndicator("Composite PMI Flash", "EUR"));

        assertEquals("Unemployment Rate", registry.matchStandardIndicator("Jobless Rate", "USD"));
        assertEquals("Employment Change", registry.matchStandardIndicator("Non Farm Payrolls", "USD"));
        assertEquals("Employment Change", registry.matchStandardIndicator("Claimant Count Change", "GBP"));
        assertEquals("Wage Growth YoY", registry.matchStandardIndicator("Average Hourly Earnings YoY", "USD"));

        assertEquals("Balance of Trade", registry.matchStandardIndicator("Trade Balance Goods", "USD"));
        assertEquals("Current Account to GDP", registry.matchStandardIndicator("Current Account % of GDP", "EUR"));

        assertEquals("Consumer Confidence", registry.matchStandardIndicator("Michigan Consumer Sentiment", "USD"));
        assertEquals("Retail Sales YoY", registry.matchStandardIndicator("Retail Sales Annual", "EUR"));
        assertEquals("Building Permits MoM", registry.matchStandardIndicator("Building Approvals MoM", "AUD"));
    }

    @Test
    @DisplayName("Verify Currency-Specific Target Differences (e.g. NFP 150K for USD vs 15K for others)")
    public void testCurrencySpecificTargets() {
        assertEquals(150.0, registry.getTargetRateForCurrency("Employment Change", "USD"), 0.001);
        assertEquals(15.0, registry.getTargetRateForCurrency("Employment Change", "CAD"), 0.001);
        assertEquals(15.0, registry.getTargetRateForCurrency("Employment Change", "GBP"), 0.001);

        assertEquals(2.0, registry.getTargetRateForCurrency("CPI YoY", "USD"), 0.001);
        assertEquals(50.0, registry.getTargetRateForCurrency("Manufacturing PMI", "USD"), 0.001);
    }
}
