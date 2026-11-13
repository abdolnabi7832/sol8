package com.institutional.forex.engine.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.institutional.forex.config.AppProperties;
import com.institutional.forex.engine.network.StealthNetworkClient;
import com.institutional.forex.engine.normalizer.CalendarFilterAndNormalizer;
import com.institutional.forex.model.entity.EconomicEventEntity;
import com.institutional.forex.model.enums.ImpactLevel;
import com.institutional.forex.registry.OmniIndicatorRegistry;
import com.institutional.forex.repository.EconomicEventRepository;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * ForexFactoryEndpointService: Scrapes and ingests economic calendar feeds from ForexFactory
 * via official/unofficial JSON feeds and resilient DOM parsing of calendar__table.
 */
@Service
public class ForexFactoryEndpointService {

    private static final Logger log = LoggerFactory.getLogger(ForexFactoryEndpointService.class);

    private final StealthNetworkClient networkClient;
    private final OmniIndicatorRegistry indicatorRegistry;
    private final EconomicEventRepository economicEventRepository;
    private final CalendarFilterAndNormalizer calendarFilterAndNormalizer;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public ForexFactoryEndpointService(StealthNetworkClient networkClient,
                                       OmniIndicatorRegistry indicatorRegistry,
                                       EconomicEventRepository economicEventRepository,
                                       CalendarFilterAndNormalizer calendarFilterAndNormalizer,
                                       AppProperties appProperties,
                                       ObjectMapper objectMapper) {
        this.networkClient = networkClient;
        this.indicatorRegistry = indicatorRegistry;
        this.economicEventRepository = economicEventRepository;
        this.calendarFilterAndNormalizer = calendarFilterAndNormalizer;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Primary ingestion pipeline: Ingests ForexFactory calendar, trying JSON feed first,
     * falling back gracefully to HTML table parsing.
     */
    @Transactional
    public int ingestForexFactoryCalendar() {
        int processedCount = 0;
        String jsonFeedUrl = appProperties.getEndpoints().getForexFactoryJsonFeedUrl();

        try {
            log.info("[ForexFactory] Attempting JSON feed ingestion from: {}", jsonFeedUrl);
            processedCount = ingestFromJsonFeed(jsonFeedUrl);
            if (processedCount > 0) {
                log.info("[ForexFactory] Successfully ingested {} events via JSON feed", processedCount);
                return processedCount;
            }
        } catch (Exception ex) {
            log.warn("[ForexFactory] JSON feed ingestion failed: {}. Falling back to HTML table scraper...", ex.getMessage());
        }

        // HTML Scraping Fallback
        try {
            String htmlUrl = appProperties.getEndpoints().getForexFactoryCalendarUrl();
            log.info("[ForexFactory] Attempting HTML calendar table ingestion from: {}", htmlUrl);
            processedCount = ingestFromHtmlTable(htmlUrl);
            log.info("[ForexFactory] Successfully ingested {} events via HTML scraper", processedCount);
        } catch (Exception ex) {
            log.error("[ForexFactory] HTML scraper also failed: {}", ex.getMessage(), ex);
        }

        return processedCount;
    }

    /**
     * Ingests JSON weekly feed formatted as:
     * [{"title": "CPI m/m", "country": "USD", "date": "2026-11-05T08:30:00-04:00", "impact": "High", "forecast": "0.3%", "previous": "0.2%"}, ...]
     */
    public int ingestFromJsonFeed(String feedUrl) throws Exception {
        String jsonStr = networkClient.fetchJson(feedUrl);
        JsonNode rootNode = objectMapper.readTree(jsonStr);

        if (!rootNode.isArray()) {
            throw new IllegalArgumentException("Expected JSON array from ForexFactory feed");
        }

        int count = 0;
        for (JsonNode node : rootNode) {
            String title = node.path("title").asText("").trim();
            String currency = node.path("country").asText("").trim().toUpperCase();

            if (!indicatorRegistry.isCurrencySupported(currency) || title.isEmpty()) {
                continue;
            }

            String dateRaw = node.path("date").asText("");
            LocalDateTime exactUtc = parseIsoDateToUtc(dateRaw);
            String dateStr = exactUtc != null ? exactUtc.toLocalDate().toString() : LocalDate.now().toString();
            String timeStr = exactUtc != null ? exactUtc.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "00:00";

            String impactStr = node.path("impact").asText("");
            ImpactLevel impact = ImpactLevel.parseFromScraped(impactStr);

            Double actual = TradingEconomicsScraperService.parseDoubleSafe(node.path("actual").asText(null));
            Double forecast = TradingEconomicsScraperService.parseDoubleSafe(node.path("forecast").asText(null));
            Double previous = TradingEconomicsScraperService.parseDoubleSafe(node.path("previous").asText(null));

            String mappedIndicator = indicatorRegistry.matchStandardIndicator(title, currency);

            String eventId = "ff_" + currency.toLowerCase() + "_" + dateStr.replace("-", "") + "_"
                    + sanitizeId(title);

            mergeOrSaveEvent(eventId, currency, title, mappedIndicator, dateStr, timeStr, exactUtc, impact, actual, previous, forecast, null);
            count++;
        }

        return count;
    }

    /**
     * Ingests ForexFactory HTML calendar parsing `table.calendar__table tr.calendar__row`.
     * Identifies impact classes: icon--ff-impact-red, icon--ff-impact-ora, icon--ff-impact-yel, icon--ff-impact-gra.
     */
    public int ingestFromHtmlTable(String htmlUrl) throws Exception {
        Document doc = networkClient.fetchDocument(htmlUrl);
        Elements rows = doc.select("table.calendar__table tr.calendar__row");

        int count = 0;
        String currentDateStr = LocalDate.now().toString();

        for (Element row : rows) {
            // Check for date header
            Element dateCol = row.selectFirst("td.calendar__date");
            if (dateCol != null && !dateCol.text().trim().isEmpty()) {
                String d = parseHtmlDate(dateCol.text().trim());
                if (d != null) currentDateStr = d;
            }

            Element currencyCol = row.selectFirst("td.calendar__currency");
            if (currencyCol == null) continue;
            String currency = currencyCol.text().trim().toUpperCase();
            if (!indicatorRegistry.isCurrencySupported(currency)) {
                continue;
            }

            Element timeCol = row.selectFirst("td.calendar__time");
            String timeText = timeCol != null ? timeCol.text().trim() : "00:00";

            Element eventCol = row.selectFirst("td.calendar__event, span.calendar__event-title");
            String title = eventCol != null ? eventCol.text().trim() : "";
            if (title.isEmpty()) continue;

            // Detect Impact from Icon class
            ImpactLevel impact = resolveImpactFromHtmlRow(row);

            // Extract numeric metrics
            Element actualCol = row.selectFirst("td.calendar__actual");
            Element forecastCol = row.selectFirst("td.calendar__forecast");
            Element prevCol = row.selectFirst("td.calendar__previous");

            Double actual = actualCol != null ? TradingEconomicsScraperService.parseDoubleSafe(actualCol.text()) : null;
            Double forecast = forecastCol != null ? TradingEconomicsScraperService.parseDoubleSafe(forecastCol.text()) : null;
            Double previous = prevCol != null ? TradingEconomicsScraperService.parseDoubleSafe(prevCol.text()) : null;

            LocalDateTime exactUtc = parseTimeToUtc(currentDateStr, timeText);
            String mappedIndicator = indicatorRegistry.matchStandardIndicator(title, currency);

            String eventId = "ff_" + currency.toLowerCase() + "_" + currentDateStr.replace("-", "") + "_"
                    + sanitizeId(title);

            mergeOrSaveEvent(eventId, currency, title, mappedIndicator, currentDateStr, timeText, exactUtc, impact, actual, previous, forecast, null);
            count++;
        }

        return count;
    }

    private ImpactLevel resolveImpactFromHtmlRow(Element row) {
        Element impactEl = row.selectFirst("td.calendar__impact span");
        if (impactEl == null) return ImpactLevel.NONE;

        String classNames = impactEl.className().toLowerCase();
        if (classNames.contains("impact-red") || classNames.contains("icon--ff-impact-red")) {
            return ImpactLevel.HIGH;
        }
        if (classNames.contains("impact-ora") || classNames.contains("icon--ff-impact-ora")) {
            return ImpactLevel.MED;
        }
        if (classNames.contains("impact-yel") || classNames.contains("icon--ff-impact-yel")) {
            return ImpactLevel.LOW;
        }
        return ImpactLevel.NONE;
    }

    /**
     * Deduplicates and intelligently merges data with existing records.
     */
    private void mergeOrSaveEvent(String eventId, String currency, String title,
                                  String mappedIndicator, String dateStr, String timeStr,
                                  LocalDateTime exactUtc, ImpactLevel impact,
                                  Double actual, Double previous, Double forecast, Double consensus) {

        // Check if event already exists by eventId or natural key
        Optional<EconomicEventEntity> existingOpt = economicEventRepository.findByEventId(eventId);
        if (existingOpt.isEmpty()) {
            existingOpt = economicEventRepository.findByCurrencyAndEventNameAndEventDateStrAndTimeUtc(
                    currency, title, dateStr, timeStr);
        }

        EconomicEventEntity event;
        if (existingOpt.isPresent()) {
            event = existingOpt.get();
        } else {
            OmniIndicatorRegistry.CountryMetadata meta = indicatorRegistry.getCountryMetadata(currency);
            String countryCode = meta != null ? meta.iso2Code() : currency.toLowerCase();
            event = new EconomicEventEntity(eventId, currency, countryCode, title, dateStr, timeStr, exactUtc, impact);
        }

        // Update fields if new data has better precision
        if (mappedIndicator != null) {
            event.setMappedStandardIndicator(mappedIndicator);
        }
        if (impact != null && (event.getImpact() == null || event.getImpact() == ImpactLevel.NONE || impact.getSeverity() > event.getImpact().getSeverity())) {
            event.setImpact(impact);
        }
        if (exactUtc != null) {
            event.setExactEventTimestampUtc(exactUtc);
        }
        if (actual != null) {
            event.setActual(actual);
        }
        if (previous != null && event.getPrevious() == null) {
            event.setPrevious(previous);
        }
        if (forecast != null && event.getForecast() == null) {
            event.setForecast(forecast);
        }
        if (consensus != null && event.getConsensus() == null) {
            event.setConsensus(consensus);
        }

        // Compute surprise standard deviation approximation if actual and forecast are present
        if (event.getActual() != null && event.getForecast() != null) {
            double diff = event.getActual() - event.getForecast();
            event.setSurpriseStdDev(Math.round(diff * 100.0) / 100.0);
        }

        // Apply institutional quant filtering and normalization
        calendarFilterAndNormalizer.classifyAndNormalize(event);

        economicEventRepository.save(event);
    }

    private LocalDateTime parseIsoDateToUtc(String isoString) {
        if (isoString == null || isoString.trim().isEmpty()) return null;
        try {
            return OffsetDateTime.parse(isoString).toLocalDateTime();
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(isoString);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private LocalDateTime parseTimeToUtc(String dateStr, String timeStr) {
        try {
            LocalDate date = LocalDate.parse(dateStr);
            if (timeStr == null || timeStr.trim().isEmpty() || timeStr.equalsIgnoreCase("all day")) {
                return date.atStartOfDay();
            }
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mma", java.util.Locale.ENGLISH);
            String clean = timeStr.trim().toUpperCase().replaceAll("\\s+", "");
            LocalTime time = LocalTime.parse(clean, formatter);
            return LocalDateTime.of(date, time);
        } catch (Exception e) {
            return LocalDate.parse(dateStr).atStartOfDay();
        }
    }

    private String parseHtmlDate(String raw) {
        try {
            // Examples: "Sun Nov 3", "Nov 4"
            int currentYear = LocalDate.now().getYear();
            String withYear = raw.trim() + " " + currentYear;
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEE MMM d yyyy", java.util.Locale.ENGLISH);
            return LocalDate.parse(withYear, fmt).toString();
        } catch (Exception e) {
            return LocalDate.now().toString();
        }
    }

    private String sanitizeId(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]", "_").replaceAll("_+", "_");
    }
}
