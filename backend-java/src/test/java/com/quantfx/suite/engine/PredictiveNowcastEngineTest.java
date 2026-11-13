package com.quantfx.suite.engine;

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
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class PredictiveNowcastEngineTest {

    private MacroIndicatorRepository macroRepo;
    private EconomicEventRepository eventRepo;
    private OmniIndicatorRegistry registry;
    private MacroSixPillarScorer scorer;
    private PredictiveNowcastEngine nowcastEngine;

    @BeforeEach
    public void setUp() {
        macroRepo = Mockito.mock(MacroIndicatorRepository.class);
        eventRepo = Mockito.mock(EconomicEventRepository.class);
        registry = new OmniIndicatorRegistry();
        scorer = Mockito.mock(MacroSixPillarScorer.class);
        nowcastEngine = new PredictiveNowcastEngine(macroRepo, eventRepo, registry, scorer);
    }

    @Test
    @DisplayName("Verify GDP Nowcasting using Leading PMIs and Retail Sales")
    public void testGdpNowcasting() {
        MacroIndicatorEntity gdp = new MacroIndicatorEntity("USD", 3, "GDP & Business", "GDP Growth YoY", 0.40, 2.0, Frequency.QUARTERLY, "%");
        gdp.setLastValue(2.0);
        gdp.setConsensusValue(2.1);
        gdp.setHistoricalStdDev(0.8);

        MacroIndicatorEntity pmi = new MacroIndicatorEntity("USD", 3, "GDP & Business", "Composite PMI", 0.20, 50.0, Frequency.MONTHLY, "Index");
        pmi.setLastValue(54.0); // +4.0 above neutral

        MacroIndicatorEntity retail = new MacroIndicatorEntity("USD", 6, "Consumer & Housing", "Retail Sales YoY", 0.30, 2.0, Frequency.MONTHLY, "%");
        retail.setLastValue(4.0); // Strong retail

        when(macroRepo.findByCurrencyAndIndicatorName("USD", "GDP Growth YoY")).thenReturn(Optional.of(gdp));
        when(macroRepo.findByCurrencyOrderByPillarNumberAscIndicatorNameAsc("USD")).thenReturn(List.of(gdp, pmi, retail));

        PredictiveNowcastEngine.NowcastPrediction prediction = nowcastEngine.nowcastIndicator("USD", "GDP Growth YoY");

        assertNotNull(prediction);
        assertTrue(prediction.modelPredictedValue() > 2.0, "Leading strong PMIs and Retail should boost nowcasted GDP above baseline 2.0%");
        assertEquals("USD", prediction.currency());
        assertNotNull(prediction.surpriseDirectionAlert());
    }

    @Test
    @DisplayName("Verify 3-Scenario Tree (Beat, Inline, Miss) for Upcoming Event")
    public void testThreeScenarioSimulation() {
        String eventId = "te_cad_cpi_upcoming";
        LocalDateTime eventTime = LocalDateTime.of(2026, 11, 15, 13, 30);
        EconomicEventEntity event = new EconomicEventEntity(
                eventId, "CAD", "ca", "CPI YoY", "2026-11-15", "13:30", eventTime, ImpactLevel.HIGH);
        event.setConsensus(2.5);
        event.setMappedStandardIndicator("CPI YoY");

        when(eventRepo.findByEventId(eventId)).thenReturn(Optional.of(event));

        MacroIndicatorEntity cpiEntity = new MacroIndicatorEntity("CAD", 2, "Prices", "CPI YoY", 0.40, 2.0, Frequency.MONTHLY, "%");
        cpiEntity.setLastValue(2.4);
        cpiEntity.setConsensusValue(2.5);
        cpiEntity.setHistoricalStdDev(0.4);
        when(macroRepo.findByCurrencyAndIndicatorName("CAD", "CPI YoY")).thenReturn(Optional.of(cpiEntity));

        // Mock Scorer responses for baseline, beat, and miss
        MacroSixPillarScorer.CurrencyMacroScore baseScore = new MacroSixPillarScorer.CurrencyMacroScore(
                "CAD", 1.5, 80.0, CurrencySpecificProfile.MacroRegime.GOLDILOCKS_GROWTH,
                CurrencySpecificProfile.ScoringMode.AUTO, 3.0, 3.0, 0.0, 0.4, 0.0, Map.of(), List.of(), LocalDateTime.now());

        MacroSixPillarScorer.CurrencyMacroScore beatScore = new MacroSixPillarScorer.CurrencyMacroScore(
                "CAD", 4.0, 85.0, CurrencySpecificProfile.MacroRegime.HAWKISH_EXPANSION,
                CurrencySpecificProfile.ScoringMode.HYBRID, 3.5, 3.0, 0.5, 1.0, 0.0, Map.of(), List.of(), LocalDateTime.now());

        MacroSixPillarScorer.CurrencyMacroScore missScore = new MacroSixPillarScorer.CurrencyMacroScore(
                "CAD", -2.0, 80.0, CurrencySpecificProfile.MacroRegime.DEFLATIONARY_RECESSION,
                CurrencySpecificProfile.ScoringMode.HYBRID, 2.0, 3.0, -1.0, -0.5, 0.0, Map.of(), List.of(), LocalDateTime.now());

        when(scorer.calculateCurrencyScore(eq("CAD"))).thenReturn(baseScore);
        when(scorer.calculateCurrencyScore(Mockito.argThat(s -> !"CAD".equals(s)))).thenReturn(new MacroSixPillarScorer.CurrencyMacroScore(
                "USD", -1.0, 75.0, CurrencySpecificProfile.MacroRegime.DEFLATIONARY_RECESSION,
                CurrencySpecificProfile.ScoringMode.AUTO, 2.0, 2.0, 0.0, 0.0, 0.0, Map.of(), List.of(), LocalDateTime.now()));
        when(scorer.calculateCurrencyScore(eq("CAD"), any(), eq(CurrencySpecificProfile.ScoringMode.HYBRID), any()))
                .thenReturn(beatScore)
                .thenReturn(baseScore)
                .thenReturn(missScore);

        PredictiveNowcastEngine.EventSimulationResult result = nowcastEngine.simulateUpcomingEvent(eventId);

        assertNotNull(result);
        assertEquals("BULLISH_BEAT", result.bullishBeatScenario().scenarioName());
        assertEquals("BASELINE_INLINE", result.baselineInlineScenario().scenarioName());
        assertEquals("BEARISH_MISS", result.bearishMissScenario().scenarioName());

        assertTrue(result.bullishBeatScenario().estimatedPipMovement() >= 15.0);
        assertTrue(result.bearishMissScenario().isReversalTriggered(), "Plunge from +1.5 to -2.0 should trigger reversal");
    }

    @Test
    @DisplayName("Verify 28-Pair Canonical Matrix Generation")
    public void testGenerate28Pairs() {
        Map<String, Double> scores = Map.of(
                "USD", 5.0,
                "EUR", 2.0,
                "GBP", 3.0,
                "JPY", -4.0,
                "AUD", 1.0,
                "CAD", 0.0,
                "CHF", -1.0,
                "NZD", -2.0
        );

        List<PredictiveNowcastEngine.SimulatedPairSignal> pairs = nowcastEngine.generate28Pairs(scores);
        assertEquals(28, pairs.size(), "8 G8 currencies must yield exactly 28 unique pairs");

        // Verify USDJPY spread = 5.0 - (-4.0) = +9.0 -> STRONG_BUY
        PredictiveNowcastEngine.SimulatedPairSignal usdjpy = pairs.stream()
                .filter(p -> p.pair().equals("USDJPY"))
                .findFirst().orElseThrow();

        assertEquals(9.0, usdjpy.netSpreadScore(), 0.01);
        assertEquals("STRONG_BUY", usdjpy.bias());
    }
}
