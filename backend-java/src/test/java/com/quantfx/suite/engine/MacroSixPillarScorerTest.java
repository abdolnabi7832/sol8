package com.quantfx.suite.engine;

import com.institutional.forex.model.entity.MacroIndicatorEntity;
import com.institutional.forex.model.enums.Frequency;
import com.institutional.forex.registry.OmniIndicatorRegistry;
import com.institutional.forex.repository.MacroIndicatorRepository;
import com.quantfx.suite.engine.CurrencySpecificProfile.MacroRegime;
import com.quantfx.suite.engine.CurrencySpecificProfile.ScoringMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class MacroSixPillarScorerTest {

    private MacroIndicatorRepository macroRepo;
    private OmniIndicatorRegistry registry;
    private TimeDecayCalculator decayCalculator;
    private CurrencySpecificProfile profile;
    private MacroSixPillarScorer scorer;

    @BeforeEach
    public void setUp() {
        macroRepo = Mockito.mock(MacroIndicatorRepository.class);
        registry = new OmniIndicatorRegistry();
        decayCalculator = new TimeDecayCalculator();
        profile = new CurrencySpecificProfile();
        scorer = new MacroSixPillarScorer(macroRepo, registry, decayCalculator, profile);
    }

    private List<MacroIndicatorEntity> createMockIndicators(String ccy, double cpi, double gdp, double pmi, double unemp) {
        List<MacroIndicatorEntity> list = new ArrayList<>();
        LocalDateTime refTime = LocalDateTime.of(2026, 11, 4, 12, 0);

        // Core CPI
        MacroIndicatorEntity cpiEntity = new MacroIndicatorEntity(ccy, 2, "Prices", "Core CPI", 0.40, 2.0, Frequency.MONTHLY, "%");
        cpiEntity.setLastValue(cpi);
        cpiEntity.setPreviousValue(cpi - 0.1);
        cpiEntity.setHistoricalStdDev(0.5);
        cpiEntity.setExactReleaseTimestampUtc(refTime.minusDays(5));
        list.add(cpiEntity);

        // GDP Growth YoY
        MacroIndicatorEntity gdpEntity = new MacroIndicatorEntity(ccy, 3, "GDP & Business", "GDP Growth YoY", 0.40, 2.0, Frequency.QUARTERLY, "%");
        gdpEntity.setLastValue(gdp);
        gdpEntity.setPreviousValue(gdp - 0.2);
        gdpEntity.setHistoricalStdDev(1.0);
        gdpEntity.setExactReleaseTimestampUtc(refTime.minusDays(20));
        list.add(gdpEntity);

        // Composite PMI
        MacroIndicatorEntity pmiEntity = new MacroIndicatorEntity(ccy, 3, "GDP & Business", "Composite PMI", 0.20, 50.0, Frequency.MONTHLY, "Index");
        pmiEntity.setLastValue(pmi);
        pmiEntity.setHistoricalStdDev(2.0);
        pmiEntity.setExactReleaseTimestampUtc(refTime.minusDays(2));
        list.add(pmiEntity);

        // Unemployment Rate (Reverse Direction!)
        MacroIndicatorEntity unempEntity = new MacroIndicatorEntity(ccy, 4, "Labour", "Unemployment Rate", 0.40, 4.0, Frequency.MONTHLY, "%");
        unempEntity.setLastValue(unemp);
        unempEntity.setPreviousValue(unemp + 0.2);
        unempEntity.setHistoricalStdDev(0.4);
        unempEntity.setExactReleaseTimestampUtc(refTime.minusDays(4));
        list.add(unempEntity);

        // Interest Rate
        MacroIndicatorEntity rateEntity = new MacroIndicatorEntity(ccy, 1, "Money", "Interest Rate", 0.40, 2.0, Frequency.MONTHLY, "%");
        rateEntity.setLastValue(3.5);
        rateEntity.setHistoricalStdDev(0.5);
        rateEntity.setExactReleaseTimestampUtc(refTime.minusDays(15));
        list.add(rateEntity);

        return list;
    }

    @Test
    @DisplayName("Verify Hawkish Expansion Regime and Macro Scoring Range")
    public void testHawkishExpansion() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 4, 12, 0);
        // GDP = 2.8 (> 2.0 target), PMI = 53.5 (>= 50.5), CPI = 3.2 (> 2.0 target) -> HAWKISH_EXPANSION
        List<MacroIndicatorEntity> mockEntities = createMockIndicators("USD", 3.2, 2.8, 53.5, 3.6);
        when(macroRepo.findByCurrencyOrderByPillarNumberAscIndicatorNameAsc(eq("USD"))).thenReturn(mockEntities);

        MacroSixPillarScorer.CurrencyMacroScore score = scorer.calculateCurrencyScore("USD", now, ScoringMode.AUTO, Map.of());

        assertEquals(MacroRegime.HAWKISH_EXPANSION, score.regime());
        assertTrue(score.finalScore() > 0.0, "Score should be bullish in Hawkish Expansion");
        assertTrue(score.finalScore() <= 10.0, "Score must be clamped to +10.0");
        assertTrue(score.confidenceScore() >= 10.0 && score.confidenceScore() <= 100.0);
    }

    @Test
    @DisplayName("Verify Stagflation Trap Regime Penalty and Inverse Inflation Sign")
    public void testStagflationTrapPenalty() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 4, 12, 0);
        // GDP = 1.0 (Output gap = 1.0 - 2.0 = -1.0 < -0.2), PMI = 47.0 (< 48.5), CPI = 3.8 (Inflation gap = 1.8 > 0.5) -> STAGFLATION_TRAP
        List<MacroIndicatorEntity> mockEntities = createMockIndicators("GBP", 3.8, 1.0, 47.0, 5.2);
        when(macroRepo.findByCurrencyOrderByPillarNumberAscIndicatorNameAsc(eq("GBP"))).thenReturn(mockEntities);

        MacroSixPillarScorer.CurrencyMacroScore score = scorer.calculateCurrencyScore("GBP", now, ScoringMode.AUTO, Map.of());

        assertEquals(MacroRegime.STAGFLATION_TRAP, score.regime());
        assertTrue(score.finalScore() < 0.0, "Score must be penalized and negative in Stagflation Trap");
        assertTrue(score.auditLogs().stream().anyMatch(log -> log.contains("STAGFLATION_TRAP")));
    }

    @Test
    @DisplayName("Verify Reverse Direction Rule for Unemployment Rate")
    public void testUnemploymentRateDirection() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 4, 12, 0);
        // Low unemployment (3.5% vs 4.0% target) is positive for currency!
        List<MacroIndicatorEntity> mockEntities = createMockIndicators("USD", 2.0, 2.0, 50.0, 3.5);
        when(macroRepo.findByCurrencyOrderByPillarNumberAscIndicatorNameAsc(eq("USD"))).thenReturn(mockEntities);

        MacroSixPillarScorer.CurrencyMacroScore score = scorer.calculateCurrencyScore("USD", now, ScoringMode.AUTO, Map.of());

        MacroSixPillarScorer.IndicatorScoreDetail unempDetail = score.pillarScores().get(4).indicators().stream()
                .filter(i -> i.indicatorName().equals("Unemployment Rate"))
                .findFirst().orElseThrow();

        assertTrue(unempDetail.zScoreLevel() > 0, "Lower unemployment rate must yield positive Z-score");
    }
}
