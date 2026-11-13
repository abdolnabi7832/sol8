package com.quantfx.suite.engine;

import com.institutional.forex.model.enums.Frequency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class TimeDecayCalculatorTest {

    private TimeDecayCalculator calculator;

    @BeforeEach
    public void setUp() {
        calculator = new TimeDecayCalculator();
    }

    @Test
    @DisplayName("Verify Half-Life Decay Formula D(Delta_t) at exactly T_freq")
    public void testHalfLifeDecay() {
        LocalDateTime refTime = LocalDateTime.of(2026, 11, 4, 12, 0);

        // Monthly frequency (T_freq = 30 days). At 30 days, D = exp(-0.6931 * 1) = 0.50
        LocalDateTime release30DaysAgo = refTime.minusDays(30);
        TimeDecayCalculator.DecayResult resMonthly = calculator.calculateDecay(release30DaysAgo, Frequency.MONTHLY, 2, refTime);

        assertEquals(0.50, resMonthly.decayFactor(), 0.01, "At Delta_t = T_freq, decay must be approx 0.50");
        assertEquals(30.0, resMonthly.deltaDays(), 0.05);
        assertFalse(resMonthly.isUltraFresh());

        // Daily frequency (T_freq = 1 day). At 1 day, D = 0.50
        LocalDateTime release1DayAgo = refTime.minusDays(1);
        TimeDecayCalculator.DecayResult resDaily = calculator.calculateDecay(release1DayAgo, Frequency.DAILY, 1, refTime);
        assertEquals(0.50, resDaily.decayFactor(), 0.01);

        // Quarterly frequency (T_freq = 90 days). At 90 days, D = 0.50
        LocalDateTime release90DaysAgo = refTime.minusDays(90);
        TimeDecayCalculator.DecayResult resQuarterly = calculator.calculateDecay(release90DaysAgo, Frequency.QUARTERLY, 3, refTime);
        assertEquals(0.50, resQuarterly.decayFactor(), 0.01);
    }

    @Test
    @DisplayName("Verify Minimum Decay Floor at 0.15 for very old data")
    public void testMinDecayFloor() {
        LocalDateTime refTime = LocalDateTime.of(2026, 11, 4, 12, 0);
        LocalDateTime veryOld = refTime.minusDays(365); // 1 year ago
        TimeDecayCalculator.DecayResult res = calculator.calculateDecay(veryOld, Frequency.MONTHLY, 1, refTime);

        assertEquals(0.15, res.decayFactor(), 0.0001, "Decay must never fall below minimum floor of 0.15");
        assertEquals(TimeDecayCalculator.FreshnessStatus.STALE, res.status());
    }

    @Test
    @DisplayName("Verify Ultra Fresh Boost 1.20x for Delta_t <= 3.0 days in core pillars")
    public void testUltraFreshBoost() {
        LocalDateTime refTime = LocalDateTime.of(2026, 11, 4, 12, 0);
        LocalDateTime release2DaysAgo = refTime.minusDays(2);

        // Pillar 2 (Prices) within 2 days -> Ultra Fresh
        TimeDecayCalculator.DecayResult resPillar2 = calculator.calculateDecay(release2DaysAgo, Frequency.MONTHLY, 2, refTime);
        assertTrue(resPillar2.isUltraFresh());
        assertEquals(1.20, resPillar2.boostFactor(), 0.001);
        assertEquals(TimeDecayCalculator.FreshnessStatus.ULTRA_FRESH, resPillar2.status());

        // Pillar 6 (Consumer/Housing) within 2 days -> Ultra Fresh status but boost only for pillars 1-4
        TimeDecayCalculator.DecayResult resPillar6 = calculator.calculateDecay(release2DaysAgo, Frequency.MONTHLY, 6, refTime);
        assertFalse(resPillar6.isUltraFresh());
        assertEquals(1.0, resPillar6.boostFactor(), 0.001);
    }

    @Test
    @DisplayName("Verify Freshness Status Categorization Transitions")
    public void testFreshnessStatusTransitions() {
        LocalDateTime refTime = LocalDateTime.of(2026, 11, 4, 12, 0);
        // T_freq = 30 days
        // ULTRA_FRESH: <= 3 days
        assertEquals(TimeDecayCalculator.FreshnessStatus.ULTRA_FRESH,
                calculator.calculateDecay(refTime.minusDays(1), Frequency.MONTHLY, 1, refTime).status());

        // FRESH: 3 < delta <= 0.55 * 30 = 16.5 days
        assertEquals(TimeDecayCalculator.FreshnessStatus.FRESH,
                calculator.calculateDecay(refTime.minusDays(10), Frequency.MONTHLY, 1, refTime).status());

        // NOMINAL: 16.5 < delta <= 1.05 * 30 = 31.5 days
        assertEquals(TimeDecayCalculator.FreshnessStatus.NOMINAL,
                calculator.calculateDecay(refTime.minusDays(25), Frequency.MONTHLY, 1, refTime).status());

        // STALE: > 31.5 days
        assertEquals(TimeDecayCalculator.FreshnessStatus.STALE,
                calculator.calculateDecay(refTime.minusDays(35), Frequency.MONTHLY, 1, refTime).status());
    }
}
