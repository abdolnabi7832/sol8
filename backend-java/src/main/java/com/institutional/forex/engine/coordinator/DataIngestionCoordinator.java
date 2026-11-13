package com.institutional.forex.engine.coordinator;

import com.institutional.forex.config.AppProperties;
import com.institutional.forex.engine.network.StealthNetworkClient;
import com.institutional.forex.engine.resolver.CrossLinkTimestampResolver;
import com.institutional.forex.engine.scraper.ForexFactoryEndpointService;
import com.institutional.forex.engine.scraper.TradingEconomicsScraperService;
import com.institutional.forex.engine.sync.FileDatabaseSyncService;
import com.institutional.forex.model.dto.IngestionStatusDto;
import com.institutional.forex.model.entity.MacroIndicatorEntity;
import com.institutional.forex.registry.OmniIndicatorRegistry;
import com.institutional.forex.registry.OmniMacroSeederAndValidator;
import com.institutional.forex.repository.EconomicEventRepository;
import com.institutional.forex.repository.MacroIndicatorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * DataIngestionCoordinator: Master orchestrator of data seeding, stealth scraping,
 * calendar integration, timestamp cross-linking, dual storage persistence,
 * and WebSocket market streaming.
 */
@Service
public class DataIngestionCoordinator {

    private static final Logger log = LoggerFactory.getLogger(DataIngestionCoordinator.class);

    private final AppProperties appProperties;
    private final OmniIndicatorRegistry indicatorRegistry;
    private final MacroIndicatorRepository macroIndicatorRepository;
    private final EconomicEventRepository economicEventRepository;
    private final TradingEconomicsScraperService teScraperService;
    private final ForexFactoryEndpointService ffEndpointService;
    private final CrossLinkTimestampResolver timestampResolver;
    private final FileDatabaseSyncService fileDatabaseSyncService;
    private final SimpMessagingTemplate messagingTemplate;
    private final OmniMacroSeederAndValidator seederAndValidator;
    private final LiveSyncCoordinatorService liveSyncService;

    private final AtomicBoolean isIngestionRunning = new AtomicBoolean(false);
    private final IngestionStatusDto status = new IngestionStatusDto();

    public DataIngestionCoordinator(AppProperties appProperties,
                                    OmniIndicatorRegistry indicatorRegistry,
                                    MacroIndicatorRepository macroIndicatorRepository,
                                    EconomicEventRepository economicEventRepository,
                                    TradingEconomicsScraperService teScraperService,
                                    ForexFactoryEndpointService ffEndpointService,
                                    CrossLinkTimestampResolver timestampResolver,
                                    FileDatabaseSyncService fileDatabaseSyncService,
                                    SimpMessagingTemplate messagingTemplate,
                                    OmniMacroSeederAndValidator seederAndValidator,
                                    LiveSyncCoordinatorService liveSyncService) {
        this.appProperties = appProperties;
        this.indicatorRegistry = indicatorRegistry;
        this.macroIndicatorRepository = macroIndicatorRepository;
        this.economicEventRepository = economicEventRepository;
        this.teScraperService = teScraperService;
        this.ffEndpointService = ffEndpointService;
        this.timestampResolver = timestampResolver;
        this.fileDatabaseSyncService = fileDatabaseSyncService;
        this.messagingTemplate = messagingTemplate;
        this.seederAndValidator = seederAndValidator;
        this.liveSyncService = liveSyncService;
    }

    /**
     * Seeds the standard 20 indicators across all 8 currencies on startup,
     * guaranteeing 100% complete institutional baselines and non-null values.
     */
    @Transactional
    public void seedStandardIndicatorsIfAbsent() {
        log.info("[Coordinator] Verifying standard 20-indicator matrix for all 8 G8 currencies...");
        int healedCount = seederAndValidator.seedOrHealAll160Indicators();
        log.info("[Coordinator] Verification complete. Seeded/Healed {} standard indicator records in H2 Database", healedCount);
    }

    /**
     * Triggered on Spring Boot startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (appProperties.getIngestion().isInitSeedOnStartup()) {
            seedStandardIndicatorsIfAbsent();
        }

        // Initial sync of seeded indicators to Excel_Logs JSON/CSV files
        fileDatabaseSyncService.syncAllFiles();

        if (appProperties.getIngestion().isRunOnStartup()) {
            Thread.ofVirtual().start(this::triggerIngestionPipeline);
        }
    }

    /**
     * Scheduled periodic execution (default every 4 hours).
     */
    @Scheduled(cron = "${app.ingestion.cron:0 0 */4 * * *}")
    public void scheduledExecution() {
        if (appProperties.getIngestion().isEnabled()) {
            log.info("[Scheduler] Triggering scheduled macro data ingestion...");
            triggerIngestionPipeline();
        }
    }

    /**
     * Master ingestion execution pipeline:
     * 1. Scrapes TradingEconomics indicators for each of the 8 currencies
     * 2. Scrapes TradingEconomics calendar
     * 3. Ingests ForexFactory calendar
     * 4. Cross-links exact timestamps & consensus forecasts
     * 5. Atomically writes to H2, JSON, and CSV files
     * 6. Broadcasts real-time STOMP updates via WebSocket
     */
    public synchronized boolean triggerIngestionPipeline() {
        if (!isIngestionRunning.compareAndSet(false, true)) {
            log.warn("[Coordinator] Ingestion pipeline is already running. Skipping concurrent trigger.");
            return false;
        }

        status.setRunning(true);
        status.setLastRunStartTimeUtc(LocalDateTime.now());
        status.setLastStatusMessage("Commencing stealth multi-source ingestion...");

        try {
            log.info("==================================================================");
            log.info("  STARTING INSTITUTIONAL FOREX INGESTION & TIME-SERIES SYNC");
            log.info("==================================================================");

            int totalMacroUpdated = 0;
            Map<String, Integer> ccyCounts = new HashMap<>();

            // 1. Ingest TradingEconomics Macro Indicators for each of the 8 currencies
            for (String ccy : indicatorRegistry.getSupportedCurrencies()) {
                status.setLastStatusMessage("Scraping TradingEconomics indicators for " + ccy);
                int count = teScraperService.scrapeIndicatorsForCurrency(ccy);
                totalMacroUpdated += count;
                ccyCounts.put(ccy, count);
            }
            status.setCurrencyIndicatorCounts(ccyCounts);
            status.setTotalIndicatorsUpdated(totalMacroUpdated);

            // 2. Ingest TradingEconomics Calendar
            status.setLastStatusMessage("Scraping TradingEconomics calendar releases");
            int teCalCount = teScraperService.scrapeEconomicCalendar();

            // 3. Ingest ForexFactory Calendar
            status.setLastStatusMessage("Ingesting ForexFactory economic calendar");
            int ffCalCount = ffEndpointService.ingestForexFactoryCalendar();
            status.setTotalCalendarEventsProcessed(teCalCount + ffCalCount);

            // 4. Cross-Link Exact Release Timestamps
            status.setLastStatusMessage("Cross-linking exact historical and next release timestamps");
            int crossLinked = timestampResolver.resolveAllCurrencies();
            status.setTotalEventsCrossLinked(crossLinked);

            // 5. Dual Storage Atomic Sync to ./data/Excel_Logs
            status.setLastStatusMessage("Synchronizing dual storage files (JSON & CSV)");
            Map<String, String> syncFilesStatus = fileDatabaseSyncService.syncAllFiles();
            status.setSyncFilesStatus(syncFilesStatus);

            // 6. Broadcast real-time notifications via WebSocket
            try {
                messagingTemplate.convertAndSend("/topic/macro-updates",
                        Map.of("status", "SUCCESS", "timestamp", LocalDateTime.now().toString(), "updatedIndicators", totalMacroUpdated));
                messagingTemplate.convertAndSend("/topic/calendar-updates",
                        Map.of("status", "SUCCESS", "timestamp", LocalDateTime.now().toString(), "processedEvents", teCalCount + ffCalCount));
                log.info("[WebSocket Broadcast] Dispatched market update notification to /topic/macro-updates and /topic/calendar-updates");
            } catch (Exception wsEx) {
                log.warn("[WebSocket Broadcast Error] Failed to publish WebSocket message: {}", wsEx.getMessage());
            }

            status.setLastStatusMessage("Pipeline execution completed successfully.");
            status.setLastRunEndTimeUtc(LocalDateTime.now());

            log.info("==================================================================");
            log.info("  INGESTION PIPELINE FINISHED SUCCESSFULLY");
            log.info("  Macro Indicators Updated: {}", totalMacroUpdated);
            log.info("  Calendar Events Processed: {}", teCalCount + ffCalCount);
            log.info("  Indicators Cross-Linked: {}", crossLinked);
            log.info("==================================================================");

            return true;

        } catch (Exception e) {
            log.error("[Coordinator Critical Error] Pipeline execution failed: {}", e.getMessage(), e);
            status.setLastStatusMessage("Pipeline failed: " + e.getMessage());
            return false;
        } finally {
            status.setRunning(false);
            isIngestionRunning.set(false);
        }
    }

    public IngestionStatusDto getStatus() {
        return status;
    }
}
