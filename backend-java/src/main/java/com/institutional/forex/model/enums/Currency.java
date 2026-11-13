package com.institutional.forex.model.enums;

/**
 * Standard G8 institutional currencies supported across the Anton Kreil Macro Framework.
 */
public enum Currency {
    USD("United States Dollar", "united-states", "us"),
    EUR("Euro Area", "euro-area", "ea"),
    GBP("British Pound", "united-kingdom", "gb"),
    JPY("Japanese Yen", "japan", "jp"),
    AUD("Australian Dollar", "australia", "au"),
    CAD("Canadian Dollar", "canada", "ca"),
    CHF("Swiss Franc", "switzerland", "ch"),
    NZD("New Zealand Dollar", "new-zealand", "nz");

    private final String countryName;
    private final String tradingEconomicsSlug;
    private final String iso2Code;

    Currency(String countryName, String tradingEconomicsSlug, String iso2Code) {
        this.countryName = countryName;
        this.tradingEconomicsSlug = tradingEconomicsSlug;
        this.iso2Code = iso2Code;
    }

    public String getCountryName() {
        return countryName;
    }

    public String getTradingEconomicsSlug() {
        return tradingEconomicsSlug;
    }

    public String getIso2Code() {
        return iso2Code;
    }

    public static Currency fromString(String ccy) {
        if (ccy == null) return null;
        String clean = ccy.trim().toUpperCase();
        for (Currency c : values()) {
            if (c.name().equals(clean)) {
                return c;
            }
        }
        return null;
    }
}
