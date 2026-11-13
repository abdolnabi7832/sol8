package com.quantfx.suite.engine;

import com.institutional.forex.model.entity.ActiveTradeEntity;
import com.institutional.forex.model.entity.PortfolioEntity;
import com.institutional.forex.repository.ActiveTradeRepository;
import com.institutional.forex.repository.PortfolioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * MonteCarloRiskEngine: Institutional portfolio risk engine implementing
 * Fractional Kelly Criterion, 1000-path Monte Carlo simulations, Parametric VaR,
 * and Herfindahl-Hirschman Concentration Index (HHI) with dual USD/IRT conversion.
 */
@Service
public class MonteCarloRiskEngine {

    private static final Logger log = LoggerFactory.getLogger(MonteCarloRiskEngine.class);

    private final PortfolioRepository portfolioRepository;
    private final ActiveTradeRepository activeTradeRepository;

    public MonteCarloRiskEngine(PortfolioRepository portfolioRepository,
                               ActiveTradeRepository activeTradeRepository) {
        this.portfolioRepository = portfolioRepository;
        this.activeTradeRepository = activeTradeRepository;
    }

    public record KellyAnalysis(
            double winRate,
            double riskRewardRatio,
            double fullKellyPercent,
            double halfKellyPercent,
            double quarterKellyPercent, // Institutional Ultra-Safe Recommended
            String recommendation
    ) {}

    public record MonteCarloResult(
            double initialBalance,
            int simulationsCount,
            int tradesPerPath,
            double probabilityOfRuinPercent,
            double medianFinalEquityP50,
            double worstCaseEquityP5,
            double bestCaseEquityP95,
            double averageMaxDrawdownPercent,
            double expectedValuePerTrade,
            List<Double> sampleEquityCurve,
            LocalDateTime calculatedAtUtc
    ) {}

    public record PortfolioRiskSnapshot(
            double accountBalanceUsd,
            double equityUsd,
            double equityIrtToman,
            double irtExchangeRate,
            double totalOpenRiskUsd,
            double totalOpenRiskPercent,
            double parametricVar95Usd,
            double parametricVar99Usd,
            double hhiConcentrationIndex,
            String diversificationStatus, // HIGHLY_DIVERSIFIED, MODERATE, HIGHLY_CONCENTRATED
            Map<String, Double> singleCurrencyExposureUsd,
            Map<String, Double> singleCurrencyExposurePercent,
            KellyAnalysis kelly,
            LocalDateTime snapshotTimestampUtc
    ) {}

    /**
     * Calculates the Fractional Kelly Criterion for position sizing.
     * Formula: K = max(0, W - (1 - W) / R)
     */
    public KellyAnalysis calculateKellyCriterion(double winRate, double riskRewardRatio) {
        double w = Math.max(0.01, Math.min(0.99, winRate));
        double r = Math.max(0.1, riskRewardRatio);

        double fullKelly = w - ((1.0 - w) / r);
        fullKelly = Math.max(0.0, fullKelly);

        double halfKelly = fullKelly * 0.50;
        double quarterKelly = fullKelly * 0.25;

        String rec;
        if (fullKelly <= 0.0) {
            rec = "Negative expectancy strategy. Do not trade.";
        } else if (quarterKelly > 0.02) {
            rec = "High statistical edge. Cap allocation at 2.0% maximum per institutional risk limits.";
        } else {
            rec = String.format("Institutional Ultra-Safe Sizing: Allocate %.2f%% per trade.", quarterKelly * 100.0);
        }

        return new KellyAnalysis(
                Math.round(w * 1000.0) / 10.0,
                r,
                Math.round(fullKelly * 10000.0) / 100.0,
                Math.round(halfKelly * 10000.0) / 100.0,
                Math.round(quarterKelly * 10000.0) / 100.0,
                rec
        );
    }

    /**
     * Executes a 1000-path Monte Carlo simulation over 100 sequential trades.
     */
    public MonteCarloResult runMonteCarloSimulation(double initialBalance,
                                                   double winRate,
                                                   double riskRewardRatio,
                                                   double riskPercentPerTrade,
                                                   double ruinThresholdPercent,
                                                   int numberOfSimulations,
                                                   int tradesPerSimulation) {

        int nSims = numberOfSimulations > 0 ? numberOfSimulations : 1000;
        int nTrades = tradesPerSimulation > 0 ? tradesPerSimulation : 100;

        double riskFrac = riskPercentPerTrade / 100.0;
        double ruinFloor = initialBalance * (1.0 - (ruinThresholdPercent / 100.0));

        double[] finalEquities = new double[nSims];
        int ruinCount = 0;
        double sumMaxDrawdowns = 0.0;

        List<Double> sampleCurve = new ArrayList<>();

        for (int s = 0; s < nSims; s++) {
            double currentEquity = initialBalance;
            double peakEquity = initialBalance;
            double maxDdThisSim = 0.0;
            boolean hitRuin = false;

            if (s == 0) {
                sampleCurve.add(currentEquity);
            }

            for (int t = 0; t < nTrades; t++) {
                double riskAmount = currentEquity * riskFrac;
                boolean isWin = ThreadLocalRandom.current().nextDouble() < winRate;

                if (isWin) {
                    currentEquity += riskAmount * riskRewardRatio;
                } else {
                    currentEquity -= riskAmount;
                }

                if (currentEquity > peakEquity) {
                    peakEquity = currentEquity;
                }
                double dd = (peakEquity - currentEquity) / peakEquity * 100.0;
                if (dd > maxDdThisSim) {
                    maxDdThisSim = dd;
                }

                if (currentEquity <= ruinFloor) {
                    hitRuin = true;
                }

                if (s == 0) {
                    sampleCurve.add(Math.round(currentEquity * 100.0) / 100.0);
                }
            }

            finalEquities[s] = currentEquity;
            sumMaxDrawdowns += maxDdThisSim;
            if (hitRuin) {
                ruinCount++;
            }
        }

        Arrays.sort(finalEquities);

        double probOfRuin = Math.round(((double) ruinCount / nSims) * 1000.0) / 10.0;
        double p5 = Math.round(finalEquities[(int) (nSims * 0.05)] * 100.0) / 100.0;
        double p50 = Math.round(finalEquities[(int) (nSims * 0.50)] * 100.0) / 100.0;
        double p95 = Math.round(finalEquities[(int) (nSims * 0.95)] * 100.0) / 100.0;
        double avgMaxDd = Math.round((sumMaxDrawdowns / nSims) * 10.0) / 10.0;

        // Mathematical Expectancy = (WinRate * Reward) - (LossRate * 1)
        double expectedValue = (winRate * riskRewardRatio) - ((1.0 - winRate) * 1.0);

        return new MonteCarloResult(
                initialBalance,
                nSims,
                nTrades,
                probOfRuin,
                p50,
                p5,
                p95,
                avgMaxDd,
                Math.round(expectedValue * 100.0) / 100.0,
                sampleCurve,
                LocalDateTime.now()
        );
    }

    /**
     * Generates institutional real-time portfolio risk snapshot including
     * HHI concentration index, parametric VaR, and dual USD / Toman equity.
     */
    public PortfolioRiskSnapshot generatePortfolioRiskSnapshot() {
        PortfolioEntity portfolio = portfolioRepository.findFirstByOrderByUpdatedAtUtcDesc()
                .orElse(new PortfolioEntity());

        List<ActiveTradeEntity> openTrades = activeTradeRepository.findByStatus("OPEN");

        double balance = portfolio.getAccountBalance();
        double irtRate = portfolio.getIrtExchangeRate() != null ? portfolio.getIrtExchangeRate() : 68000.0;

        double totalOpenRiskUsd = 0.0;
        double totalUnrealizedPnl = 0.0;

        Map<String, Double> currencyExposureUsd = new HashMap<>();

        for (ActiveTradeEntity trade : openTrades) {
            double riskUsd = trade.getOpenRiskUsd() != null ? trade.getOpenRiskUsd() : 0.0;
            totalOpenRiskUsd += riskUsd;
            if (trade.getUnrealizedPnlUsd() != null) {
                totalUnrealizedPnl += trade.getUnrealizedPnlUsd();
            }

            if (trade.getPair() != null && trade.getPair().length() >= 6) {
                String base = trade.getPair().substring(0, 3);
                String quote = trade.getPair().substring(3, 6);

                if ("BUY".equalsIgnoreCase(trade.getDirection())) {
                    currencyExposureUsd.put(base, currencyExposureUsd.getOrDefault(base, 0.0) + riskUsd);
                    currencyExposureUsd.put(quote, currencyExposureUsd.getOrDefault(quote, 0.0) - riskUsd);
                } else {
                    currencyExposureUsd.put(base, currencyExposureUsd.getOrDefault(base, 0.0) - riskUsd);
                    currencyExposureUsd.put(quote, currencyExposureUsd.getOrDefault(quote, 0.0) + riskUsd);
                }
            }
        }

        double equityUsd = balance + totalUnrealizedPnl;
        double equityIrt = equityUsd * irtRate;
        double totalOpenRiskPercent = balance > 0 ? (totalOpenRiskUsd / balance * 100.0) : 0.0;

        // Parametric Value at Risk (VaR) assuming daily portfolio volatility ~ 1.2%
        double dailyVol = 0.012;
        double var95Usd = Math.round(equityUsd * 1.645 * dailyVol * 100.0) / 100.0;
        double var99Usd = Math.round(equityUsd * 2.326 * dailyVol * 100.0) / 100.0;

        // Herfindahl-Hirschman Concentration Index (HHI)
        // HHI = sum((s_j * 100)^2) where s_j = Exposure_j / TotalEquity
        double hhiSum = 0.0;
        Map<String, Double> currencyExposurePercent = new HashMap<>();

        for (Map.Entry<String, Double> entry : currencyExposureUsd.entrySet()) {
            double absExp = Math.abs(entry.getValue());
            double share = equityUsd > 0 ? (absExp / equityUsd) : 0.0;
            double sharePercent = share * 100.0;
            hhiSum += Math.pow(sharePercent, 2);
            currencyExposurePercent.put(entry.getKey(), Math.round(sharePercent * 10.0) / 10.0);
        }

        double hhi = Math.round(hhiSum * 10.0) / 10.0;
        String divStatus;
        if (hhi < 1500.0) {
            divStatus = "🟢 HIGHLY_DIVERSIFIED (Safe Institutional Distribution)";
        } else if (hhi <= 2500.0) {
            divStatus = "🟡 MODERATE_CONCENTRATION (Acceptable)";
        } else {
            divStatus = "🔴 HIGHLY_CONCENTRATED (Excessive Single-Asset Risk)";
        }

        KellyAnalysis kelly = calculateKellyCriterion(0.55, 2.0);

        return new PortfolioRiskSnapshot(
                Math.round(balance * 100.0) / 100.0,
                Math.round(equityUsd * 100.0) / 100.0,
                Math.round(equityIrt),
                irtRate,
                Math.round(totalOpenRiskUsd * 100.0) / 100.0,
                Math.round(totalOpenRiskPercent * 10.0) / 10.0,
                var95Usd,
                var99Usd,
                hhi,
                divStatus,
                currencyExposureUsd,
                currencyExposurePercent,
                kelly,
                LocalDateTime.now()
        );
    }
}
