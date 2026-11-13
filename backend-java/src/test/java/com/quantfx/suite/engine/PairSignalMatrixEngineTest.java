package com.quantfx.suite.engine;

import com.institutional.forex.model.entity.ActiveTradeEntity;
import com.institutional.forex.model.entity.EconomicEventEntity;
import com.institutional.forex.model.entity.MarketStructureEntity;
import com.institutional.forex.model.enums.ImpactLevel;
import com.institutional.forex.repository.ActiveTradeRepository;
import com.institutional.forex.repository.EconomicEventRepository;
import com.institutional.forex.repository.MarketStructureRepository;
import com.quantfx.suite.engine.CurrencySpecificProfile.MacroRegime;
import com.quantfx.suite.engine.CurrencySpecificProfile.ScoringMode;
import com.quantfx.suite.engine.MacroSixPillarScorer.CurrencyMacroScore;
import com.quantfx.suite.engine.PairSignalMatrixEngine.PairSignal;
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

public class PairSignalMatrixEngineTest {

    private MacroSixPillarScorer macroScorer;
    private EconomicEventRepository eventRepo;
    private MarketStructureRepository structureRepo;
    private ActiveTradeRepository tradeRepo;
    private PairSignalMatrixEngine engine;

    @BeforeEach
    public void setUp() {
        macroScorer = Mockito.mock(MacroSixPillarScorer.class);
        eventRepo = Mockito.mock(EconomicEventRepository.class);
        structureRepo = Mockito.mock(MarketStructureRepository.class);
        tradeRepo = Mockito.mock(ActiveTradeRepository.class);

        engine = new PairSignalMatrixEngine(macroScorer, eventRepo, structureRepo, tradeRepo);
    }

    @Test
    @DisplayName("Verify Gate 4 News Shield locks pair when High Impact news is within 12 hours")
    public void testGate4NewsLock() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 4, 12, 0);

        CurrencyMacroScore eurScore = new CurrencyMacroScore("EUR", 4.0, 85.0, MacroRegime.HAWKISH_EXPANSION, ScoringMode.AUTO, 3.5, 3.5, 0.0, 0.5, 0.5, Map.of(), List.of(), now);
        CurrencyMacroScore usdScore = new CurrencyMacroScore("USD", -2.5, 80.0, MacroRegime.DEFLATIONARY_RECESSION, ScoringMode.AUTO, 2.0, 2.0, 0.0, -0.5, -0.5, Map.of(), List.of(), now);

        // Upcoming High Impact news for USD in 4 hours
        EconomicEventEntity highImpactNfp = new EconomicEventEntity("te_usd_nfp", "USD", "us", "Non Farm Payrolls", "2026-11-04", "16:00", now.plusHours(4), ImpactLevel.HIGH);
        when(eventRepo.findByCurrencyAndImpactOrderByExactEventTimestampUtcAsc(eq("USD"), eq(ImpactLevel.HIGH)))
                .thenReturn(List.of(highImpactNfp));
        when(eventRepo.findByCurrencyAndImpactOrderByExactEventTimestampUtcAsc(eq("EUR"), eq(ImpactLevel.HIGH)))
                .thenReturn(List.of());

        PairSignal signal = engine.evaluateSinglePair("EURUSD", "EUR", "USD", eurScore, usdScore, now, 50000.0, 1.0, 2.0, Map.of());

        assertEquals(PairSignalMatrixEngine.NewsShieldStatus.NEWS_LOCKED, signal.gates().gate4NewsStatus());
        assertFalse(signal.gates().gate4NewsStatus() == PairSignalMatrixEngine.NewsShieldStatus.CLEAR_HORIZON);
        assertEquals("🔒 LOCKED (NEWS SHIELD)", signal.signalClassification());
    }

    @Test
    @DisplayName("Verify Gate 5 Structure Conflict sets signal to WAIT FOR BOS")
    public void testGate5StructureConflict() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 4, 12, 0);

        // Macro is strongly Bullish (+6.0 spread)
        CurrencyMacroScore eurScore = new CurrencyMacroScore("EUR", 4.0, 85.0, MacroRegime.HAWKISH_EXPANSION, ScoringMode.AUTO, 3.5, 3.5, 0.0, 0.5, 0.5, Map.of(), List.of(), now);
        CurrencyMacroScore usdScore = new CurrencyMacroScore("USD", -2.0, 80.0, MacroRegime.DEFLATIONARY_RECESSION, ScoringMode.AUTO, 2.0, 2.0, 0.0, -0.5, -0.5, Map.of(), List.of(), now);

        // But Daily HTF Technical Structure is Bearish
        MarketStructureEntity bearishStructure = new MarketStructureEntity("EURUSD", "DAILY", "BEARISH", "BOS", 1.0500, 1.0800, 1.0600);
        when(structureRepo.findByPairAndTimeframe(eq("EURUSD"), eq("DAILY"))).thenReturn(Optional.of(bearishStructure));
        when(eventRepo.findByCurrencyAndImpactOrderByExactEventTimestampUtcAsc(any(), any())).thenReturn(List.of());

        PairSignal signal = engine.evaluateSinglePair("EURUSD", "EUR", "USD", eurScore, usdScore, now, 50000.0, 1.0, 2.0, Map.of());

        assertFalse(signal.gates().gate5StructurePassed());
        assertEquals("WAIT_FOR_BOS", signal.gates().structureStatus());
        assertEquals("⏳ WAIT FOR BOS (STRUCTURE CONFLICT)", signal.signalClassification());
    }

    @Test
    @DisplayName("Verify Ultra-Safe Classification when all 7 gates pass with conviction")
    public void testUltraSafeClassification() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 4, 12, 0);

        CurrencyMacroScore gbpScore = new CurrencyMacroScore("GBP", 4.5, 88.0, MacroRegime.HAWKISH_EXPANSION, ScoringMode.AUTO, 4.0, 4.0, 0.0, 0.5, 0.5, Map.of(), List.of(), now);
        CurrencyMacroScore jpyScore = new CurrencyMacroScore("JPY", -3.0, 85.0, MacroRegime.DEFLATIONARY_RECESSION, ScoringMode.AUTO, 0.25, 0.25, 0.0, -0.5, -0.5, Map.of(), List.of(), now);

        MarketStructureEntity bullishStructure = new MarketStructureEntity("GBPJPY", "DAILY", "BULLISH", "BOS", 190.0, 198.0, 195.0);
        when(structureRepo.findByPairAndTimeframe(eq("GBPJPY"), eq("DAILY"))).thenReturn(Optional.of(bullishStructure));
        when(eventRepo.findByCurrencyAndImpactOrderByExactEventTimestampUtcAsc(any(), any())).thenReturn(List.of());

        PairSignal signal = engine.evaluateSinglePair("GBPJPY", "GBP", "JPY", gbpScore, jpyScore, now, 50000.0, 1.0, 2.0, Map.of());

        assertTrue(signal.gates().allSevenGatesPassed());
        assertEquals("💎 ULTRA-SAFE BUY", signal.signalClassification());
        assertTrue(signal.deltaScore() >= 5.0);
    }
}
