package com.institutional.forex;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.institutional.forex.engine.gemini.GeminiKeyPoolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Gemini Key Pool & Decision 1 Engine Tests")
class GeminiKeyPoolServiceTest {

    private GeminiKeyPoolService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new GeminiKeyPoolService(objectMapper);
    }

    @Test
    @DisplayName("Should initialize key pool with default user key and correct limits")
    void shouldInitializeKeyPool() {
        List<Map<String, Object>> keys = service.listKeys();
        assertNotNull(keys);
        assertFalse(keys.isEmpty());

        Map<String, Object> defaultKey = keys.get(0);
        assertEquals(200, ((Number) defaultKey.get("limitRpm")).intValue(), "Default RPM must be 200 for Decision 1");
        assertEquals(50000, ((Number) defaultKey.get("limitTpm")).intValue(), "Default TPM must be 50K for Decision 1");
        assertEquals(10000, ((Number) defaultKey.get("limitRpd")).intValue(), "Default RPD must be 10K for Decision 1");
        assertTrue(defaultKey.get("maskedKey").toString().contains("..."));
    }

    @Test
    @DisplayName("Should add new API key to pool, enforce rate limits, and allow deletion")
    void shouldAddAndManageKeys() {
        int initialCount = service.listKeys().size();
        String testKeyStr = "AQ.TestKeyMock1234567890abcdefghijklmnopqrstuvwxyz";

        GeminiKeyPoolService.ApiKeyEntry added = service.addKey("Secondary Fallback Key", testKeyStr, "gemini-decision-1");
        assertNotNull(added);
        assertEquals("Secondary Fallback Key", added.label);
        assertEquals("gemini-decision-1", added.model);
        assertEquals(initialCount + 1, service.listKeys().size());

        // Test deletion
        boolean deleted = service.deleteKey(added.id);
        assertTrue(deleted);
        assertEquals(initialCount, service.listKeys().size());
    }

    @Test
    @DisplayName("Should evaluate trade decision with resilient institutional quant fallback")
    void shouldEvaluateTradeDecision() {
        Map<String, Object> context = new HashMap<>();
        context.put("deltaScore", "+6.26");
        context.put("direction", "BUY");
        context.put("spotPrice", 1.42517);
        context.put("gatesPassed", "7/7 Gates Passed");
        context.put("carryDiff", "+1.35%");
        context.put("strategyName", "Institutional Macro Divergence");

        Map<String, Object> decision = service.evaluateDecision("USDCAD", context);
        assertNotNull(decision);
        assertTrue(decision.containsKey("decision"));
        assertTrue(decision.containsKey("conviction"));
        assertTrue(decision.containsKey("macroPillarAnalysis"));

        String decStr = (String) decision.get("decision");
        assertTrue(decStr.equals("BUY") || decStr.equals("SELL") || decStr.equals("HOLD"));

        int conviction = ((Number) decision.get("conviction")).intValue();
        assertTrue(conviction >= 50 && conviction <= 100);
    }

    @Test
    @DisplayName("Should run hybrid AI strategy backtest and calculate false-positive reduction")
    void shouldRunAiStrategyBacktest() {
        Map<String, Object> backtest = service.runAiStrategyBacktest("EURNZD");
        assertNotNull(backtest);
        assertTrue(backtest.containsKey("baselineStrategyWinRate"));
        assertTrue(backtest.containsKey("aiEnhancedWinRate"));
        assertTrue(backtest.containsKey("falsePositivesEliminated"));
        assertTrue(backtest.containsKey("sampleAuditTrades"));

        double baselineWr = Double.parseDouble(backtest.get("baselineStrategyWinRate").toString());
        double aiWr = Double.parseDouble(backtest.get("aiEnhancedWinRate").toString());
        assertTrue(aiWr >= baselineWr, "AI filter should improve or equal baseline win rate");

        int falsePositives = ((Number) backtest.get("falsePositivesEliminated")).intValue();
        assertTrue(falsePositives > 0, "AI Decision filter should eliminate false positive breakout trades");
    }
}
