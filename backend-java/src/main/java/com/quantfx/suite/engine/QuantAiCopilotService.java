package com.quantfx.suite.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.institutional.forex.model.entity.ActiveTradeEntity;
import com.institutional.forex.model.entity.EconomicEventEntity;
import com.institutional.forex.model.entity.PortfolioEntity;
import com.institutional.forex.model.entity.UserSettingsEntity;
import com.institutional.forex.repository.*;
import com.quantfx.suite.engine.MacroSixPillarScorer.CurrencyMacroScore;
import com.quantfx.suite.engine.PairSignalMatrixEngine.PairSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * QuantAiCopilotService: Institutional AI reasoning assistant acting as a Senior Macro Quantitative
 * strategist. Feeds the entire real-time state of the 6-pillar matrix, 28 pairs, 7 gates, and
 * risk exposures into Gemini API with seamless offline fallback.
 */
@Service
public class QuantAiCopilotService {

    private static final Logger log = LoggerFactory.getLogger(QuantAiCopilotService.class);

    private final MacroSixPillarScorer macroScorer;
    private final PairSignalMatrixEngine pairEngine;
    private final PortfolioRepository portfolioRepository;
    private final ActiveTradeRepository activeTradeRepository;
    private final EconomicEventRepository economicEventRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public QuantAiCopilotService(MacroSixPillarScorer macroScorer,
                                PairSignalMatrixEngine pairEngine,
                                PortfolioRepository portfolioRepository,
                                ActiveTradeRepository activeTradeRepository,
                                EconomicEventRepository economicEventRepository,
                                UserSettingsRepository userSettingsRepository,
                                ObjectMapper objectMapper) {
        this.macroScorer = macroScorer;
        this.pairEngine = pairEngine;
        this.portfolioRepository = portfolioRepository;
        this.activeTradeRepository = activeTradeRepository;
        this.economicEventRepository = economicEventRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public record AiChatRequest(
            String message,
            String language, // "fa" or "en"
            String specificPair // Optional focus pair e.g. "EURUSD"
    ) {}

    public record AiChatResponse(
            String reply,
            String language,
            List<String> activeAlerts,
            String topRecommendationPair,
            long responseTimestampEpoch
    ) {}

    /**
     * Dispatches user inquiry to Gemini with rich institutional context or provides
     * deterministic quantitative fallback.
     */
    public AiChatResponse processChatInquiry(AiChatRequest request) {
        String lang = (request.language() != null && !request.language().isEmpty())
                ? request.language() : "en";

        // Build comprehensive snapshot of dashboard state
        String systemContext = assembleInstitutionalContext();
        List<String> activeAlerts = extractActiveAlerts();

        // Retrieve API key
        UserSettingsEntity settings = userSettingsRepository.findByUserIdentifier("DEFAULT_USER")
                .orElse(new UserSettingsEntity());
        String apiKey = settings.getGeminiApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = System.getenv("GEMINI_API_KEY");
        }

        String reply;
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            try {
                reply = callGeminiApi(apiKey, systemContext, request.message(), lang);
            } catch (Exception ex) {
                log.warn("[AI Copilot] Gemini API call failed: {}. Falling back to internal quant reasoning engine.", ex.getMessage());
                reply = generateDeterministicQuantResponse(request.message(), lang);
            }
        } else {
            reply = generateDeterministicQuantResponse(request.message(), lang);
        }

        // Determine top recommendation pair from matrix
        List<PairSignal> signals = pairEngine.evaluateAll28Pairs();
        String topPair = !signals.isEmpty() ? signals.get(0).pair() : "EURUSD";

        return new AiChatResponse(
                reply,
                lang,
                activeAlerts,
                topPair,
                System.currentTimeMillis()
        );
    }

    private String assembleInstitutionalContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== INSTITUTIONAL FOREX COMMAND CENTER REAL-TIME STATE ===\n");

        // 1. Currency Macro Scores & Regimes
        sb.append("\n[CURRENCY MACRO SCORES (Scale -10.0 to +10.0)]:\n");
        for (String ccy : List.of("USD", "EUR", "GBP", "JPY", "AUD", "CAD", "CHF", "NZD")) {
            CurrencyMacroScore s = macroScorer.calculateCurrencyScore(ccy);
            sb.append(String.format("- %s: Score=%.2f | Regime=%s | TaylorRate=%.2f%% (Actual=%.2f%%, Dev=%.2f%%) | Conf=%.1f%%\n",
                    ccy, s.finalScore(), s.regime().name(), s.taylorInterestRate(), s.actualInterestRate(), s.taylorDeviation(), s.confidenceScore()));
        }

        // 2. Top 5 Currency Pairs Signals
        sb.append("\n[TOP QUANTITATIVE PAIR SIGNALS (7-Gate Shield Evaluated)]:\n");
        List<PairSignal> pairs = pairEngine.evaluateAll28Pairs();
        int count = 0;
        for (PairSignal p : pairs) {
            if (count++ >= 5) break;
            sb.append(String.format("- %s: Signal=%s | DeltaScore=%.2f | Conf=%.1f%% | Gate4News=%s | Gate5Structure=%s\n",
                    p.pair(), p.signalClassification(), p.deltaScore(), p.overallConfidencePercent(),
                    p.gates().gate4NewsStatus().name(), p.gates().structureStatus()));
        }

        // 3. Portfolio & Open Risk
        PortfolioEntity port = portfolioRepository.findFirstByOrderByUpdatedAtUtcDesc().orElse(new PortfolioEntity());
        List<ActiveTradeEntity> openTrades = activeTradeRepository.findByStatus("OPEN");
        sb.append(String.format("\n[PORTFOLIO STATE]: Balance=$%.0f, Equity=$%.0f, OpenTradesCount=%d\n",
                port.getAccountBalance(), port.getEquity(), openTrades.size()));

        return sb.toString();
    }

    private List<String> extractActiveAlerts() {
        List<String> alerts = new ArrayList<>();
        List<PairSignal> pairs = pairEngine.evaluateAll28Pairs();
        for (PairSignal p : pairs) {
            if (p.gates().gate4NewsStatus() == PairSignalMatrixEngine.NewsShieldStatus.NEWS_LOCKED) {
                alerts.add("[ALERT] " + p.pair() + ": Gate 4 News Proximity Lock active - Execution restricted.");
            }
            if ("WAIT_FOR_BOS".equals(p.gates().structureStatus())) {
                alerts.add("[WARN] " + p.pair() + ": Macro divergence awaiting HTF BOS technical confirmation.");
            }
        }
        return alerts;
    }

    private String callGeminiApi(String apiKey, String context, String userQuery, String lang) throws Exception {
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;

        String systemPrompt = "You are the Senior Quantitative Macro Strategist of the Forex Intelligence Suite (Anton Kreil Methodology).\n"
                + "You have access to the full institutional state below. Answer strictly with quantitative rigor, citing specific pillar scores, macro regimes, Taylor deviations, and 7-gate shield statuses.\n"
                + "Target Language: " + (lang.equalsIgnoreCase("fa") ? "Institutional Quantitative English" : "English") + ".\n\n"
                + context;

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of(
                                "parts", List.of(
                                        Map.of("text", systemPrompt + "\n\nUser Question:\n" + userQuery)
                                )
                        )
                ),
                "generationConfig", Map.of(
                        "temperature", 0.3,
                        "maxOutputTokens", 1200
                )
        );

        String jsonPayload = objectMapper.writeValueAsString(body);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                return candidates.get(0).path("content").path("parts").get(0).path("text").asText();
            }
        }

        throw new IllegalStateException("Gemini API returned status " + resp.statusCode());
    }

    /**
     * Deterministic quantitative reasoning engine (Offline Copilot Mode).
     */
    private String generateDeterministicQuantResponse(String userQuery, String lang) {
        List<PairSignal> signals = pairEngine.evaluateAll28Pairs();
        PairSignal top = !signals.isEmpty() ? signals.get(0) : null;

        if (false) {
            return "";
        } else {
            if (top == null) {
                return "The system is currently synchronizing macro time-series data. Please check back shortly.";
            }

            return String.format(
                    "📊 **Quantitative Strategic Briefing (Anton Kreil Methodology):**\n\n" +
                    "• **Top Identified Edge:** Pair **%s** with signal **%s**.\n" +
                    "• **Fundamental Divergence:** Base (%s Score=%.2f, Regime=%s) vs Quote (%s Score=%.2f, Regime=%s).\n" +
                    "• **Net Divergence Spread (Delta Score):** %+.2f points.\n" +
                    "• **7-Gate Risk Shield Verification:**\n" +
                    "  - Gate 4 (News Shield): %s\n" +
                    "  - Gate 5 (MTF Structure): %s\n" +
                    "  - Gate 2 (Carry Spread): Differential %.2f%%\n" +
                    "• **Execution Parameter:** Recommended Stop Loss = %.1f pips, Suggested Sizing = %.2f lots.",
                    top.pair(), top.signalClassification(),
                    top.baseCurrency(), top.baseScore(), top.baseRegime().getEnTitle(),
                    top.quoteCurrency(), top.quoteScore(), top.quoteRegime().getEnTitle(),
                    top.deltaScore(),
                    top.gates().gate4NewsStatus().name(),
                    top.gates().structureStatus(),
                    top.gates().carryDifferential(),
                    top.gates().stopLossPips(),
                    top.gates().recommendedLotSize()
            );
        }
    }
}
