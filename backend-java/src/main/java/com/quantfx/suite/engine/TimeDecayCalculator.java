package com.quantfx.suite.engine;

import com.institutional.forex.model.entity.MacroIndicatorEntity;
import com.institutional.forex.model.enums.Frequency;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * TimeDecayCalculator: Exponential time-decay engine modeling information depreciation
 * across institutional macroeconomic indicator frequencies and release recency.
 */
@Component
public class TimeDecayCalculator {

    public static final double LAMBDA = 0.6931; // ln(2) - Half-life decay parameter at Delta_t = T_freq
    public static final double MIN_DECAY_FLOOR = 0.15;
    public static final double ULTRA_FRESH_THRESHOLD_DAYS = 3.0;
    public static final double ULTRA_FRESH_BOOST = 1.20;

    public enum FreshnessStatus {
        ULTRA_FRESH("ULTRA-FRESH", "Ultra Fresh (High Momentum)"),
        FRESH("FRESH", "Active Informational Value"),
        NOMINAL("NOMINAL", "Standard Pricing Phase"),
        STALE("ARCHIVAL", "Awaiting Next Scheduled Release");

        private final String faLabel;
        private final String enDescription;

        FreshnessStatus(String faLabel, String enDescription) {
            this.faLabel = faLabel;
            this.enDescription = enDescription;
        }

        public String getFaLabel() { return faLabel; }
        public String getEnDescription() { return enDescription; }
    }

    public record DecayResult(
            double decayFactor,
            double deltaDays,
            double boostFactor,
            boolean isUltraFresh,
            FreshnessStatus status,
            double periodDays
    ) {}

    /**
     * Calculates exponential time decay using current UTC time.
     */
    public DecayResult calculateDecay(MacroIndicatorEntity indicator) {
        LocalDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime();
        return calculateDecay(indicator, nowUtc);
    }

    /**
     * Calculates exponential time decay with custom reference time (for historical backtesting).
     */
    public DecayResult calculateDecay(MacroIndicatorEntity indicator, LocalDateTime referenceTimeUtc) {
        if (indicator == null) {
            return new DecayResult(MIN_DECAY_FLOOR, 999.0, 1.0, false, FreshnessStatus.STALE, 30.0);
        }

        LocalDateTime releaseUtc = indicator.getExactReleaseTimestampUtc();
        Frequency frequency = indicator.getFrequency();
        int pillar = indicator.getPillarNumber() != null ? indicator.getPillarNumber() : 3;

        return calculateDecay(releaseUtc, frequency, pillar, referenceTimeUtc);
    }

    /**
     * Core mathematical time decay formula implementation.
     */
    public DecayResult calculateDecay(LocalDateTime exactReleaseUtc, Frequency frequency, int pillarNumber, LocalDateTime referenceTimeUtc) {
        double tFreq = getStandardFrequencyDays(frequency);

        if (exactReleaseUtc == null) {
            // Unverified timestamp defaults to nominal half-life decay
            return new DecayResult(0.50, tFreq, 1.0, false, FreshnessStatus.NOMINAL, tFreq);
        }

        LocalDateTime refTime = referenceTimeUtc != null ? referenceTimeUtc : ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime();

        // Exact decimal elapsed days
        Duration duration = Duration.between(exactReleaseUtc, refTime);
        double deltaDays = Math.max(0.0, duration.toSeconds() / 86400.0);

        // Exponential decay: D(delta_t) = max(0.15, exp(-lambda * (delta_t / T_freq)))
        double ratio = deltaDays / tFreq;
        double exponential = Math.exp(-LAMBDA * ratio);
        double decayFactor = Math.max(MIN_DECAY_FLOOR, exponential);

        // Fresh Release Boost: if delta_t <= 3.0 days and in core Pillars 1, 2, 3, or 4
        boolean isUltraFresh = (deltaDays <= ULTRA_FRESH_THRESHOLD_DAYS) && (pillarNumber >= 1 && pillarNumber <= 4);
        double boostFactor = isUltraFresh ? ULTRA_FRESH_BOOST : 1.0;

        // Freshness Status Categorization
        FreshnessStatus status;
        if (deltaDays <= ULTRA_FRESH_THRESHOLD_DAYS) {
            status = FreshnessStatus.ULTRA_FRESH;
        } else if (deltaDays <= 0.55 * tFreq) {
            status = FreshnessStatus.FRESH;
        } else if (deltaDays <= 1.05 * tFreq) {
            status = FreshnessStatus.NOMINAL;
        } else {
            status = FreshnessStatus.STALE;
        }

        return new DecayResult(decayFactor, deltaDays, boostFactor, isUltraFresh, status, tFreq);
    }

    /**
     * Standard frequency in days: Daily = 1, Weekly = 7, Monthly = 30, Quarterly = 90.
     */
    public static double getStandardFrequencyDays(Frequency frequency) {
        if (frequency == null) return 30.0;
        return switch (frequency) {
            case DAILY -> 1.0;
            case WEEKLY -> 7.0;
            case MONTHLY -> 30.0;
            case QUARTERLY -> 90.0;
        };
    }
}
