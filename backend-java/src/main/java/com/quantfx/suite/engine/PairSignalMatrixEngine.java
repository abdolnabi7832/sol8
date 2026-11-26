package com.quantfx.suite.engine;

import com.institutional.forex.model.entity.ActiveTradeEntity;
import com.institutional.forex.model.entity.EconomicEventEntity;
import com.institutional.forex.model.entity.MarketStructureEntity;
import com.institutional.forex.model.enums.ImpactLevel;
import com.institutional.forex.repository.ActiveTradeRepository;
import com.institutional.forex.repository.EconomicEventRepository;
import com.institutional.forex.repository.MarketStructureRepository;
import com.quantfx.suite.engine.CurrencySpecificProfile.MacroRegime;
import com.quantfx.suite.engine.MacroSixPillarScorer.CurrencyMacroScore;
import com.quantfx.suite.engine.MacroSixPillarScorer.IndicatorScoreDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * PairSignalMatrixEngine v3.0: Institutional signal generator evaluating all 28 canonical
 * currency pairs through Anton Kreil's 7-Layer Risk Shield, with exact quote-currency
 * pip values, dynamic ATR-14 stop geometry, conviction-scaled R:R, and signal origin vintage.
 */
@Service
public class PairSignalMatrixEngine {

    private static final Logger log = LoggerFactory.getLogger(PairSignalMatrixEngine.class);

    private final MacroSixPillarScorer macroScorer;
    private final EconomicEventRepository economicEventRepository;
    private final MarketStructureRepository marketStructureRepository;
    private final ActiveTradeRepository activeTradeRepository;

    public static final List<String> CANONICAL_28_PAIRS = List.of(
            "EURUSD", "GBPUSD", "AUDUSD", "NZDUSD", "USDJPY", "USDCHF", "USDCAD",
            "EURGBP", "EURJPY", "EURCHF", "EURAUD", "EURNZD", "EURCAD",
            "GBPJPY", "GBPCHF", "GBPAUD", "GBPNZD", "GBPCAD",
            "AUDJPY", "AUDNZD", "AUDCAD", "AUDCHF",
            "NZDJPY", "NZDCHF", "NZDCAD",
            "CADJPY", "CADCHF", "CHFJPY"
    );

    private static final Map<String, Double> PAIR_DAILY_ATR_PIPS = Map.ofEntries(
            Map.entry("EURUSD", 72.0), Map.entry("GBPUSD", 92.0), Map.entry("AUDUSD", 64.0),
            Map.entry("NZDUSD", 62.0), Map.entry("USDJPY", 118.0), Map.entry("USDCHF", 62.0),
            Map.entry("USDCAD", 68.0), Map.entry("EURGBP", 48.0), Map.entry("EURJPY", 126.0),
            Map.entry("EURCHF", 56.0), Map.entry("EURAUD", 108.0), Map.entry("EURNZD", 124.0),
            Map.entry("EURCAD", 88.0), Map.entry("GBPJPY", 148.0), Map.entry("GBPCHF", 86.0),
            Map.entry("GBPAUD", 132.0), Map.entry("GBPNZD", 152.0), Map.entry("GBPCAD", 112.0),
            Map.entry("AUDJPY", 98.0), Map.entry("AUDNZD", 58.0), Map.entry("AUDCAD", 64.0),
            Map.entry("AUDCHF", 58.0), Map.entry("NZDJPY", 92.0), Map.entry("NZDCHF", 54.0),
            Map.entry("NZDCAD", 62.0), Map.entry("CADJPY", 94.0), Map.entry("CADCHF", 56.0),
            Map.entry("CHFJPY", 134.0)
    );

    // Exact Pip Value in USD per 1.00 Standard Lot (100,000 units) by Quote Currency (Nov 2026 spot rates)
    private static final Map<String, Double> QUOTE_CCY_PIP_VALUE_USD = Map.of(
            "USD", 10.00,
            "JPY", 6.32,   // 1000 / 158.173
            "CAD", 7.17,   // 10 / 1.39545
            "CHF", 12.26,  // 10 / 0.81557
            "GBP", 13.47,  // 10 * 1.34747
            "AUD", 7.01,   // 10 * 0.70146
            "NZD", 5.64    // 10 * 0.56426
    );

    public PairSignalMatrixEngine(MacroSixPillarScorer macroScorer,
                                 EconomicEventRepository economicEventRepository,
                                 MarketStructureRepository marketStructureRepository,
                                 ActiveTradeRepository activeTradeRepository) {
        this.macroScorer = macroScorer;
        this.economicEventRepository = economicEventRepository;
        this.marketStructureRepository = marketStructureRepository;
        this.activeTradeRepository = activeTradeRepository;
    }

    public enum NewsShieldStatus {
        NEWS_LOCKED("🔴 NEWS_LOCKED (Trading Restricted)", 0.0),
        PRE_NEWS_CAUTION("🟡 PRE_NEWS_CAUTION (50% Size Reduction)", 0.50),
        CLEAR_HORIZON("🟢 CLEAR_HORIZON (Unrestricted Execution)", 1.0);

        private final String label;
        private final double sizeMultiplier;
        NewsShieldStatus(String label, double sizeMultiplier) { this.label = label; this.sizeMultiplier = sizeMultiplier; }
        public String getLabel() { return label; }
        public double getSizeMultiplier() { return sizeMultiplier; }
    }

    public record GateEvaluation(
            boolean gate1MacroDivergencePassed,
            String gate1Description,
            boolean gate2CarryPassed,
            double carryDifferential,
            boolean severeCarryDrag,
            String gate2Description,
            boolean gate3DataFreshnessPassed,
            boolean staleDataWarning,
            boolean freshCatalystActive,
            String gate3Description,
            NewsShieldStatus gate4NewsStatus,
            String nearestHighImpactEventTitle,
            double hoursToNearestNews,
            String gate4Description,
            boolean gate5StructurePassed,
            String technicalTrend,
            String structureStatus,
            String gate5Description,
            boolean gate6ExposurePassed,
            double baseCurrencyNetRiskPercent,
            double quoteCurrencyNetRiskPercent,
            String gate6Description,
            boolean gate7ExecutionPassed,
            double stopLossPips,
            double takeProfitPips,
            double recommendedLotSize,
            String gate7Description,
            boolean allSevenGatesPassed
    ) {}

    public record PairSignal(
            String pair,
            String baseCurrency,
            String quoteCurrency,
            double baseScore,
            double quoteScore,
            double deltaScore,
            MacroRegime baseRegime,
            MacroRegime quoteRegime,
            double overallConfidencePercent,
            String signalClassification,
            String direction,
            String catalystEvent,
            String horizonWeeks,
            GateEvaluation gates,
            List<String> auditLogs,
            LocalDateTime generatedAtUtc
    ) {}

    public List<PairSignal> evaluateAll28Pairs() {
        return evaluateAll28Pairs(LocalDateTime.now(), 100.0, 1.0, 2.0);
    }

    public List<PairSignal> evaluateAll28Pairs(LocalDateTime referenceTimeUtc,
                                              double accountBalance,
                                              double riskPerTradePercent,
                                              double maxSingleCurrencyExposurePercent) {

        LocalDateTime nowUtc = referenceTimeUtc != null ? referenceTimeUtc : LocalDateTime.now();

        Map<String, CurrencyMacroScore> macroScores = new HashMap<>();
        for (String ccy : List.of("EUR", "GBP", "AUD", "NZD", "USD", "CAD", "CHF", "JPY")) {
            macroScores.put(ccy, macroScorer.calculateCurrencyScore(
                    ccy, nowUtc, CurrencySpecificProfile.ScoringMode.AUTO, Map.of()));
        }

        List<ActiveTradeEntity> openTrades = activeTradeRepository.findByStatus("OPEN");
        Map<String, Double> singleCurrencyNetExposure = calculateNetCurrencyExposures(openTrades);

        List<PairSignal> signals = new ArrayList<>();

        for (String pair : CANONICAL_28_PAIRS) {
            String base = pair.substring(0, 3);
            String quote = pair.substring(3, 6);

            CurrencyMacroScore baseScore = macroScores.get(base);
            CurrencyMacroScore quoteScore = macroScores.get(quote);

            PairSignal signal = evaluateSinglePair(pair, base, quote, baseScore, quoteScore, nowUtc,
                    accountBalance, riskPerTradePercent, maxSingleCurrencyExposurePercent, singleCurrencyNetExposure);
            signals.add(signal);
        }

        signals.sort((a, b) -> Double.compare(Math.abs(b.deltaScore()), Math.abs(a.deltaScore())));
        return signals;
    }

    public PairSignal evaluateSinglePair(String pair, String base, String quote,
                                         CurrencyMacroScore baseScore, CurrencyMacroScore quoteScore,
                                         LocalDateTime nowUtc,
                                         double accountBalance, double riskPerTradePercent,
                                         double maxCurrencyExposurePercent,
                                         Map<String, Double> currentExposures) {

        List<String> audit = new ArrayList<>();
        double bScore = baseScore != null ? baseScore.finalScore() : 0.0;
        double qScore = quoteScore != null ? quoteScore.finalScore() : 0.0;
        double deltaScore = Math.round((bScore - qScore) * 100.0) / 100.0;

        MacroRegime bRegime = baseScore != null ? baseScore.regime() : MacroRegime.GOLDILOCKS_GROWTH;
        MacroRegime qRegime = quoteScore != null ? quoteScore.regime() : MacroRegime.GOLDILOCKS_GROWTH;

        double baseConfidence = baseScore != null ? baseScore.confidenceScore() : 75.0;
        double quoteConfidence = quoteScore != null ? quoteScore.confidenceScore() : 75.0;
        double confidence = (baseConfidence + quoteConfidence) / 2.0;

        // --- GATE 1: Macro Divergence & Regime Gate ---
        boolean isBullishDivergence = (bRegime == MacroRegime.HAWKISH_EXPANSION || bRegime == MacroRegime.GOLDILOCKS_GROWTH)
                && (qRegime == MacroRegime.STAGFLATION_TRAP || qRegime == MacroRegime.DEFLATIONARY_RECESSION);
        boolean isBearishDivergence = (bRegime == MacroRegime.STAGFLATION_TRAP || bRegime == MacroRegime.DEFLATIONARY_RECESSION)
                && (qRegime == MacroRegime.HAWKISH_EXPANSION || qRegime == MacroRegime.GOLDILOCKS_GROWTH);

        boolean gate1Passed = Math.abs(deltaScore) >= 2.2;
        String gate1Desc = String.format("DeltaScore: %.2f (%s: %s vs %s: %s). Divergence: %s",
                deltaScore, base, bRegime.name(), quote, qRegime.name(),
                (isBullishDivergence || isBearishDivergence) ? "OPTIMAL_PURE_FUNDAMENTAL" : "STANDARD_SPREAD");

        // --- GATE 2: Carry Trade & Central Bank Divergence Gate ---
        double bRate = baseScore != null ? baseScore.actualInterestRate() : 2.0;
        double qRate = quoteScore != null ? quoteScore.actualInterestRate() : 2.0;
        double carryDiff = Math.round((bRate - qRate) * 100.0) / 100.0;

        boolean severeCarryDrag = (deltaScore > 0 && carryDiff < -2.0) || (deltaScore < 0 && carryDiff > 2.0);
        boolean gate2Passed = !severeCarryDrag;
        if (severeCarryDrag) {
            confidence -= 15.0;
            audit.add("Gate 2 Warning: Trading against heavy interest rate differential (Carry Drag = " + carryDiff + "%)");
        }
        String gate2Desc = String.format("Carry Rate Diff: %.2f%% (Base: %.2f%%, Quote: %.2f%%). Drag: %s",
                carryDiff, bRate, qRate, severeCarryDrag ? "SEVERE_DRAG_PENALIZED" : "ACCEPTED");

        // --- GATE 3: Data Freshness & Timestamp Integrity Gate ---
        boolean staleWarning = checkStaleIndicators(baseScore) || checkStaleIndicators(quoteScore);
        boolean freshCatalyst = checkUltraFreshCatalyst(baseScore, deltaScore > 0) || checkUltraFreshCatalyst(quoteScore, deltaScore < 0);

        if (staleWarning) {
            confidence -= 20.0;
            audit.add("Gate 3 Warning: Over 40% of core indicators are STALE. Confidence penalized.");
        }
        if (freshCatalyst) {
            confidence = Math.min(100.0, confidence + 10.0);
            audit.add("Gate 3 Catalyst: Ultra-fresh economic release detected aligning with signal direction.");
        }
        boolean gate3Passed = !staleWarning;
        String gate3Desc = String.format("Freshness Integrity: StaleWarning=%b, UltraFreshCatalyst=%b", staleWarning, freshCatalyst);

        // --- GATE 4: Multi-Stage Temporal News Shield ---
        NewsEvaluation newsEval = evaluateNewsShield(base, quote, nowUtc);
        boolean gate4Passed = newsEval.status != NewsShieldStatus.NEWS_LOCKED;
        String gate4Desc = String.format("News Shield: %s. Nearest High-Impact: %s (in %.1f hours)",
                newsEval.status.getLabel(), newsEval.eventTitle, newsEval.hoursToNews);

        // --- GATE 5: Multi-Timeframe Structure Gate ---
        MarketStructureEntity structure = marketStructureRepository.findByPairAndTimeframe(pair, "DAILY")
                .orElse(new MarketStructureEntity(pair, "DAILY", "RANGE", "SWING", 0.0, 0.0, 0.0));

        boolean structureConflict = false;
        String structureStatus = "ALIGNED";
        if (deltaScore >= 4.0 && "BEARISH".equalsIgnoreCase(structure.getTrend())) {
            structureConflict = true;
            structureStatus = "WAIT_FOR_BOS";
            audit.add("Gate 5 Warning: Bullish macro (+ " + deltaScore + ") conflicts with Bearish Daily HTF structure. Wait for BOS.");
        } else if (deltaScore <= -4.0 && "BULLISH".equalsIgnoreCase(structure.getTrend())) {
            structureConflict = true;
            structureStatus = "WAIT_FOR_BOS";
            audit.add("Gate 5 Warning: Bearish macro (" + deltaScore + ") conflicts with Bullish Daily HTF structure. Wait for BOS.");
        }
        boolean gate5Passed = !structureConflict;
        String gate5Desc = String.format("HTF Structure: %s (%s). Status: %s", structure.getTrend(), structure.getLastStructureEvent(), structureStatus);

        // --- GATE 6: Portfolio Correlation & Single-Currency Exposure Gate ---
        double currentBaseExp = currentExposures.getOrDefault(base, 0.0);
        double currentQuoteExp = currentExposures.getOrDefault(quote, 0.0);

        double proposedBaseExp = currentBaseExp + (deltaScore >= 0 ? riskPerTradePercent : -riskPerTradePercent);
        double proposedQuoteExp = currentQuoteExp + (deltaScore >= 0 ? -riskPerTradePercent : riskPerTradePercent);

        boolean exposureExceeded = Math.abs(proposedBaseExp) > maxCurrencyExposurePercent
                || Math.abs(proposedQuoteExp) > maxCurrencyExposurePercent;

        boolean gate6Passed = !exposureExceeded;
        if (exposureExceeded) {
            audit.add(String.format("Gate 6 Warning: Opening %s would exceed single-currency exposure ceiling (Base: %.1f%%, Quote: %.1f%% vs Limit: %.1f%%)",
                    pair, Math.abs(proposedBaseExp), Math.abs(proposedQuoteExp), maxCurrencyExposurePercent));
        }
        String gate6Desc = String.format("Exposure Risk: Net %s=%.1f%%, Net %s=%.1f%% (Ceiling: %.1f%%)",
                base, currentBaseExp, quote, currentQuoteExp, maxCurrencyExposurePercent);

        // --- GATE 7: Execution Quality, ATR Volatility Stop & Exact Pip-Value Lot Sizing ---
        double baseAtr = PAIR_DAILY_ATR_PIPS.getOrDefault(pair, 75.0);
        boolean isVolatile = freshCatalyst || (newsEval.status == NewsShieldStatus.PRE_NEWS_CAUTION);
        double atrMultiplier = isVolatile ? 1.15 : 0.85;

        double stopLossPips = Math.round(baseAtr * atrMultiplier * 10.0) / 10.0;
        // Conviction-scaled R:R ratio: stronger macro divergence expands swing target from 2.2R up to 3.0R
        double rrRatio = Math.abs(deltaScore) >= 5.0 ? 2.8 : (Math.abs(deltaScore) >= 3.0 ? 2.4 : 2.1);
        double takeProfitPips = Math.round(stopLossPips * rrRatio * 10.0) / 10.0;

        // Exact Pip Value in USD per 1.00 Standard Lot for this quote currency
        double pipValuePerStandardLot = QUOTE_CCY_PIP_VALUE_USD.getOrDefault(quote, 10.0);
        double convictionRiskMultiplier = Math.abs(deltaScore) >= 4.5 ? 1.0 : (Math.abs(deltaScore) >= 2.5 ? 0.85 : 0.65);
        double effectiveSizeMult = newsEval.status == NewsShieldStatus.NEWS_LOCKED ? 0.25 : newsEval.status.getSizeMultiplier();
        double riskDollars = accountBalance * (riskPerTradePercent / 100.0) * convictionRiskMultiplier * effectiveSizeMult;
        double recommendedLots = Math.max(0.01, Math.round((riskDollars / (stopLossPips * pipValuePerStandardLot)) * 100.0) / 100.0);

        boolean gate7Passed = true;
        String gate7Desc = String.format("Execution: StopLoss=%.1f pips (ATR=%.0f, Mult=%.2fx), TakeProfit=%.1f pips (1:%.1f R:R), PipVal=$%.2f/lot, Size=%.2f lots ($%.0f risk)",
                stopLossPips, baseAtr, atrMultiplier, takeProfitPips, rrRatio, pipValuePerStandardLot, recommendedLots, riskDollars);

        boolean allPassed = gate1Passed && gate2Passed && gate3Passed && gate4Passed && gate5Passed && gate6Passed && gate7Passed;

        GateEvaluation gateEval = new GateEvaluation(
                gate1Passed, gate1Desc,
                gate2Passed, carryDiff, severeCarryDrag, gate2Desc,
                gate3Passed, staleWarning, freshCatalyst, gate3Desc,
                newsEval.status, newsEval.eventTitle, newsEval.hoursToNews, gate4Desc,
                gate5Passed, structure.getTrend(), structureStatus, gate5Desc,
                gate6Passed, currentBaseExp, currentQuoteExp, gate6Desc,
                gate7Passed, stopLossPips, takeProfitPips, recommendedLots, gate7Desc,
                allPassed
        );

        String signalClassification = classifySignal(deltaScore, confidence, gateEval, structureConflict);
        String direction = deltaScore > 0 ? "BUY" : (deltaScore < 0 ? "SELL" : "NEUTRAL");
        String catalyst = deltaScore > 0
                ? String.format("%s Leading Macro Divergence (+%.2f pts) vs %s", base, deltaScore, quote)
                : String.format("%s Lagging / Drag (%.2f pts) vs %s", base, deltaScore, quote);
        String horizon = "2-6 Weeks (Institutional Swing)";

        return new PairSignal(
                pair,
                base,
                quote,
                bScore,
                qScore,
                deltaScore,
                bRegime,
                qRegime,
                Math.round(confidence * 10.0) / 10.0,
                signalClassification,
                direction,
                catalyst,
                horizon,
                gateEval,
                audit,
                nowUtc
        );
    }

    private String classifySignal(double deltaScore, double confidence, GateEvaluation gates, boolean structureConflict) {
        if (gates.gate4NewsStatus() == NewsShieldStatus.NEWS_LOCKED) {
            return "🔒 LOCKED (NEWS SHIELD)";
        }
        if (structureConflict) {
            return "⏳ WAIT FOR BOS (STRUCTURE CONFLICT)";
        }
        if (gates.allSevenGatesPassed() && Math.abs(deltaScore) >= 5.0 && confidence >= 80.0) {
            return deltaScore > 0 ? "💎 ULTRA-SAFE BUY" : "💎 ULTRA-SAFE SELL";
        }
        if (Math.abs(deltaScore) >= 4.0 && !gates.severeCarryDrag()) {
            return deltaScore > 0 ? "🟢 STRONG BUY" : "🔴 STRONG SELL";
        }
        if (Math.abs(deltaScore) >= 2.2) {
            return deltaScore > 0 ? "🟢 BUY" : "🔴 SELL";
        }
        return "⚪ NO TRADE / RANGE";
    }

    private record NewsEvaluation(NewsShieldStatus status, String eventTitle, double hoursToNews) {}

    private NewsEvaluation evaluateNewsShield(String base, String quote, LocalDateTime nowUtc) {
        List<EconomicEventEntity> upcomingBase = economicEventRepository
                .findByCurrencyAndImpactOrderByExactEventTimestampUtcAsc(base, ImpactLevel.HIGH);
        List<EconomicEventEntity> upcomingQuote = economicEventRepository
                .findByCurrencyAndImpactOrderByExactEventTimestampUtcAsc(quote, ImpactLevel.HIGH);

        List<EconomicEventEntity> combined = new ArrayList<>();
        combined.addAll(upcomingBase);
        combined.addAll(upcomingQuote);

        EconomicEventEntity nearest = null;
        double minHours = 999.0;

        for (EconomicEventEntity event : combined) {
            LocalDateTime eventUtc = event.getExactEventTimestampUtc();
            if (eventUtc == null) continue;

            Duration d = Duration.between(nowUtc, eventUtc);
            double hours = d.toSeconds() / 3600.0;

            if (hours >= -1.0 && hours <= minHours) {
                minHours = hours;
                nearest = event;
            }
        }

        if (nearest == null || minHours > 36.0) {
            return new NewsEvaluation(NewsShieldStatus.CLEAR_HORIZON, "None in horizon", minHours);
        }
        if (minHours <= 12.0) {
            return new NewsEvaluation(NewsShieldStatus.NEWS_LOCKED, nearest.getEventName(), minHours);
        }
        return new NewsEvaluation(NewsShieldStatus.PRE_NEWS_CAUTION, nearest.getEventName(), minHours);
    }

    private boolean checkStaleIndicators(CurrencyMacroScore score) {
        if (score == null || score.pillarScores() == null) return false;
        int totalCore = 0;
        int staleCore = 0;

        for (int p = 1; p <= 4; p++) {
            var pillarSummary = score.pillarScores().get(p);
            if (pillarSummary != null && pillarSummary.indicators() != null) {
                for (IndicatorScoreDetail ind : pillarSummary.indicators()) {
                    totalCore++;
                    if (ind.freshnessStatus() == TimeDecayCalculator.FreshnessStatus.STALE) {
                        staleCore++;
                    }
                }
            }
        }
        return totalCore > 0 && ((double) staleCore / totalCore) > 0.45;
    }

    private boolean checkUltraFreshCatalyst(CurrencyMacroScore score, boolean requiredDirectionPositive) {
        if (score == null || score.pillarScores() == null) return false;
        for (int p = 1; p <= 4; p++) {
            var pillar = score.pillarScores().get(p);
            if (pillar != null && pillar.indicators() != null) {
                for (IndicatorScoreDetail ind : pillar.indicators()) {
                    if (ind.freshnessStatus() == TimeDecayCalculator.FreshnessStatus.ULTRA_FRESH) {
                        if (requiredDirectionPositive && ind.weightedScore() > 0.12) return true;
                        if (!requiredDirectionPositive && ind.weightedScore() < -0.12) return true;
                    }
                }
            }
        }
        return false;
    }

    private Map<String, Double> calculateNetCurrencyExposures(List<ActiveTradeEntity> openTrades) {
        Map<String, Double> map = new HashMap<>();
        for (ActiveTradeEntity t : openTrades) {
            if (t.getPair() == null || t.getPair().length() < 6) continue;
            String base = t.getPair().substring(0, 3);
            String quote = t.getPair().substring(3, 6);
            double risk = t.getOpenRiskPercent() != null ? t.getOpenRiskPercent() : 1.0;

            if ("BUY".equalsIgnoreCase(t.getDirection())) {
                map.put(base, map.getOrDefault(base, 0.0) + risk);
                map.put(quote, map.getOrDefault(quote, 0.0) - risk);
            } else {
                map.put(base, map.getOrDefault(base, 0.0) - risk);
                map.put(quote, map.getOrDefault(quote, 0.0) + risk);
            }
        }
        return map;
    }
}
