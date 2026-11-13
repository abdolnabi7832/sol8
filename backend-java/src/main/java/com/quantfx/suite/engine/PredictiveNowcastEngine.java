package com.quantfx.suite.engine;

import com.institutional.forex.model.entity.EconomicEventEntity;
import com.institutional.forex.model.entity.MacroIndicatorEntity;
import com.institutional.forex.registry.OmniIndicatorRegistry;
import com.institutional.forex.repository.EconomicEventRepository;
import com.institutional.forex.repository.MacroIndicatorRepository;
import com.quantfx.suite.engine.CurrencySpecificProfile.ScoringMode;
import com.quantfx.suite.engine.MacroSixPillarScorer.CurrencyMacroScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * PredictiveNowcastEngine: Advanced predictive nowcasting and 3-branch scenario simulator
 * forecasting macroeconomic trajectories and market impact before official news releases.
 */
@Service
public class PredictiveNowcastEngine {

    private static final Logger log = LoggerFactory.getLogger(PredictiveNowcastEngine.class);

    private final MacroIndicatorRepository macroIndicatorRepository;
    private final EconomicEventRepository economicEventRepository;
    private final OmniIndicatorRegistry indicatorRegistry;
    private final MacroSixPillarScorer macroScorer;

    // Typical Average True Range (ATR) in pips for major pairs
    private static final Map<String, Double> PAIR_DAILY_ATR = Map.ofEntries(
            Map.entry("EURUSD", 75.0),
            Map.entry("GBPUSD", 95.0),
            Map.entry("USDJPY", 90.0),
            Map.entry("AUDUSD", 65.0),
            Map.entry("USDCAD", 70.0),
            Map.entry("USDCHF", 60.0),
            Map.entry("NZDUSD", 65.0),
            Map.entry("EURGBP", 55.0),
            Map.entry("EURJPY", 100.0),
            Map.entry("GBPJPY", 125.0),
            Map.entry("AUDJPY", 85.0),
            Map.entry("CADJPY", 85.0)
    );

    public PredictiveNowcastEngine(MacroIndicatorRepository macroIndicatorRepository,
                                  EconomicEventRepository economicEventRepository,
                                  OmniIndicatorRegistry indicatorRegistry,
                                  MacroSixPillarScorer macroScorer) {
        this.macroIndicatorRepository = macroIndicatorRepository;
        this.economicEventRepository = economicEventRepository;
        this.indicatorRegistry = indicatorRegistry;
        this.macroScorer = macroScorer;
    }

    public record NowcastPrediction(
            String currency,
            String indicatorName,
            Double lastOfficialValue,
            Double consensusValue,
            double modelPredictedValue,
            double predictedDelta,
            double surpriseStdDev,
            boolean isHighSurpriseProbability,
            String surpriseDirectionAlert, // HAWKISH_BEAT, DOVISH_MISS, NEUTRAL
            double modelConfidence,
            String methodologyExplanation
    ) {}

    public record ScenarioBranch(
            String scenarioName, // BULLISH_BEAT, BASELINE_INLINE, BEARISH_MISS
            double assumedValue,
            double newCurrencyScore,
            double scoreDelta,
            String recommendedTradePair,
            String tradeDirection, // LONG, SHORT, NEUTRAL
            double estimatedPipMovement,
            boolean isReversalTriggered,
            String narrativeSummary
    ) {}

    public record EventSimulationResult(
            String eventId,
            String eventName,
            String currency,
            String mappedIndicator,
            LocalDateTime eventTimestampUtc,
            Double consensus,
            double currentBaselineScore,
            NowcastPrediction nowcast,
            ScenarioBranch bullishBeatScenario,
            ScenarioBranch baselineInlineScenario,
            ScenarioBranch bearishMissScenario,
            LocalDateTime simulatedAtUtc
    ) {}

    public record SimulatedPairSignal(
            String pair,
            String baseCurrency,
            String quoteCurrency,
            double baseScore,
            double quoteScore,
            double netSpreadScore,
            String bias, // STRONG_BUY, BUY, NEUTRAL, SELL, STRONG_SELL
            double estimatedVolatilityPips
    ) {}

    public record CustomScenarioSimulation(
            Map<String, Double> simulatedCurrencyScores,
            Map<String, Double> baselineCurrencyScores,
            List<SimulatedPairSignal> topRankedPairs,
            List<String> scenarioInsights,
            LocalDateTime simulationTimestampUtc
    ) {}

    /**
     * Nowcasting: Forecasts next release value using high-frequency leading indicators.
     */
    public NowcastPrediction nowcastIndicator(String currency, String indicatorName) {
        String ccy = currency.toUpperCase();
        MacroIndicatorEntity targetEntity = macroIndicatorRepository
                .findByCurrencyAndIndicatorName(ccy, indicatorName)
                .orElse(null);

        if (targetEntity == null) {
            return new NowcastPrediction(ccy, indicatorName, 0.0, 0.0, 0.0, 0.0, 0.0, false, "UNKNOWN", 10.0, "Indicator not found");
        }

        Double lastVal = targetEntity.getLastValue() != null ? targetEntity.getLastValue() : targetEntity.getTargetRate();
        Double consensus = targetEntity.getConsensusValue() != null ? targetEntity.getConsensusValue()
                : (targetEntity.getForecastValue() != null ? targetEntity.getForecastValue() : lastVal);

        double sigma = resolveSigma(targetEntity);
        double predictedVal;
        String methodology;

        List<MacroIndicatorEntity> allCcyIndicators = macroIndicatorRepository
                .findByCurrencyOrderByPillarNumberAscIndicatorNameAsc(ccy);

        Map<String, Double> valMap = new HashMap<>();
        for (MacroIndicatorEntity e : allCcyIndicators) {
            if (e.getLastValue() != null) valMap.put(e.getIndicatorName(), e.getLastValue());
        }

        // 1. GDP Growth YoY Nowcasting via Leading PMIs, Retail Sales, and Employment
        if ("GDP Growth YoY".equalsIgnoreCase(indicatorName)) {
            double compPmi = valMap.getOrDefault("Composite PMI",
                    (valMap.getOrDefault("Manufacturing PMI", 50.0) + valMap.getOrDefault("Services PMI", 50.0)) / 2.0);
            double retail = valMap.getOrDefault("Retail Sales YoY", 2.5);
            double emp = valMap.getOrDefault("Employment Change", 50.0);

            double deltaPmi = (compPmi - 50.0) * 0.04;
            double deltaRetail = (retail - 2.0) * 0.05;
            double deltaEmp = (emp - 20.0) / 100.0 * 0.10;

            predictedVal = lastVal + (0.45 * deltaPmi) + (0.30 * deltaRetail) + (0.25 * deltaEmp);
            methodology = String.format("GDP Weighted Regression: Baseline(%.2f) + PMI_Delta(%.2f) + Retail_Delta(%.2f) + Emp_Delta(%.2f)",
                    lastVal, deltaPmi, deltaRetail, deltaEmp);

        // 2. CPI YoY Nowcasting via Leading PPI and Wage Growth
        } else if ("CPI YoY".equalsIgnoreCase(indicatorName)) {
            double ppi = valMap.getOrDefault("PPI YoY", lastVal);
            double wages = valMap.getOrDefault("Wage Growth YoY", 3.0);

            double ppiPassThrough = (ppi - lastVal) * 0.15;
            double wagePassThrough = (wages - 3.0) * 0.10;

            predictedVal = lastVal + ppiPassThrough + wagePassThrough;
            methodology = String.format("Inflation Leading Pipeline: Baseline(%.2f) + PPI_PassThrough(%.2f) + Wage_Pressure(%.2f)",
                    lastVal, ppiPassThrough, wagePassThrough);

        // 3. General Momentum Extrapolation for other indicators
        } else {
            Double prev = targetEntity.getPreviousValue();
            double trend = prev != null ? (lastVal - prev) * 0.35 : 0.0;
            predictedVal = lastVal + trend;
            methodology = String.format("Auto-Regressive Trend Extrapolation: Last(%.2f) + Trend(%.2f)", lastVal, trend);
        }

        predictedVal = Math.round(predictedVal * 100.0) / 100.0;
        double diffFromConsensus = predictedVal - consensus;
        double surpriseStdDev = Math.round((diffFromConsensus / Math.max(0.05, sigma)) * 100.0) / 100.0;

        boolean isHighSurprise = Math.abs(surpriseStdDev) >= 0.50;
        String alert;
        if (surpriseStdDev >= 0.50) {
            alert = "HAWKISH_BEAT_ALERT (Model projects release well above consensus)";
        } else if (surpriseStdDev <= -0.50) {
            alert = "DOVISH_MISS_ALERT (Model projects release significantly below consensus)";
        } else {
            alert = "INLINE_ALIGNMENT (Model aligns closely with news consensus)";
        }

        double confidence = 75.0 + (isHighSurprise ? 10.0 : 0.0);

        return new NowcastPrediction(
                ccy,
                indicatorName,
                lastVal,
                consensus,
                predictedVal,
                diffFromConsensus,
                surpriseStdDev,
                isHighSurprise,
                alert,
                confidence,
                methodology
        );
    }

    /**
     * Simulates the 3 outcome branches for an upcoming calendar event.
     */
    public EventSimulationResult simulateUpcomingEvent(String eventId) {
        EconomicEventEntity event = economicEventRepository.findByEventId(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Economic calendar event not found: " + eventId));

        // Strict quantitative validation: Non-numeric, holiday, or clock shift events are forbidden
        if (!Boolean.TRUE.equals(event.getIsQuantSimulatable())) {
            throw new IllegalArgumentException("Event is not quant-simulatable (non-numeric, holiday, or clock shift): " + event.getEventName());
        }

        if (event.getImpact() == com.institutional.forex.model.enums.ImpactLevel.NONE || event.getImpact() == com.institutional.forex.model.enums.ImpactLevel.LOW) {
            throw new IllegalArgumentException("Only HIGH or MED impact events can be simulated in the predictive engine: " + event.getEventName());
        }

        String ccy = event.getCurrency();
        String indicatorName = event.getMappedStandardIndicator() != null
                ? event.getMappedStandardIndicator()
                : indicatorRegistry.matchStandardIndicator(event.getEventName(), ccy);

        if (indicatorName == null) {
            indicatorName = "CPI YoY"; // Fallback to major price driver
        }

        CurrencyMacroScore baselineScore = macroScorer.calculateCurrencyScore(ccy);
        double currentScore = baselineScore.finalScore();

        MacroIndicatorEntity indicatorEntity = macroIndicatorRepository
                .findByCurrencyAndIndicatorName(ccy, indicatorName)
                .orElse(new MacroIndicatorEntity(ccy, 2, "Prices", indicatorName, 0.4, 2.0, com.institutional.forex.model.enums.Frequency.MONTHLY, "%"));

        double sigma = resolveSigma(indicatorEntity);
        Double consensus = event.getConsensus() != null ? event.getConsensus()
                : (event.getForecast() != null ? event.getForecast() : indicatorEntity.getLastValue());
        if (consensus == null) consensus = indicatorEntity.getTargetRate();

        NowcastPrediction nowcast = nowcastIndicator(ccy, indicatorName);

        // Identify counter-currency for best trade opportunity
        String bestOpponent = findOpponentCurrency(ccy, currentScore);

        // --- Scenario 1: BULLISH_BEAT (Consensus + 1.5 sigma) ---
        boolean isUnemployment = "Unemployment Rate".equalsIgnoreCase(indicatorName);
        double beatValue = isUnemployment ? (consensus - 1.5 * sigma) : (consensus + 1.5 * sigma);
        CurrencyMacroScore beatScore = macroScorer.calculateCurrencyScore(ccy, LocalDateTime.now(), ScoringMode.HYBRID, Map.of(indicatorName, beatValue));
        double beatDelta = beatScore.finalScore() - currentScore;
        double beatPips = estimatePipMovement(ccy, bestOpponent, Math.abs(beatDelta));
        ScenarioBranch beatBranch = new ScenarioBranch(
                "BULLISH_BEAT",
                Math.round(beatValue * 100.0) / 100.0,
                beatScore.finalScore(),
                Math.round(beatDelta * 100.0) / 100.0,
                formatPair(ccy, bestOpponent),
                "BUY",
                beatPips,
                false,
                String.format("Release beats consensus by +1.5σ. Currency surges to %.2f (+%.2f pts). Strong buy on %s.",
                        beatScore.finalScore(), beatDelta, formatPair(ccy, bestOpponent))
        );

        // --- Scenario 2: BASELINE_INLINE (Actual = Consensus) ---
        CurrencyMacroScore inlineScore = macroScorer.calculateCurrencyScore(ccy, LocalDateTime.now(), ScoringMode.HYBRID, Map.of(indicatorName, consensus));
        double inlineDelta = inlineScore.finalScore() - currentScore;
        double inlinePips = estimatePipMovement(ccy, bestOpponent, Math.abs(inlineDelta));
        ScenarioBranch inlineBranch = new ScenarioBranch(
                "BASELINE_INLINE",
                consensus,
                inlineScore.finalScore(),
                Math.round(inlineDelta * 100.0) / 100.0,
                formatPair(ccy, bestOpponent),
                "NEUTRAL",
                inlinePips,
                false,
                String.format("Release meets consensus exactly. Time decay resets. Score adjusts by %.2f pts.", inlineDelta)
        );

        // --- Scenario 3: BEARISH_MISS (Consensus - 1.5 sigma) ---
        double missValue = isUnemployment ? (consensus + 1.5 * sigma) : (consensus - 1.5 * sigma);
        CurrencyMacroScore missScore = macroScorer.calculateCurrencyScore(ccy, LocalDateTime.now(), ScoringMode.HYBRID, Map.of(indicatorName, missValue));
        double missDelta = missScore.finalScore() - currentScore;
        double missPips = estimatePipMovement(ccy, bestOpponent, Math.abs(missDelta));
        boolean isReversal = (currentScore >= 1.5 && missScore.finalScore() <= -1.0);
        ScenarioBranch missBranch = new ScenarioBranch(
                "BEARISH_MISS",
                Math.round(missValue * 100.0) / 100.0,
                missScore.finalScore(),
                Math.round(missDelta * 100.0) / 100.0,
                formatPair(ccy, bestOpponent),
                "SELL",
                missPips,
                isReversal,
                String.format("Release misses consensus by -1.5σ. Currency score plunges to %.2f (%.2f pts). %s",
                        missScore.finalScore(), missDelta, isReversal ? "CRITICAL REVERSAL TRIGGERED!" : "Sell bias confirmed.")
        );

        return new EventSimulationResult(
                eventId,
                event.getEventName(),
                ccy,
                indicatorName,
                event.getExactEventTimestampUtc(),
                consensus,
                currentScore,
                nowcast,
                beatBranch,
                inlineBranch,
                missBranch,
                LocalDateTime.now()
        );
    }

    /**
     * Interactive Scenario Simulator: Runs custom hypothetical values across all 8 currencies
     * and constructs the full 28-pair quantitative cross-matrix without altering persistent DB.
     */
    public CustomScenarioSimulation simulateCustomScenario(String targetCurrency,
                                                           Map<String, Double> customOverrides,
                                                           LocalDateTime futureDate) {
        LocalDateTime simDate = futureDate != null ? futureDate : LocalDateTime.now();
        Map<String, Double> baselineScores = new LinkedHashMap<>();
        Map<String, Double> simulatedScores = new LinkedHashMap<>();
        List<String> insights = new ArrayList<>();

        for (String ccy : indicatorRegistry.getSupportedCurrencies()) {
            CurrencyMacroScore base = macroScorer.calculateCurrencyScore(ccy, simDate, ScoringMode.AUTO, Map.of());
            baselineScores.put(ccy, base.finalScore());

            if (ccy.equalsIgnoreCase(targetCurrency) && customOverrides != null && !customOverrides.isEmpty()) {
                CurrencyMacroScore sim = macroScorer.calculateCurrencyScore(ccy, simDate, ScoringMode.HYBRID, customOverrides);
                simulatedScores.put(ccy, sim.finalScore());
                insights.add(String.format("Target %s modified by user scenario: Score moved from %.2f to %.2f (Regime: %s)",
                        ccy, base.finalScore(), sim.finalScore(), sim.regime().name()));
            } else {
                simulatedScores.put(ccy, base.finalScore());
            }
        }

        // Build 28 G8 Currency Pairs Matrix
        List<SimulatedPairSignal> pairSignals = generate28Pairs(simulatedScores);

        // Sort pairs by absolute net score spread (highest edge opportunities first)
        pairSignals.sort((a, b) -> Double.compare(Math.abs(b.netSpreadScore()), Math.abs(a.netSpreadScore())));

        return new CustomScenarioSimulation(
                simulatedScores,
                baselineScores,
                pairSignals,
                insights,
                LocalDateTime.now()
        );
    }

    /**
     * Generates all 28 canonical currency pairs from the 8 G8 currencies.
     */
    public List<SimulatedPairSignal> generate28Pairs(Map<String, Double> scores) {
        List<String> currencies = List.of("EUR", "GBP", "AUD", "NZD", "USD", "CAD", "CHF", "JPY");
        List<SimulatedPairSignal> list = new ArrayList<>();

        for (int i = 0; i < currencies.size(); i++) {
            for (int j = i + 1; j < currencies.size(); j++) {
                String base = currencies.get(i);
                String quote = currencies.get(j);

                double bScore = scores.getOrDefault(base, 0.0);
                double qScore = scores.getOrDefault(quote, 0.0);
                double spread = Math.round((bScore - qScore) * 100.0) / 100.0;

                String bias;
                if (spread >= 3.5) bias = "STRONG_BUY";
                else if (spread >= 1.5) bias = "BUY";
                else if (spread <= -3.5) bias = "STRONG_SELL";
                else if (spread <= -1.5) bias = "SELL";
                else bias = "NEUTRAL";

                String pairName = base + quote;
                double estAtr = PAIR_DAILY_ATR.getOrDefault(pairName, 75.0);

                list.add(new SimulatedPairSignal(pairName, base, quote, bScore, qScore, spread, bias, estAtr));
            }
        }
        return list;
    }

    private String findOpponentCurrency(String ccy, double currentScore) {
        // Find weakest currency if current is bullish, or strongest if current is bearish
        String bestOpponent = "USD".equalsIgnoreCase(ccy) ? "EUR" : "USD";
        double extremeScore = (currentScore >= 0) ? 999.0 : -999.0;

        for (String other : indicatorRegistry.getSupportedCurrencies()) {
            if (other.equalsIgnoreCase(ccy)) continue;
            CurrencyMacroScore otherScore = macroScorer.calculateCurrencyScore(other);
            if (otherScore != null) {
                if (currentScore >= 0 && otherScore.finalScore() < extremeScore) {
                    extremeScore = otherScore.finalScore();
                    bestOpponent = other;
                } else if (currentScore < 0 && otherScore.finalScore() > extremeScore) {
                    extremeScore = otherScore.finalScore();
                    bestOpponent = other;
                }
            }
        }
        return bestOpponent;
    }

    private double estimatePipMovement(String base, String quote, double scoreDelta) {
        String pair = formatPair(base, quote);
        double atr = PAIR_DAILY_ATR.getOrDefault(pair, 75.0);
        double est = scoreDelta * 0.45 * atr;
        return Math.round(Math.max(15.0, Math.min(180.0, est)) * 10.0) / 10.0;
    }

    private String formatPair(String ccy1, String ccy2) {
        List<String> order = List.of("EUR", "GBP", "AUD", "NZD", "USD", "CAD", "CHF", "JPY");
        int idx1 = order.indexOf(ccy1);
        int idx2 = order.indexOf(ccy2);
        if (idx1 != -1 && idx2 != -1 && idx1 < idx2) {
            return ccy1 + ccy2;
        } else if (idx1 != -1 && idx2 != -1) {
            return ccy2 + ccy1;
        }
        return ccy1 + ccy2;
    }

    private double resolveSigma(MacroIndicatorEntity entity) {
        if (entity.getHistoricalStdDev() != null && entity.getHistoricalStdDev() > 0.0001) {
            return entity.getHistoricalStdDev();
        }
        if (entity.getHighestValue() != null && entity.getLowestValue() != null) {
            double range = entity.getHighestValue() - entity.getLowestValue();
            if (range > 0.0001) return range / 4.0;
        }
        return 1.0;
    }

    /**
     * Returns strictly quant-simulatable upcoming calendar events with HIGH or MED impact.
     */
    public List<EconomicEventEntity> getSimulatableUpcomingEvents(String currency) {
        LocalDateTime now = LocalDateTime.now();
        List<EconomicEventEntity> candidates;
        if (currency != null && !currency.trim().isEmpty() && !"ALL".equalsIgnoreCase(currency)) {
            candidates = economicEventRepository.findByCurrencyOrderByExactEventTimestampUtcAsc(currency.toUpperCase());
        } else {
            candidates = economicEventRepository.findAll();
        }

        return candidates.stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsQuantSimulatable()))
                .filter(e -> e.getImpact() == com.institutional.forex.model.enums.ImpactLevel.HIGH 
                          || e.getImpact() == com.institutional.forex.model.enums.ImpactLevel.MED)
                .filter(e -> e.getExactEventTimestampUtc() != null && e.getExactEventTimestampUtc().isAfter(now.minusHours(12)))
                .sorted(Comparator.comparing(EconomicEventEntity::getExactEventTimestampUtc))
                .toList();
    }
}
