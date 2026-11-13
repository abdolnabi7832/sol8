package com.institutional.forex.engine.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.institutional.forex.config.AppProperties;
import com.institutional.forex.model.dto.MacroIndicatorDto;
import com.institutional.forex.model.entity.*;
import com.institutional.forex.registry.OmniIndicatorRegistry;
import com.institutional.forex.repository.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * FileDatabaseSyncService: High-integrity dual storage engine that guarantees
 * atomic persistence to JSON and CSV files in the Excel_Logs folder,
 * with full resilience against Windows file locks (e.g. Microsoft Excel).
 */
@Service
public class FileDatabaseSyncService {

    private static final Logger log = LoggerFactory.getLogger(FileDatabaseSyncService.class);

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final MacroIndicatorRepository macroIndicatorRepository;
    private final EconomicEventRepository economicEventRepository;
    private final OmniIndicatorRegistry indicatorRegistry;
    private final PortfolioRepository portfolioRepository;
    private final ActiveTradeRepository activeTradeRepository;
    private final TradeJournalRepository tradeJournalRepository;
    private final PsychologyLogRepository psychologyLogRepository;
    private final MarketStructureRepository marketStructureRepository;
    private final UserSettingsRepository userSettingsRepository;

    public FileDatabaseSyncService(AppProperties appProperties,
                                   ObjectMapper objectMapper,
                                   MacroIndicatorRepository macroIndicatorRepository,
                                   EconomicEventRepository economicEventRepository,
                                   OmniIndicatorRegistry indicatorRegistry,
                                   PortfolioRepository portfolioRepository,
                                   ActiveTradeRepository activeTradeRepository,
                                   TradeJournalRepository tradeJournalRepository,
                                   PsychologyLogRepository psychologyLogRepository,
                                   MarketStructureRepository marketStructureRepository,
                                   UserSettingsRepository userSettingsRepository) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.macroIndicatorRepository = macroIndicatorRepository;
        this.economicEventRepository = economicEventRepository;
        this.indicatorRegistry = indicatorRegistry;
        this.portfolioRepository = portfolioRepository;
        this.activeTradeRepository = activeTradeRepository;
        this.tradeJournalRepository = tradeJournalRepository;
        this.psychologyLogRepository = psychologyLogRepository;
        this.marketStructureRepository = marketStructureRepository;
        this.userSettingsRepository = userSettingsRepository;
    }

    /**
     * Synchronizes all G8 macro indicators and economic calendar events from H2 Database to:
     * - ./data/Excel_Logs/macro_<CCY>.json
     * - ./data/Excel_Logs/macro_<CCY>.csv
     * - ./data/Excel_Logs/calendar_events.json
     * - ./data/Excel_Logs/calendar_log.csv
     */
    public synchronized Map<String, String> syncAllFiles() {
        Map<String, String> syncStatus = new LinkedHashMap<>();
        Path targetDir = appProperties.getStorage().getExcelLogsDirectory();

        try {
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
                log.info("[Sync Engine] Created Excel_Logs directory at: {}", targetDir);
            }
        } catch (IOException e) {
            log.error("[Sync Engine Fatal] Failed to create Excel_Logs directory: {}", e.getMessage(), e);
            syncStatus.put("ERROR_DIR", "Failed to create directory: " + e.getMessage());
            return syncStatus;
        }

        // 1. Sync Macro Indicators for each of the 8 currencies
        for (String ccy : indicatorRegistry.getSupportedCurrencies()) {
            List<MacroIndicatorEntity> entities = macroIndicatorRepository
                    .findByCurrencyOrderByPillarNumberAscIndicatorNameAsc(ccy);

            String jsonFileName = "macro_" + ccy + ".json";
            String csvFileName = "macro_" + ccy + ".csv";

            boolean jsonOk = writeAtomicJson(targetDir.resolve(jsonFileName), entities);
            boolean csvOk = writeAtomicMacroCsv(targetDir.resolve(csvFileName), entities);

            syncStatus.put(jsonFileName, jsonOk ? "SYNCED" : "LOCKED_OR_FAILED");
            syncStatus.put(csvFileName, csvOk ? "SYNCED" : "LOCKED_OR_FAILED");
        }

        // 2. Sync Global Economic Calendar Events
        List<EconomicEventEntity> allEvents = economicEventRepository.findAll();
        boolean calJsonOk = writeAtomicJson(targetDir.resolve("calendar_events.json"), allEvents);
        boolean calCsvOk = writeAtomicCalendarCsv(targetDir.resolve("calendar_log.csv"), allEvents);

        syncStatus.put("calendar_events.json", calJsonOk ? "SYNCED" : "LOCKED_OR_FAILED");
        syncStatus.put("calendar_log.csv", calCsvOk ? "SYNCED" : "LOCKED_OR_FAILED");

        // 3. Sync Portfolio, Trades, Journal, Psychology, Market Structure & Settings
        PortfolioEntity portfolio = portfolioRepository.findFirstByOrderByUpdatedAtUtcDesc().orElse(new PortfolioEntity());
        syncStatus.put("portfolio.json", writeAtomicJson(targetDir.resolve("portfolio.json"), portfolio) ? "SYNCED" : "FAILED");

        List<ActiveTradeEntity> trades = activeTradeRepository.findAll();
        syncStatus.put("active_trades.json", writeAtomicJson(targetDir.resolve("active_trades.json"), trades) ? "SYNCED" : "FAILED");

        List<TradeJournalEntity> journal = tradeJournalRepository.findAll();
        syncStatus.put("trade_journal.json", writeAtomicJson(targetDir.resolve("trade_journal.json"), journal) ? "SYNCED" : "FAILED");

        List<PsychologyLogEntity> psych = psychologyLogRepository.findAll();
        syncStatus.put("psychology.json", writeAtomicJson(targetDir.resolve("psychology.json"), psych) ? "SYNCED" : "FAILED");

        List<MarketStructureEntity> structures = marketStructureRepository.findAll();
        syncStatus.put("market_structure.json", writeAtomicJson(targetDir.resolve("market_structure.json"), structures) ? "SYNCED" : "FAILED");

        UserSettingsEntity settings = userSettingsRepository.findByUserIdentifier("DEFAULT_USER").orElse(new UserSettingsEntity());
        syncStatus.put("settings.json", writeAtomicJson(targetDir.resolve("settings.json"), settings) ? "SYNCED" : "FAILED");

        log.info("[Sync Engine] Dual storage sync completed with status: {}", syncStatus);
        return syncStatus;
    }

    /**
     * Writes object graph to JSON atomically via temporary file and atomic swap,
     * handling Windows/Excel file lock exceptions safely.
     */
    public boolean writeAtomicJson(Path targetPath, Object data) {
        String tempExtension = appProperties.getStorage().getAtomicTempExtension();
        Path tempPath = targetPath.resolveSibling(targetPath.getFileName().toString() + tempExtension);

        try {
            // Write to temporary file first
            String jsonOutput = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
            Files.writeString(tempPath, jsonOutput, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

            // Execute atomic move with retry
            return moveWithLockResilience(tempPath, targetPath);

        } catch (Exception ex) {
            log.error("[Sync Error] Failed writing JSON {}: {}", targetPath.getFileName(), ex.getMessage());
            return false;
        }
    }

    /**
     * Writes Macro Indicators list to CSV atomically.
     */
    public boolean writeAtomicMacroCsv(Path targetPath, List<MacroIndicatorEntity> entities) {
        String tempExtension = appProperties.getStorage().getAtomicTempExtension();
        Path tempPath = targetPath.resolveSibling(targetPath.getFileName().toString() + tempExtension);

        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader("PillarNumber", "Category", "IndicatorName", "LastValue", "PreviousValue",
                        "RevisedPreviousValue", "ConsensusValue", "ForecastValue", "HighestValue",
                        "LowestValue", "TargetRate", "HistoricalMean", "HistoricalStdDev", "Weight",
                        "Unit", "ReleaseDateRaw", "ExactReleaseTimestampUtc", "NextReleaseTimestampUtc",
                        "ReferencePeriod", "Frequency", "UpdatedAtUtc")
                .build();

        try (BufferedWriter writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
             CSVPrinter printer = new CSVPrinter(writer, csvFormat)) {

            for (MacroIndicatorEntity e : entities) {
                printer.printRecord(
                        e.getPillarNumber(),
                        e.getCategory(),
                        e.getIndicatorName(),
                        formatNumber(e.getLastValue()),
                        formatNumber(e.getPreviousValue()),
                        formatNumber(e.getRevisedPreviousValue()),
                        formatNumber(e.getConsensusValue()),
                        formatNumber(e.getForecastValue()),
                        formatNumber(e.getHighestValue()),
                        formatNumber(e.getLowestValue()),
                        formatNumber(e.getTargetRate()),
                        formatNumber(e.getHistoricalMean()),
                        formatNumber(e.getHistoricalStdDev()),
                        formatNumber(e.getWeight()),
                        e.getUnit() != null ? e.getUnit() : "",
                        e.getReleaseDateRaw() != null ? e.getReleaseDateRaw() : "",
                        e.getExactReleaseTimestampUtc() != null ? e.getExactReleaseTimestampUtc().toString() : "",
                        e.getNextReleaseTimestampUtc() != null ? e.getNextReleaseTimestampUtc().toString() : "",
                        e.getReferencePeriod() != null ? e.getReferencePeriod() : "",
                        e.getFrequency() != null ? e.getFrequency().name() : "",
                        e.getUpdatedAtUtc() != null ? e.getUpdatedAtUtc().toString() : ""
                );
            }
            printer.flush();

            return moveWithLockResilience(tempPath, targetPath);

        } catch (Exception ex) {
            log.error("[Sync Error] Failed writing macro CSV {}: {}", targetPath.getFileName(), ex.getMessage());
            return false;
        }
    }

    /**
     * Writes Economic Calendar Events to CSV atomically.
     */
    public boolean writeAtomicCalendarCsv(Path targetPath, List<EconomicEventEntity> events) {
        String tempExtension = appProperties.getStorage().getAtomicTempExtension();
        Path tempPath = targetPath.resolveSibling(targetPath.getFileName().toString() + tempExtension);

        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader("EventId", "Currency", "CountryCode", "EventName", "MappedStandardIndicator",
                        "EventDateStr", "TimeUtc", "TimeTehran", "ExactEventTimestampUtc", "Impact",
                        "Actual", "Previous", "Consensus", "Forecast", "SurpriseStdDev", "CreatedAtUtc")
                .build();

        try (BufferedWriter writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
             CSVPrinter printer = new CSVPrinter(writer, csvFormat)) {

            for (EconomicEventEntity e : events) {
                printer.printRecord(
                        e.getEventId(),
                        e.getCurrency(),
                        e.getCountryCode(),
                        e.getEventName(),
                        e.getMappedStandardIndicator() != null ? e.getMappedStandardIndicator() : "",
                        e.getEventDateStr(),
                        e.getTimeUtc(),
                        e.getTimeTehran(),
                        e.getExactEventTimestampUtc() != null ? e.getExactEventTimestampUtc().toString() : "",
                        e.getImpact() != null ? e.getImpact().name() : "",
                        formatNumber(e.getActual()),
                        formatNumber(e.getPrevious()),
                        formatNumber(e.getConsensus()),
                        formatNumber(e.getForecast()),
                        formatNumber(e.getSurpriseStdDev()),
                        e.getCreatedAtUtc() != null ? e.getCreatedAtUtc().toString() : ""
                );
            }
            printer.flush();

            return moveWithLockResilience(tempPath, targetPath);

        } catch (Exception ex) {
            log.error("[Sync Error] Failed writing calendar CSV {}: {}", targetPath.getFileName(), ex.getMessage());
            return false;
        }
    }

    /**
     * Atomically replaces targetPath with tempPath, implementing retry logic and
     * catching Windows file lock exceptions (e.g. file open in Microsoft Excel).
     */
    private boolean moveWithLockResilience(Path tempPath, Path targetPath) {
        int maxRetries = appProperties.getStorage().getMaxLockRetryAttempts();
        long retryWait = appProperties.getStorage().getLockRetryWaitMillis();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                try {
                    Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                log.debug("[Atomic Sync Success] Updated file -> {}", targetPath.getFileName());
                return true;
            } catch (FileSystemException lockEx) {
                log.warn("[Excel File Lock Warning] Attempt {}/{}: File {} is locked by an external application (e.g. Microsoft Excel).",
                        attempt, maxRetries, targetPath.getFileName());

                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(retryWait);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                } else {
                    // Save as pending file so no data is lost
                    Path pendingPath = targetPath.resolveSibling(targetPath.getFileName().toString() + ".pending_sync");
                    try {
                        Files.move(tempPath, pendingPath, StandardCopyOption.REPLACE_EXISTING);
                        log.warn("[Excel Lock Handled] Target file {} remains locked. Updated data safely preserved in: {}",
                                targetPath.getFileName(), pendingPath.getFileName());
                    } catch (Exception ignore) {}
                    return false;
                }
            } catch (IOException e) {
                log.error("[Sync File System Error] Error moving file {}: {}", targetPath.getFileName(), e.getMessage());
                return false;
            }
        }
        return false;
    }

    private String formatNumber(Double d) {
        if (d == null) return "";
        if (d == Math.floor(d) && !Double.isInfinite(d)) {
            return String.valueOf(d.longValue());
        }
        return String.valueOf(d);
    }
}
