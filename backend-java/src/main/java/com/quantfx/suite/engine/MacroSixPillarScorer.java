package com.quantfx.suite.engine;

import com.institutional.forex.model.entity.MacroIndicatorEntity;
import com.institutional.forex.registry.OmniIndicatorRegistry;
import com.institutional.forex.repository.MacroIndicatorRepository;
import com.quantfx.suite.engine.CurrencySpecificProfile.MacroRegime;
import com.quantfx.suite.engine.CurrencySpecificProfile.ScoringMode;
import com.quantfx.suite.engine.TimeDecayCalculator.DecayResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * MacroSixPillarScorer v3.0: Quantitative scoring engine implementing Anton Kreil's
 * 6-Pillar Macro Methodology across 42 institutional indicators per currency,
 * Taylor Rule Deviation, Economic Regime Matrix, and exact release date vintage tracking.
 */
@Service
public class MacroSixPillarScorer {

    private static final Logger log = LoggerFactory.getLogger(MacroSixPillarScorer.class);

    private final MacroIndicatorRepository macroIndicatorRepository;
    private final OmniIndicatorRegistry indicatorRegistry;
    private final TimeDecayCalculator timeDecayCalculator;
    private final CurrencySpecificProfile currencyProfile;

    public MacroSixPillarScorer(MacroIndicatorRepository macroIndicatorRepository,
                                OmniIndicatorRegistry indicatorRegistry,
                                TimeDecayCalculator timeDecayCalculator,
                                CurrencySpecificProfile currencyProfile) {
        this.macroIndicatorRepository = macroIndicatorRepository;
        this.indicatorRegistry = indicatorRegistry;
        this.timeDecayCalculator = timeDecayCalculator;
        this.currencyProfile = currencyProfile;
    }

    public record IndicatorScoreDetail(
            String indicatorName,
            int pillarNumber,
            Double lastValue,
            Double targetRate,
            Double sigma,
            double zScoreLevel,
            double momentum,
            double surpriseFactor,
            double rawScore,
            double decayFactor,
            double boostFactor,
            double sensitivityMultiplier,
            double weightedScore,
            TimeDecayCalculator.FreshnessStatus freshnessStatus,
            double elapsedDays
    ) {}

    public record PillarScoreSummary(
            int pillarNumber,
            String pillarName,
            double pillarWeight,
            double rawPillarScore,
            double weightedPillarScore,
            List<IndicatorScoreDetail> indicators
    ) {}

    public record CurrencyMacroScore(
            String currency,
            double finalScore, // Scaled strictly to [-10.0, +10.0]
            double confidenceScore, // 10.0% to 100.0%
            MacroRegime regime,
            ScoringMode mode,
            double taylorInterestRate,
            double actualInterestRate,
            double taylorDeviation,
            double inflationGap,
            double outputGap,
            Map<Integer, PillarScoreSummary> pillarScores,
            List<String> auditLogs,
            LocalDateTime calculatedAtUtc
    ) {}

    public CurrencyMacroScore calculateCurrencyScore(String currency) {
        return calculateCurrencyScore(currency, LocalDateTime.now(), ScoringMode.AUTO, Map.of());
    }

    public CurrencyMacroScore calculateCurrencyScore(String currency,
                                                     LocalDateTime referenceTimeUtc,
                                                     ScoringMode mode,
                                                     Map<String, Double> overrides) {
        String ccy = currency.toUpperCase();
        List<String> audit = new ArrayList<>();
        audit.add("=== Commencing Anton Kreil 6-Pillar Macro Scoring v3.0 for " + ccy + " [" + mode + "] ===");

        List<MacroIndicatorEntity> entities = macroIndicatorRepository
                .findByCurrencyOrderByPillarNumberAscIndicatorNameAsc(ccy);

        if (entities.isEmpty()) {
            audit.add("Warning: No database indicators found for " + ccy + ". Matrix empty.");
            return createEmptyScore(ccy, audit);
        }

        Map<Integer, List<IndicatorScoreDetail>> pillarIndicators = new HashMap<>();
        for (int p = 1; p <= 6; p++) {
            pillarIndicators.put(p, new ArrayList<>());
        }

        Map<String, Double> currentValuesMap = new HashMap<>();
        int populatedIndicatorsCount = 0;
        double sumDecay = 0.0;

        for (MacroIndicatorEntity entity : entities) {
            String name = entity.getIndicatorName();
            int pillar = entity.getPillarNumber();

            Double lastVal = entity.getLastValue();
            if (overrides != null && overrides.containsKey(name)) {
                lastVal = overrides.get(name);
                audit.add("Custom override applied for " + name + ": " + lastVal);
            }

            if (lastVal != null) {
                currentValuesMap.put(name, lastVal);
                populatedIndicatorsCount++;
            }

            boolean isReversePolarity = "Unemployment Rate".equalsIgnoreCase(name)
                    || "Youth Unemployment Rate".equalsIgnoreCase(name)
                    || "Government Debt to GDP".equalsIgnoreCase(name);

            // 1. Z-Score Level Calculation (L_c,i)
            double target = entity.getTargetRate() != null ? entity.getTargetRate()
                    : indicatorRegistry.getTargetRateForCurrency(name, ccy);
            double sigma = resolveSigma(entity);

            double numerator = (lastVal != null) ? (lastVal - target) : 0.0;
            if (isReversePolarity && lastVal != null) {
                numerator = target - lastVal;
            }
            double zScore = clamp(numerator / Math.max(0.0001, sigma), -3.0, 3.0);

            // 2. Revision-Adjusted Momentum (M_c,i)
            double momentum = 0.0;
            if (lastVal != null) {
                Double prev = entity.getPreviousValue();
                Double revPrev = entity.getRevisedPreviousValue();

                if (revPrev != null && prev != null) {
                    double momPrimary = (lastVal - revPrev) / Math.max(0.1, Math.abs(revPrev)) * 2.5;
                    double momRevision = (revPrev - prev) / Math.max(0.1, Math.abs(prev)) * 0.5;
                    momentum = clamp(momPrimary + momRevision, -3.0, 3.0);
                } else if (prev != null) {
                    momentum = clamp((lastVal - prev) / Math.max(0.1, Math.abs(prev)) * 2.5, -3.0, 3.0);
                }

                if (isReversePolarity) {
                    momentum = -momentum;
                }
            }

            // 3. Market Surprise Factor (S_c,i)
            DecayResult decay = timeDecayCalculator.calculateDecay(entity, referenceTimeUtc);
            sumDecay += decay.decayFactor();

            double surprise = 0.0;
            Double consensus = entity.getConsensusValue() != null ? entity.getConsensusValue() : entity.getForecastValue();
            if (lastVal != null && consensus != null && decay.deltaDays() <= 25.0) {
                double surpriseDenom = Math.max(0.05, 0.25 * sigma);
                double surpriseNum = lastVal - consensus;
                if (isReversePolarity) {
                    surpriseNum = consensus - lastVal;
                }
                surprise = clamp(surpriseNum / surpriseDenom, -3.0, 3.0);
            }

            // 4. Raw Score and Weighting
            double intraWeight = entity.getWeight() != null ? entity.getWeight() : 0.15;
            double sensitivity = currencyProfile.getIndicatorMultiplier(ccy, name, pillar);

            double compositeFactor = (0.50 * zScore) + (0.30 * momentum) + (0.20 * surprise);
            double rawScore = compositeFactor * intraWeight * decay.decayFactor() * decay.boostFactor() * sensitivity;

            IndicatorScoreDetail detail = new IndicatorScoreDetail(
                    name,
                    pillar,
                    lastVal,
                    target,
                    sigma,
                    zScore,
                    momentum,
                    surprise,
                    rawScore,
                    decay.decayFactor(),
                    decay.boostFactor(),
                    sensitivity,
                    rawScore,
                    decay.status(),
                    decay.deltaDays()
            );

            pillarIndicators.get(pillar).add(detail);
        }

        // 5. Pillar Score Aggregation
        Map<Integer, PillarScoreSummary> pillarSummaries = new LinkedHashMap<>();
        for (int p = 1; p <= 6; p++) {
            OmniIndicatorRegistry.PillarMetadata meta = OmniIndicatorRegistry.PILLAR_MAP.get(p);
            String pName = meta != null ? meta.pillarName() : "Pillar " + p;
            double pWeight = meta != null ? meta.pillarWeight() : 0.15;

            List<IndicatorScoreDetail> details = pillarIndicators.get(p);
            double sumRaw = 0.0;
            for (IndicatorScoreDetail d : details) {
                sumRaw += d.weightedScore();
            }

            double weightedPillar = sumRaw * pWeight;
            pillarSummaries.put(p, new PillarScoreSummary(p, pName, pWeight, sumRaw, weightedPillar, details));
        }

        // 6. Central Bank Reaction Function (Taylor Rule) & Macro Regime Identification
        Double rawCoreCpi = currentValuesMap.get("Core CPI");
        Double headlineCpi = currentValuesMap.getOrDefault("CPI YoY", 2.5);
        // Guard against raw CPI index points (e.g. 339.4 index level on US Core Consumer Prices)
        Double coreCpi = (rawCoreCpi != null && rawCoreCpi > -10.0 && rawCoreCpi < 20.0) ? rawCoreCpi : headlineCpi;
        Double gdpGrowth = currentValuesMap.get("GDP Growth YoY");
        Double compPmi = currentValuesMap.getOrDefault("Composite PMI",
                (currentValuesMap.getOrDefault("Manufacturing PMI", 50.0) + currentValuesMap.getOrDefault("Services PMI", 50.0)) / 2.0);
        Double interestRate = currentValuesMap.getOrDefault("Interest Rate", 2.0);

        double targetInflation = 2.0;
        double targetGdp = 2.0;
        double inflationGap = (coreCpi != null ? coreCpi : 2.0) - targetInflation;
        double outputGap = (gdpGrowth != null ? gdpGrowth : 2.0) - targetGdp;
        double pmiGap = (compPmi - 50.0) * 0.10;
        double combinedOutputGap = outputGap + pmiGap;

        double rStar = currencyProfile.getNeutralRealRate(ccy);
        double taylorRate = rStar + (coreCpi != null ? coreCpi : 2.0) + 0.5 * inflationGap + 0.5 * combinedOutputGap;
        double taylorDeviation = taylorRate - interestRate;

        audit.add(String.format("Taylor Analysis: CoreCPI=%.2f%% (Gap=%.2f%%), OutputGap=%.2f%%, r*=%.2f%% => TaylorRate=%.2f%% vs Actual=%.2f%% (Dev=%.2f%%)",
                coreCpi != null ? coreCpi : 2.0, inflationGap, combinedOutputGap, rStar, taylorRate, interestRate, taylorDeviation));

        MacroRegime regime = detectRegime(combinedOutputGap, compPmi, inflationGap);
        audit.add("Economic Regime Identified: " + regime.name() + " (" + regime.getEnTitle() + ")");

        double regimePenalty = 0.0;
        if (regime == MacroRegime.HAWKISH_EXPANSION) {
            PillarScoreSummary p2 = pillarSummaries.get(2);
            pillarSummaries.put(2, new PillarScoreSummary(2, p2.pillarName(), p2.pillarWeight(), p2.rawPillarScore() * 1.30, p2.weightedPillarScore() * 1.30, p2.indicators()));
            audit.add("Regime Adjustment [HAWKISH_EXPANSION]: Pillar 2 (Prices) score multiplied by 1.30x");
        } else if (regime == MacroRegime.GOLDILOCKS_GROWTH) {
            PillarScoreSummary p3 = pillarSummaries.get(3);
            PillarScoreSummary p4 = pillarSummaries.get(4);
            pillarSummaries.put(3, new PillarScoreSummary(3, p3.pillarName(), p3.pillarWeight(), p3.rawPillarScore() * 1.20, p3.weightedPillarScore() * 1.20, p3.indicators()));
            pillarSummaries.put(4, new PillarScoreSummary(4, p4.pillarName(), p4.pillarWeight(), p4.rawPillarScore() * 1.20, p4.weightedPillarScore() * 1.20, p4.indicators()));
            audit.add("Regime Adjustment [GOLDILOCKS_GROWTH]: Pillars 3 & 4 scores multiplied by 1.20x");
        } else if (regime == MacroRegime.STAGFLATION_TRAP) {
            PillarScoreSummary p2 = pillarSummaries.get(2);
            double newP2Raw = p2.rawPillarScore() > 0 ? (p2.rawPillarScore() * -0.80) : p2.rawPillarScore();
            pillarSummaries.put(2, new PillarScoreSummary(2, p2.pillarName(), p2.pillarWeight(), newP2Raw, newP2Raw * p2.pillarWeight(), p2.indicators()));
            regimePenalty = -1.5;
            audit.add("Regime Adjustment [STAGFLATION_TRAP]: Inverted positive inflation score to negative (-0.80x) and applied -1.50 point penalty");
        }

        double totalWeighted = 0.0;
        for (PillarScoreSummary summary : pillarSummaries.values()) {
            totalWeighted += summary.weightedPillarScore();
        }

        double scaledScore = clamp((totalWeighted * 5.0) + regimePenalty, -10.0, 10.0);
        scaledScore = Math.round(scaledScore * 100.0) / 100.0;

        double expectedDenom = Math.max(20.0, entities.size());
        double completenessRatio = Math.min(1.0, (double) populatedIndicatorsCount / expectedDenom);
        double avgDecay = sumDecay / Math.max(1, entities.size());
        double confidence = clamp((completenessRatio * 60.0) + (avgDecay * 40.0), 10.0, 100.0);
        confidence = Math.round(confidence * 10.0) / 10.0;

        audit.add(String.format("Final Macro Score for %s: %.2f / 10.0 (Confidence: %.1f%%)", ccy, scaledScore, confidence));

        return new CurrencyMacroScore(
                ccy,
                scaledScore,
                confidence,
                regime,
                mode,
                taylorRate,
                interestRate,
                taylorDeviation,
                inflationGap,
                combinedOutputGap,
                pillarSummaries,
                audit,
                referenceTimeUtc
        );
    }

    private MacroRegime detectRegime(double outputGap, double compositePmi, double inflationGap) {
        if ((outputGap < -0.2 || compositePmi < 48.5) && inflationGap > 0.5) {
            return MacroRegime.STAGFLATION_TRAP;
        }
        if (outputGap > 0 && compositePmi >= 50.5 && inflationGap > 0) {
            return MacroRegime.HAWKISH_EXPANSION;
        }
        if (outputGap > 0 && compositePmi >= 50.5 && inflationGap <= 0) {
            return MacroRegime.GOLDILOCKS_GROWTH;
        }
        if (outputGap < 0 && compositePmi < 49.0 && inflationGap < 0) {
            return MacroRegime.DEFLATIONARY_RECESSION;
        }
        return outputGap >= 0 ? MacroRegime.GOLDILOCKS_GROWTH : MacroRegime.DEFLATIONARY_RECESSION;
    }

    private double resolveSigma(MacroIndicatorEntity entity) {
        if (entity.getHistoricalStdDev() != null && entity.getHistoricalStdDev() > 0.0001) {
            return entity.getHistoricalStdDev();
        }
        if (entity.getHighestValue() != null && entity.getLowestValue() != null) {
            double range = entity.getHighestValue() - entity.getLowestValue();
            if (range > 0.0001) {
                return range / 4.0;
            }
        }
        if ("Employment Change".equalsIgnoreCase(entity.getIndicatorName())) {
            return "USD".equalsIgnoreCase(entity.getCurrency()) ? 60.0 : 15.0;
        }
        if (entity.getIndicatorName().contains("PMI") || entity.getIndicatorName().contains("Confidence")) {
            return 3.0;
        }
        return 1.0;
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    private CurrencyMacroScore createEmptyScore(String ccy, List<String> audit) {
        return new CurrencyMacroScore(
                ccy,
                0.0,
                10.0,
                MacroRegime.DEFLATIONARY_RECESSION,
                ScoringMode.AUTO,
                2.0,
                2.0,
                0.0,
                0.0,
                0.0,
                Map.of(),
                audit,
                LocalDateTime.now()
        );
    }
}
