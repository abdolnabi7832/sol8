package com.quantfx.suite.engine;

import com.institutional.forex.model.entity.ActiveTradeEntity;
import com.institutional.forex.model.entity.PortfolioEntity;
import com.institutional.forex.repository.ActiveTradeRepository;
import com.institutional.forex.repository.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class MonteCarloRiskEngineTest {

    private PortfolioRepository portfolioRepo;
    private ActiveTradeRepository tradeRepo;
    private MonteCarloRiskEngine riskEngine;

    @BeforeEach
    public void setUp() {
        portfolioRepo = Mockito.mock(PortfolioRepository.class);
        tradeRepo = Mockito.mock(ActiveTradeRepository.class);
        riskEngine = new MonteCarloRiskEngine(portfolioRepo, tradeRepo);
    }

    @Test
    @DisplayName("Verify Fractional Kelly Criterion Mathematical Formulation")
    public void testKellyCriterion() {
        // W = 0.55 (55%), R = 2.0
        // Full Kelly = 0.55 - (1 - 0.55)/2.0 = 0.55 - 0.225 = 0.325 (32.5%)
        // Half Kelly = 16.25%
        // Quarter Kelly = 8.125%
        MonteCarloRiskEngine.KellyAnalysis kelly = riskEngine.calculateKellyCriterion(0.55, 2.0);

        assertEquals(32.5, kelly.fullKellyPercent(), 0.1);
        assertEquals(16.25, kelly.halfKellyPercent(), 0.1);
        assertEquals(8.12, kelly.quarterKellyPercent(), 0.1);

        // Negative edge strategy (W = 0.30, R = 1.0) -> Kelly = 0.0
        MonteCarloRiskEngine.KellyAnalysis badStrategy = riskEngine.calculateKellyCriterion(0.30, 1.0);
        assertEquals(0.0, badStrategy.fullKellyPercent(), 0.01);
    }

    @Test
    @DisplayName("Verify 1000-Path Monte Carlo Simulation Outputs")
    public void testMonteCarloSimulation() {
        MonteCarloRiskEngine.MonteCarloResult result = riskEngine.runMonteCarloSimulation(
                50000.0, 0.55, 2.0, 1.0, 15.0, 1000, 100);

        assertEquals(50000.0, result.initialBalance());
        assertEquals(1000, result.simulationsCount());
        assertEquals(100, result.tradesPerPath());

        assertTrue(result.medianFinalEquityP50() > 50000.0, "Positive expectancy strategy must have median equity > initial balance");
        assertTrue(result.bestCaseEquityP95() >= result.medianFinalEquityP50());
        assertTrue(result.medianFinalEquityP50() >= result.worstCaseEquityP5());
        assertTrue(result.probabilityOfRuinPercent() >= 0.0 && result.probabilityOfRuinPercent() <= 100.0);
        assertFalse(result.sampleEquityCurve().isEmpty());
    }

    @Test
    @DisplayName("Verify Portfolio Risk Snapshot, HHI and Toman Conversion")
    public void testPortfolioRiskSnapshot() {
        PortfolioEntity port = new PortfolioEntity();
        port.setAccountBalance(50000.0);
        port.setEquity(52000.0);
        port.setIrtExchangeRate(70000.0); // 70,000 Toman

        ActiveTradeEntity trade1 = new ActiveTradeEntity("T1", "EURUSD", "BUY", 1.0, 1.08, 1.075, 1.09, 500.0, 1.0);
        trade1.setUnrealizedPnlUsd(1200.0);

        ActiveTradeEntity trade2 = new ActiveTradeEntity("T2", "GBPJPY", "BUY", 1.0, 195.0, 194.0, 197.0, 500.0, 1.0);
        trade2.setUnrealizedPnlUsd(800.0);

        when(portfolioRepo.findFirstByOrderByUpdatedAtUtcDesc()).thenReturn(Optional.of(port));
        when(tradeRepo.findByStatus("OPEN")).thenReturn(List.of(trade1, trade2));

        MonteCarloRiskEngine.PortfolioRiskSnapshot snapshot = riskEngine.generatePortfolioRiskSnapshot();

        assertEquals(50000.0, snapshot.accountBalanceUsd());
        assertEquals(52000.0, snapshot.equityUsd()); // 50000 + 2000 PnL
        assertEquals(52000.0 * 70000.0, snapshot.equityIrtToman(), 100.0); // Toman conversion
        assertEquals(1000.0, snapshot.totalOpenRiskUsd(), 0.01);
        assertTrue(snapshot.hhiConcentrationIndex() > 0.0);
        assertNotNull(snapshot.diversificationStatus());
    }
}
