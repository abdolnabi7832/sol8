package com.quantfx.suite.engine;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * CurrencySpecificProfile: Institutional sensitivity multipliers modeling
 * Anton Kreil's currency-specific market drivers and trade balances.
 */
@Component
public class CurrencySpecificProfile {

    public enum ScoringMode {
        AUTO("Fully Automated Algorithmic Engine"),
        MANUAL("Manual Quant Override"),
        HYBRID("Automated Baseline with Manual Adjustments");

        private final String description;
        ScoringMode(String description) { this.description = description; }
        public String getDescription() { return description; }
    }

    public enum MacroRegime {
        HAWKISH_EXPANSION("Hawkish Expansion (Rate Hike Cycle)", "Hawkish Expansion (Rate Hike Cycle)"),
        GOLDILOCKS_GROWTH("Goldilocks Growth (Optimal Economic Expansion)", "Goldilocks Growth (Optimal Economic Expansion)"),
        STAGFLATION_TRAP("Stagflation Trap (Currency Depreciation Threat)", "Stagflation Trap (Currency Depreciation Threat)"),
        DEFLATIONARY_RECESSION("Deflationary Recession (Dovish Rate Cuts)", "Deflationary Recession (Dovish Rate Cuts)");

        private final String faTitle;
        private final String enTitle;

        MacroRegime(String faTitle, String enTitle) {
            this.faTitle = faTitle;
            this.enTitle = enTitle;
        }

        public String getFaTitle() { return faTitle; }
        public String getEnTitle() { return enTitle; }
    }

    public record CurrencyMetadata(
            String currency,
            String primaryDriverDescription,
            double centralBankNeutralRate, // r*
            Map<String, Double> indicatorSensitivities,
            Map<Integer, Double> pillarSensitivities
    ) {}

    // Currency-specific sensitivities based on institutional Anton Kreil FX Playbook
    private static final Map<String, CurrencyMetadata> PROFILES = Map.of(
            "USD", new CurrencyMetadata(
                    "USD",
                    "Global Reserve Currency. Extreme sensitivity to Non-Farm Payrolls (NFP), Core CPI, and ISM Services.",
                    0.50,
                    Map.of(
                            "Employment Change", 1.25,
                            "Core CPI", 1.25,
                            "Services PMI", 1.25
                    ),
                    Map.of()
            ),
            "EUR", new CurrencyMetadata(
                    "EUR",
                    "Eurozone Industrial & Export Engine. Heavy weighting on German/HCOB Manufacturing, CPI YoY, and Trade Balance.",
                    0.25,
                    Map.of(
                            "Manufacturing PMI", 1.20,
                            "CPI YoY", 1.20,
                            "Balance of Trade", 1.20
                    ),
                    Map.of()
            ),
            "GBP", new CurrencyMetadata(
                    "GBP",
                    "Service-dominated financial economy. Extreme sensitivity to Services PMI, Wage Growth (BoE hawkishness), and Core CPI.",
                    0.75,
                    Map.of(
                            "Services PMI", 1.30,
                            "Wage Growth YoY", 1.30,
                            "Core CPI", 1.30
                    ),
                    Map.of()
            ),
            "JPY", new CurrencyMetadata(
                    "JPY",
                    "Funding & Carry Currency. Severe sensitivity to BoJ Yield Curve / Interest Rate shifts, Balance Sheet, and Shunto Wage negotiations.",
                    -0.10,
                    Map.of(
                            "Interest Rate", 1.35,
                            "CB Balance Sheet YoY", 1.35,
                            "Wage Growth YoY", 1.35
                    ),
                    Map.of()
            ),
            "AUD", new CurrencyMetadata(
                    "AUD",
                    "Primary Commodity Currency. Strong beta to Trade Balance (iron ore/coal), Employment Change, and quarterly CPI.",
                    0.75,
                    Map.of(
                            "Balance of Trade", 1.30,
                            "Employment Change", 1.30,
                            "CPI YoY", 1.30
                    ),
                    Map.of(
                            5, 1.30 // Trade Pillar boost
                    )
            ),
            "CAD", new CurrencyMetadata(
                    "CAD",
                    "Petro-Currency & US Trade Partner. Sensitive to monthly/quarterly GDP Growth, Employment Change, and Energy Trade Balance.",
                    0.50,
                    Map.of(
                            "GDP Growth YoY", 1.35,
                            "Employment Change", 1.35,
                            "Balance of Trade", 1.35
                    ),
                    Map.of()
            ),
            "CHF", new CurrencyMetadata(
                    "CHF",
                    "Safe Haven & Anti-Inflationary Hub. Driven by strict SNB CPI ceiling, Trade Balance, and Manufacturing PMI.",
                    0.00,
                    Map.of(
                            "CPI YoY", 1.20,
                            "Balance of Trade", 1.20,
                            "Manufacturing PMI", 1.20
                    ),
                    Map.of()
            ),
            "NZD", new CurrencyMetadata(
                    "NZD",
                    "High-Beta Agricultural Export Currency. Heavy sensitivity to CPI YoY, Unemployment Rate, and Current Account deficit.",
                    0.75,
                    Map.of(
                            "CPI YoY", 1.25,
                            "Unemployment Rate", 1.25,
                            "Current Account to GDP", 1.25
                    ),
                    Map.of()
            )
    );

    /**
     * Resolves the sensitivity coefficient for a specific currency and indicator.
     */
    public double getIndicatorMultiplier(String currency, String indicatorName, int pillarNumber) {
        if (currency == null) return 1.0;
        CurrencyMetadata profile = PROFILES.get(currency.toUpperCase());
        if (profile == null) return 1.0;

        double mult = 1.0;

        // Pillar level multiplier
        if (profile.pillarSensitivities().containsKey(pillarNumber)) {
            mult *= profile.pillarSensitivities().get(pillarNumber);
        }

        // Indicator level multiplier
        if (indicatorName != null && profile.indicatorSensitivities().containsKey(indicatorName)) {
            mult *= profile.indicatorSensitivities().get(indicatorName);
        }

        return mult;
    }

    public double getNeutralRealRate(String currency) {
        if (currency == null) return 0.50;
        CurrencyMetadata profile = PROFILES.get(currency.toUpperCase());
        return profile != null ? profile.centralBankNeutralRate() : 0.50;
    }

    public CurrencyMetadata getProfile(String currency) {
        if (currency == null) return null;
        return PROFILES.get(currency.toUpperCase());
    }

    public Set<String> getSupportedCurrencies() {
        return PROFILES.keySet();
    }
}
