package com.institutional.forex.registry;

import com.institutional.forex.model.enums.Frequency;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * OmniIndicatorRegistry: The Institutional Canonical Dictionary (OMNI_MAP v3.0)
 * mapping the 6 Anton Kreil Macro Pillars, 42 Institutional Indicators across all 10
 * TradingEconomics sections (Overview, GDP, Labour, Prices, Money, Trade, Government,
 * Business, Consumer, Housing), and G8 Country Profiles.
 */
@Component
public class OmniIndicatorRegistry {

    public record CountryMetadata(
            String currency,
            String countryName,
            String tradingEconomicsSlug,
            String iso2Code,
            List<String> aliases
    ) {}

    public record PillarMetadata(
            int pillarNumber,
            String pillarName,
            double pillarWeight
    ) {}

    public record IndicatorSpec(
            int pillarNumber,
            String category,
            String canonicalName,
            double intraWeight,
            Frequency frequency,
            double defaultTarget,
            String unit,
            List<String> rawAliases
    ) {}

    // Map of Currency to Country Metadata
    public static final Map<String, CountryMetadata> COUNTRY_MAP = new LinkedHashMap<>();

    // Map of Pillar Number to Pillar Metadata
    public static final Map<Integer, PillarMetadata> PILLAR_MAP = new LinkedHashMap<>();

    // List of 42 Institutional Canonical Indicators
    public static final List<IndicatorSpec> INDICATOR_SPECS = new ArrayList<>();

    // Quick lookup map: Canonical name to IndicatorSpec
    public static final Map<String, IndicatorSpec> CANONICAL_INDICATOR_MAP = new LinkedHashMap<>();

    static {
        // --- 1. G8 Country Mapping ---
        COUNTRY_MAP.put("USD", new CountryMetadata("USD", "United States", "united-states", "us",
                List.of("us", "usa", "united states", "fed", "federal reserve")));
        COUNTRY_MAP.put("EUR", new CountryMetadata("EUR", "Euro Area", "euro-area", "ea",
                List.of("ea", "eu", "de", "fr", "it", "es", "germany", "france", "italy", "spain", "ecb", "european union")));
        COUNTRY_MAP.put("GBP", new CountryMetadata("GBP", "United Kingdom", "united-kingdom", "gb",
                List.of("gb", "uk", "united kingdom", "britain", "boe", "bank of england")));
        COUNTRY_MAP.put("JPY", new CountryMetadata("JPY", "Japan", "japan", "jp",
                List.of("jp", "japan", "boj", "bank of japan")));
        COUNTRY_MAP.put("AUD", new CountryMetadata("AUD", "Australia", "australia", "au",
                List.of("au", "australia", "rba", "reserve bank of australia")));
        COUNTRY_MAP.put("CAD", new CountryMetadata("CAD", "Canada", "canada", "ca",
                List.of("ca", "canada", "boc", "bank of canada")));
        COUNTRY_MAP.put("CHF", new CountryMetadata("CHF", "Switzerland", "switzerland", "ch",
                List.of("ch", "switzerland", "swiss", "snb", "swiss national bank")));
        COUNTRY_MAP.put("NZD", new CountryMetadata("NZD", "New Zealand", "new-zealand", "nz",
                List.of("nz", "new zealand", "rbnz", "reserve bank of new zealand")));

        // --- 2. 6 Macro Pillars & Weights ---
        PILLAR_MAP.put(1, new PillarMetadata(1, "Money & Central Bank", 0.25));
        PILLAR_MAP.put(2, new PillarMetadata(2, "Prices & Inflation", 0.20));
        PILLAR_MAP.put(3, new PillarMetadata(3, "GDP & Business", 0.20));
        PILLAR_MAP.put(4, new PillarMetadata(4, "Labour Market", 0.15));
        PILLAR_MAP.put(5, new PillarMetadata(5, "Trade & Fiscal", 0.10));
        PILLAR_MAP.put(6, new PillarMetadata(6, "Consumer & Housing", 0.10));

        // --- 3. 42 Institutional Canonical Indicators across all TradingEconomics Sections ---

        // ================= PILLAR 1: MONEY & CENTRAL BANK (7 Indicators, Sum Weight = 1.00) =================
        register(new IndicatorSpec(1, "Money", "Interest Rate", 0.28, Frequency.MONTHLY, 2.0, "%",
                List.of("interest rate", "deposit facility rate", "repo rate", "cash rate",
                        "official bank rate", "overnight rate", "policy balance rate",
                        "fed funds rate", "target rate", "discount rate", "main refinancing rate")));

        register(new IndicatorSpec(1, "Money", "Interbank Rate", 0.16, Frequency.DAILY, 2.0, "%",
                List.of("interbank rate", "effective federal funds rate", "sofr", "sonia",
                        "euribor", "corra", "saron", "tonar", "libor", "overnight interbank average rate",
                        "overnight call rate", "secured overnight financing rate", "euro short term rate")));

        register(new IndicatorSpec(1, "Money", "Deposit Interest Rate", 0.12, Frequency.MONTHLY, 2.0, "%",
                List.of("deposit interest rate", "overnight reverse repo", "standing deposit facility")));

        register(new IndicatorSpec(1, "Money", "Bank Lending Rate", 0.10, Frequency.MONTHLY, 4.5, "%",
                List.of("bank lending rate", "lending rate", "prime lending rate", "bank bill swap rate")));

        register(new IndicatorSpec(1, "Money", "M1 MoM", 0.10, Frequency.MONTHLY, 0.2, "%",
                List.of("money supply m1", "m1 money supply mom", "m1 money supply", "m1 mom", "m1 annual change")));

        register(new IndicatorSpec(1, "Money", "Money Supply M2 YoY", 0.12, Frequency.MONTHLY, 3.0, "%",
                List.of("money supply m2", "money supply m3", "m2 money supply", "m3 money supply", "broad money")));

        register(new IndicatorSpec(1, "Money", "CB Balance Sheet YoY", 0.12, Frequency.MONTHLY, 0.0, "%",
                List.of("central bank balance sheet", "fed balance sheet", "central bank balance sheet yoy",
                        "boj balance sheet", "ecb balance sheet", "balance sheet yoy", "banks balance sheet")));

        // ================= PILLAR 2: PRICES & INFLATION (8 Indicators, Sum Weight = 1.00) =================
        register(new IndicatorSpec(2, "Prices", "CPI YoY", 0.24, Frequency.MONTHLY, 2.0, "%",
                List.of("inflation rate", "cpi yoy", "inflation rate yoy", "consumer price index yoy",
                        "cpi annual", "cpi (yoy)", "headline cpi yoy", "hicp yoy")));

        register(new IndicatorSpec(2, "Prices", "CPI MoM", 0.12, Frequency.MONTHLY, 0.15, "%",
                List.of("inflation rate mom", "cpi mom", "consumer price index mom", "cpi (mom)", "hicp mom")));

        register(new IndicatorSpec(2, "Prices", "Core CPI", 0.20, Frequency.MONTHLY, 2.0, "%",
                List.of("core inflation rate", "core cpi yoy", "core pce price index annual",
                        "core cpi", "core pce price index", "core inflation rate yoy", "core hicp", "core consumer prices")));

        register(new IndicatorSpec(2, "Prices", "Core Inflation MoM", 0.10, Frequency.MONTHLY, 0.2, "%",
                List.of("core inflation rate mom", "core pce price index mom", "trimmed mean cpi mom", "core cpi mom")));

        register(new IndicatorSpec(2, "Prices", "PPI YoY", 0.10, Frequency.MONTHLY, 2.0, "%",
                List.of("producer prices change", "ppi yoy", "producer price index yoy",
                        "producer prices yoy", "ppi final demand yoy", "producer prices")));

        register(new IndicatorSpec(2, "Prices", "PPI MoM", 0.08, Frequency.MONTHLY, 0.2, "%",
                List.of("producer price inflation mom", "core producer prices mom", "ppi input", "ppi mom")));

        register(new IndicatorSpec(2, "Prices", "Inflation Expectations", 0.08, Frequency.MONTHLY, 2.2, "%",
                List.of("inflation expectations", "michigan inflation expectations", "michigan 5 year inflation expectations",
                        "dmp 1y cpi expectations", "inflation expectations 3y", "inflation expectations 5y")));

        register(new IndicatorSpec(2, "Prices", "Services Inflation", 0.08, Frequency.MONTHLY, 2.5, "%",
                List.of("services inflation", "rent inflation", "food inflation", "gdp deflator", "services producer price index yoy")));

        // ================= PILLAR 3: GDP, GROWTH & BUSINESS (9 Indicators, Sum Weight = 1.00) =================
        register(new IndicatorSpec(3, "GDP & Business", "GDP Growth YoY", 0.22, Frequency.QUARTERLY, 2.0, "%",
                List.of("gdp annual growth rate", "gdp growth yoy", "gdp yoy", "gdp mom",
                        "gross domestic product yoy", "gross domestic product mom", "gross domestic product",
                        "monthly gdp yoy", "full year gdp growth")));

        register(new IndicatorSpec(3, "GDP & Business", "GDP Growth QoQ", 0.16, Frequency.QUARTERLY, 0.5, "%",
                List.of("gdp growth rate", "quarterly gdp", "gdp 3m/3m", "gdp growth annualized", "gdpnow", "monthly gdp mom")));

        register(new IndicatorSpec(3, "GDP & Business", "Manufacturing PMI", 0.12, Frequency.MONTHLY, 50.0, "Index",
                List.of("manufacturing pmi", "ism manufacturing pmi", "s&p global manufacturing pmi",
                        "hcob manufacturing pmi", "jibun bank manufacturing pmi", "judo bank manufacturing pmi",
                        "ivey pmi", "ism manufacturing index", "manufacturing pmi flash")));

        register(new IndicatorSpec(3, "GDP & Business", "Services PMI", 0.14, Frequency.MONTHLY, 50.0, "Index",
                List.of("services pmi", "ism services pmi", "s&p global services pmi",
                        "hcob services pmi", "jibun bank services pmi", "judo bank services pmi",
                        "ism non-manufacturing pmi", "services pmi flash", "non-manufacturing pmi", "non manufacturing pmi")));

        register(new IndicatorSpec(3, "GDP & Business", "Composite PMI", 0.10, Frequency.MONTHLY, 50.0, "Index",
                List.of("composite pmi", "s&p global composite pmi", "hcob composite pmi",
                        "jibun bank composite pmi", "composite pmi flash")));

        register(new IndicatorSpec(3, "GDP & Business", "Business Confidence", 0.08, Frequency.MONTHLY, 50.0, "Index",
                List.of("business confidence", "zew economic sentiment index", "tankan large manufacturing outlook",
                        "nfib business optimism index", "reuters tankan index", "business climate indicator")));

        register(new IndicatorSpec(3, "GDP & Business", "Industrial Production YoY", 0.07, Frequency.MONTHLY, 1.5, "%",
                List.of("industrial production", "industrial production yoy", "manufacturing production", "industrial production mom")));

        register(new IndicatorSpec(3, "GDP & Business", "Capacity Utilization", 0.06, Frequency.MONTHLY, 78.0, "%",
                List.of("capacity utilization", "durable goods orders", "factory orders", "machinery orders")));

        register(new IndicatorSpec(3, "GDP & Business", "Leading Economic Index", 0.05, Frequency.MONTHLY, 100.0, "Index",
                List.of("leading economic index", "composite leading indicator", "coincident index", "weekly economic index")));

        // ================= PILLAR 4: LABOUR MARKET & WAGES (7 Indicators, Sum Weight = 1.00) =================
        register(new IndicatorSpec(4, "Labour", "Unemployment Rate", 0.26, Frequency.MONTHLY, 4.0, "%",
                List.of("unemployment rate", "jobless rate", "harmonised unemployment rate")));

        register(new IndicatorSpec(4, "Labour", "Employment Change", 0.22, Frequency.MONTHLY, 150.0, "K",
                List.of("non farm payrolls", "employment change", "claimant count change",
                        "non-farm payrolls", "nfp", "net employment change", "payrolls change", "adp employment change")));

        register(new IndicatorSpec(4, "Labour", "Wage Growth YoY", 0.18, Frequency.MONTHLY, 3.0, "%",
                List.of("wage growth", "wage growth yoy", "average hourly earnings yoy",
                        "wages yoy", "labour cost index", "regular pay yoy", "employment cost index yoy",
                        "average hourly earnings", "negotiated wage growth")));

        register(new IndicatorSpec(4, "Labour", "Labor Force Participation Rate", 0.10, Frequency.MONTHLY, 63.0, "%",
                List.of("labor force participation rate", "participation rate", "inactivity rate")));

        register(new IndicatorSpec(4, "Labour", "Employment Rate", 0.10, Frequency.MONTHLY, 62.0, "%",
                List.of("employment rate", "employed persons", "full time employment")));

        register(new IndicatorSpec(4, "Labour", "Job Vacancies", 0.08, Frequency.MONTHLY, 500.0, "K",
                List.of("job vacancies", "job offers", "initial jobless claims", "jobs to applications ratio", "job vacancy rate")));

        register(new IndicatorSpec(4, "Labour", "Youth Unemployment Rate", 0.06, Frequency.MONTHLY, 10.0, "%",
                List.of("youth unemployment rate", "long term unemployment rate", "u6 unemployment rate")));

        // ================= PILLAR 5: TRADE, CAPITAL FLOWS & FISCAL (5 Indicators, Sum Weight = 1.00) =================
        register(new IndicatorSpec(5, "Trade", "Balance of Trade", 0.28, Frequency.MONTHLY, 0.0, "B",
                List.of("balance of trade", "trade balance", "goods trade balance", "trade balance goods")));

        register(new IndicatorSpec(5, "Trade", "Current Account to GDP", 0.26, Frequency.QUARTERLY, -1.0, "%",
                List.of("current account to gdp", "current account balance", "current account % of gdp", "current account / gdp")));

        register(new IndicatorSpec(5, "Trade", "Terms of Trade", 0.16, Frequency.QUARTERLY, 100.0, "Index",
                List.of("terms of trade", "capital flows", "foreign direct investment", "global dairy trade price index")));

        register(new IndicatorSpec(5, "Trade", "Government Debt to GDP", 0.15, Frequency.QUARTERLY, 60.0, "%",
                List.of("government debt to gdp", "public sector net debt to gdp", "debt to gdp")));

        register(new IndicatorSpec(5, "Trade", "Government Budget to GDP", 0.15, Frequency.QUARTERLY, -2.0, "%",
                List.of("government budget", "government budget to gdp", "fiscal balance", "government budget value")));

        // ================= PILLAR 6: CONSUMER, CREDIT & HOUSING (6 Indicators, Sum Weight = 1.00) =================
        register(new IndicatorSpec(6, "Consumer & Housing", "Consumer Confidence", 0.24, Frequency.MONTHLY, 50.0, "Index",
                List.of("consumer confidence", "michigan consumer sentiment", "gfk consumer confidence",
                        "westpac consumer confidence", "consumer sentiment", "seco consumer climate",
                        "anz roy morgan consumer confidence index", "cb consumer confidence", "economic optimism index")));

        register(new IndicatorSpec(6, "Consumer & Housing", "Retail Sales YoY", 0.20, Frequency.MONTHLY, 2.5, "%",
                List.of("retail sales yoy", "retail sales annual", "retail trade yoy", "household spending", "brc retail sales monitor yoy")));

        register(new IndicatorSpec(6, "Consumer & Housing", "Retail Sales MoM", 0.14, Frequency.MONTHLY, 0.3, "%",
                List.of("retail sales mom", "retail sales", "retail sales ex autos", "household spending mom", "personal spending")));

        register(new IndicatorSpec(6, "Consumer & Housing", "Building Permits MoM", 0.14, Frequency.MONTHLY, 0.0, "%",
                List.of("building permits mom", "building approvals mom", "housing starts",
                        "building permits", "building approvals", "housing permits mom", "housing starts yoy")));

        register(new IndicatorSpec(6, "Consumer & Housing", "House Price Index YoY", 0.14, Frequency.MONTHLY, 2.0, "%",
                List.of("house price index yoy", "residential property prices", "nationwide housing prices yoy",
                        "corelogic dwelling prices mom", "housing index", "price to rent ratio")));

        register(new IndicatorSpec(6, "Consumer & Housing", "Personal Savings Rate", 0.14, Frequency.QUARTERLY, 5.0, "%",
                List.of("personal savings", "households debt to gdp", "private sector credit", "consumer credit")));
    }

    private static void register(IndicatorSpec spec) {
        INDICATOR_SPECS.add(spec);
        CANONICAL_INDICATOR_MAP.put(spec.canonicalName(), spec);
    }

    /**
     * Matches any raw event or indicator name against the 42 institutional canonical indicators.
     */
    public String matchStandardIndicator(String rawName, String currency) {
        if (rawName == null || rawName.trim().isEmpty()) {
            return null;
        }

        String normalized = cleanRawName(rawName);

        // 1. Direct canonical name match
        for (IndicatorSpec spec : INDICATOR_SPECS) {
            if (cleanRawName(spec.canonicalName()).equals(normalized)) {
                return spec.canonicalName();
            }
        }

        // Disambiguation for Trade & Fiscal
        if (normalized.contains("current account to gdp") || normalized.contains("current account of gdp")) {
            return "Current Account to GDP";
        }
        if (normalized.equals("current account")) {
            return "Current Account to GDP";
        }
        if (normalized.contains("balance of trade") || normalized.contains("trade balance")) {
            return "Balance of Trade";
        }
        if (normalized.contains("government debt to gdp")) {
            return "Government Debt to GDP";
        }
        if (normalized.equals("government budget") || normalized.contains("government budget to gdp")) {
            return "Government Budget to GDP";
        }
        if (normalized.equals("terms of trade")) {
            return "Terms of Trade";
        }

        // Specific disambiguation rules for Prices (Core CPI vs Core MoM vs CPI YoY vs CPI MoM)
        if (normalized.contains("core") && normalized.contains("mom") && (normalized.contains("cpi") || normalized.contains("inflation") || normalized.contains("pce"))) {
            return "Core Inflation MoM";
        }
        if (normalized.contains("core") && (normalized.contains("cpi") || normalized.contains("inflation") || normalized.contains("pce") || normalized.contains("consumer price"))) {
            return "Core CPI";
        }
        if (normalized.contains("mom") && (normalized.contains("cpi") || normalized.contains("inflation") || normalized.contains("consumer price"))) {
            return "CPI MoM";
        }
        if ((normalized.contains("yoy") || normalized.contains("annual") || normalized.equals("inflation rate") || normalized.equals("consumer price index cpi"))
                && (normalized.contains("cpi") || normalized.contains("inflation") || normalized.contains("consumer price") || normalized.contains("hicp"))) {
            return "CPI YoY";
        }
        if (normalized.contains("producer price inflation mom") || (normalized.contains("ppi") && normalized.contains("mom"))) {
            return "PPI MoM";
        }
        if (normalized.contains("producer prices change") || (normalized.contains("ppi") && !normalized.contains("mom"))) {
            return "PPI YoY";
        }
        if (normalized.contains("inflation expectations")) {
            return "Inflation Expectations";
        }
        if (normalized.equals("services inflation") || normalized.equals("rent inflation")) {
            return "Services Inflation";
        }

        // PMI & GDP disambiguation
        if (normalized.contains("composite") && normalized.contains("pmi")) {
            return "Composite PMI";
        }
        if ((normalized.contains("service") || normalized.contains("non manufacturing")) && normalized.contains("pmi")) {
            return "Services PMI";
        }
        if (normalized.contains("manufacturing") && normalized.contains("pmi")) {
            return "Manufacturing PMI";
        }
        if (normalized.equals("gdp growth rate") || normalized.equals("gdp growth qoq") || normalized.equals("gdp sales qoq")) {
            return "GDP Growth QoQ";
        }
        if (normalized.equals("gdp annual growth rate") || normalized.equals("gdp growth yoy") || normalized.equals("monthly gdp yoy")) {
            return "GDP Growth YoY";
        }

        // CAD/GBP monthly GDP special handling
        if (("CAD".equalsIgnoreCase(currency) || "GBP".equalsIgnoreCase(currency))
                && (normalized.contains("gdp") || normalized.contains("gross domestic product"))
                && (normalized.contains("mom") || normalized.contains("monthly"))) {
            return "GDP Growth YoY";
        }

        // Retail Sales disambiguation
        if (normalized.contains("retail sales") && normalized.contains("mom")) {
            return "Retail Sales MoM";
        }
        if (normalized.contains("retail sales") && (normalized.contains("yoy") || normalized.contains("annual"))) {
            return "Retail Sales YoY";
        }

        // 3. Match against registered aliases
        for (IndicatorSpec spec : INDICATOR_SPECS) {
            for (String alias : spec.rawAliases()) {
                String cleanAlias = cleanRawName(alias);
                if (normalized.equals(cleanAlias) || normalized.contains(cleanAlias) || cleanAlias.contains(normalized)) {
                    return spec.canonicalName();
                }
            }
        }

        return null;
    }

    /**
     * Returns currency-specific target rate (e.g. 150K for USD Non-Farm Payrolls, 15K for CAD/AUD/GBP).
     */
    public double getTargetRateForCurrency(String canonicalName, String currency) {
        IndicatorSpec spec = CANONICAL_INDICATOR_MAP.get(canonicalName);
        if (spec == null) {
            return 0.0;
        }

        if ("Employment Change".equalsIgnoreCase(canonicalName)) {
            if ("USD".equalsIgnoreCase(currency)) {
                return 150.0; // 150K
            } else {
                return 15.0;  // 15K
            }
        }

        return spec.defaultTarget();
    }

    /**
     * Cleans and normalizes string for fuzzy/robust matching.
     */
    public static String cleanRawName(String input) {
        if (input == null) return "";
        return input.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public List<IndicatorSpec> getAllStandardIndicators() {
        return Collections.unmodifiableList(INDICATOR_SPECS);
    }

    public IndicatorSpec getSpecByCanonicalName(String canonicalName) {
        return CANONICAL_INDICATOR_MAP.get(canonicalName);
    }

    public CountryMetadata getCountryMetadata(String currency) {
        if (currency == null) return null;
        return COUNTRY_MAP.get(currency.toUpperCase());
    }

    public boolean isCurrencySupported(String currency) {
        return currency != null && COUNTRY_MAP.containsKey(currency.toUpperCase());
    }

    public Set<String> getSupportedCurrencies() {
        return COUNTRY_MAP.keySet();
    }
}
