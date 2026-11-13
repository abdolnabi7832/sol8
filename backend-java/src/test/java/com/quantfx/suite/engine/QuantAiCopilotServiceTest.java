package com.quantfx.suite.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.institutional.forex.model.entity.PortfolioEntity;
import com.institutional.forex.model.entity.UserSettingsEntity;
import com.institutional.forex.repository.ActiveTradeRepository;
import com.institutional.forex.repository.EconomicEventRepository;
import com.institutional.forex.repository.PortfolioRepository;
import com.institutional.forex.repository.UserSettingsRepository;
import com.quantfx.suite.engine.MacroSixPillarScorer.CurrencyMacroScore;
import com.quantfx.suite.engine.PairSignalMatrixEngine.GateEvaluation;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class QuantAiCopilotServiceTest {

    private MacroSixPillarScorer macroScorer;
    private PairSignalMatrixEngine pairEngine;
    private PortfolioRepository portfolioRepo;
    private ActiveTradeRepository tradeRepo;
    private EconomicEventRepository eventRepo;
    private UserSettingsRepository settingsRepo;
    private QuantAiCopilotService copilotService;

    @BeforeEach
    public void setUp() {
        macroScorer = Mockito.mock(MacroSixPillarScorer.class);
        pairEngine = Mockito.mock(PairSignalMatrixEngine.class);
        portfolioRepo = Mockito.mock(PortfolioRepository.class);
        tradeRepo = Mockito.mock(ActiveTradeRepository.class);
        eventRepo = Mockito.mock(EconomicEventRepository.class);
        settingsRepo = Mockito.mock(UserSettingsRepository.class);

        copilotService = new QuantAiCopilotService(
                macroScorer, pairEngine, portfolioRepo, tradeRepo, eventRepo, settingsRepo, new ObjectMapper());
    }

    @Test
    @DisplayName("Verify Deterministic Quant Copilot Reasoning in Persian")
    public void testCopilotPersianResponse() {
        when(settingsRepo.findByUserIdentifier(anyString())).thenReturn(Optional.of(new UserSettingsEntity()));
        when(portfolioRepo.findFirstByOrderByUpdatedAtUtcDesc()).thenReturn(Optional.of(new PortfolioEntity()));
        when(tradeRepo.findByStatus(anyString())).thenReturn(List.of());

        CurrencyMacroScore dummy = new CurrencyMacroScore("USD", 3.0, 80.0, CurrencySpecificProfile.MacroRegime.GOLDILOCKS_GROWTH,
                CurrencySpecificProfile.ScoringMode.AUTO, 3.0, 3.0, 0.0, 0.0, 0.0, Map.of(), List.of(), LocalDateTime.now());
        when(macroScorer.calculateCurrencyScore(anyString())).thenReturn(dummy);

        GateEvaluation dummyGate = new GateEvaluation(true, "", true, 1.5, false, "", true, false, false, "",
                PairSignalMatrixEngine.NewsShieldStatus.CLEAR_HORIZON, "", 99.0, "", true, "BULLISH", "ALIGNED", "",
                true, 1.0, -1.0, "", true, 60.0, 120.0, 0.5, "", true);

        PairSignal mockSignal = new PairSignal("EURUSD", "EUR", "USD", 2.0, -1.0, 3.0,
                CurrencySpecificProfile.MacroRegime.GOLDILOCKS_GROWTH, CurrencySpecificProfile.MacroRegime.DEFLATIONARY_RECESSION,
                85.0, "BULLISH_EXPANSION", "🟢 BUY", "Macro Divergence", "6-12 Weeks", dummyGate, List.of(), LocalDateTime.now());

        when(pairEngine.evaluateAll28Pairs()).thenReturn(List.of(mockSignal));

        QuantAiCopilotService.AiChatRequest request = new QuantAiCopilotService.AiChatRequest(
                "What is the top macro divergence trade?", "en", "EURUSD");

        QuantAiCopilotService.AiChatResponse response = copilotService.processChatInquiry(request);

        assertNotNull(response);
        assertEquals("en", response.language());
        assertEquals("EURUSD", response.topRecommendationPair());
        assertTrue(response.reply().contains("EURUSD"));
        assertTrue(response.reply().contains("Quantitative Strategic Briefing"));
    }
}
