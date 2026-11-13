package com.institutional.forex.controller;

import com.institutional.forex.engine.sync.FileDatabaseSyncService;
import com.institutional.forex.model.entity.*;
import com.institutional.forex.repository.*;
import com.quantfx.suite.engine.MonteCarloRiskEngine;
import com.quantfx.suite.engine.MonteCarloRiskEngine.KellyAnalysis;
import com.quantfx.suite.engine.MonteCarloRiskEngine.MonteCarloResult;
import com.quantfx.suite.engine.MonteCarloRiskEngine.PortfolioRiskSnapshot;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * PortfolioTradingController: Full CRUD REST endpoints for Portfolio Management,
 * Active Trades Execution, Journal, Psychology, Market Structure, Settings, and Risk Modeling.
 */
@RestController
@RequestMapping("/api/v1")
public class PortfolioTradingController {

    private final PortfolioRepository portfolioRepository;
    private final ActiveTradeRepository activeTradeRepository;
    private final TradeJournalRepository tradeJournalRepository;
    private final PsychologyLogRepository psychologyLogRepository;
    private final MarketStructureRepository marketStructureRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final MonteCarloRiskEngine riskEngine;
    private final FileDatabaseSyncService syncService;
    private final com.institutional.forex.engine.telegram.TelegramNotificationService telegramService;

    public PortfolioTradingController(PortfolioRepository portfolioRepository,
                                      ActiveTradeRepository activeTradeRepository,
                                      TradeJournalRepository tradeJournalRepository,
                                      PsychologyLogRepository psychologyLogRepository,
                                      MarketStructureRepository marketStructureRepository,
                                      UserSettingsRepository userSettingsRepository,
                                      MonteCarloRiskEngine riskEngine,
                                      FileDatabaseSyncService syncService,
                                      com.institutional.forex.engine.telegram.TelegramNotificationService telegramService) {
        this.portfolioRepository = portfolioRepository;
        this.activeTradeRepository = activeTradeRepository;
        this.tradeJournalRepository = tradeJournalRepository;
        this.psychologyLogRepository = psychologyLogRepository;
        this.marketStructureRepository = marketStructureRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.riskEngine = riskEngine;
        this.syncService = syncService;
        this.telegramService = telegramService;
    }

    // --- PORTFOLIO ---
    @GetMapping("/portfolio")
    public ResponseEntity<PortfolioEntity> getPortfolio() {
        PortfolioEntity entity = portfolioRepository.findFirstByOrderByUpdatedAtUtcDesc()
                .orElseGet(() -> portfolioRepository.save(new PortfolioEntity()));
        return ResponseEntity.ok(entity);
    }

    @PostMapping("/portfolio")
    public ResponseEntity<PortfolioEntity> updatePortfolio(@RequestBody PortfolioEntity updated) {
        PortfolioEntity current = portfolioRepository.findFirstByOrderByUpdatedAtUtcDesc()
                .orElse(new PortfolioEntity());
        if (updated.getAccountBalance() != null) current.setAccountBalance(updated.getAccountBalance());
        if (updated.getEquity() != null) current.setEquity(updated.getEquity());
        if (updated.getIrtExchangeRate() != null) current.setIrtExchangeRate(updated.getIrtExchangeRate());
        if (updated.getTargetMonthlyReturnPercent() != null) current.setTargetMonthlyReturnPercent(updated.getTargetMonthlyReturnPercent());
        if (updated.getMaxAllowedDrawdownPercent() != null) current.setMaxAllowedDrawdownPercent(updated.getMaxAllowedDrawdownPercent());

        PortfolioEntity saved = portfolioRepository.save(current);
        syncService.syncAllFiles();
        return ResponseEntity.ok(saved);
    }

    // --- ACTIVE TRADES & EXECUTION ---
    @GetMapping("/active-trades")
    public ResponseEntity<List<ActiveTradeEntity>> getActiveTrades() {
        return ResponseEntity.ok(activeTradeRepository.findAll());
    }

    @PostMapping("/active-trades")
    public ResponseEntity<ActiveTradeEntity> openTrade(@RequestBody ActiveTradeEntity trade) {
        if (trade.getTradeId() == null || trade.getTradeId().isEmpty()) {
            trade.setTradeId("TRD-" + System.currentTimeMillis() % 100000);
        }
        trade.setStatus("OPEN");
        trade.setOpenedAtUtc(LocalDateTime.now());
        ActiveTradeEntity saved = activeTradeRepository.save(trade);
        syncService.syncAllFiles();

        try {
            telegramService.notifyPositionOpened(
                    saved.getPair(),
                    saved.getDirection(),
                    saved.getLotSize() != null ? saved.getLotSize() : 0.01,
                    saved.getEntryPrice() != null ? saved.getEntryPrice() : 0.0,
                    saved.getStopLoss() != null ? saved.getStopLoss() : 0.0,
                    saved.getTakeProfit() != null ? saved.getTakeProfit() : 0.0,
                    saved.getOpenRiskPercent() != null ? saved.getOpenRiskPercent() : 1.0,
                    saved.getSetupType() != null ? saved.getSetupType() : "Macro Quantitative 6-Pillar Signal"
            );
        } catch (Exception ignored) {}

        return ResponseEntity.ok(saved);
    }

    @PostMapping("/execution")
    public ResponseEntity<ActiveTradeEntity> executeOrder(@RequestBody ActiveTradeEntity order) {
        return openTrade(order);
    }

    @DeleteMapping("/active-trades/{tradeId}")
    public ResponseEntity<Map<String, Object>> closeTrade(@PathVariable String tradeId) {
        Optional<ActiveTradeEntity> opt = activeTradeRepository.findByTradeId(tradeId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        ActiveTradeEntity trade = opt.get();
        trade.setStatus("CLOSED");
        trade.setClosedAtUtc(LocalDateTime.now());
        activeTradeRepository.save(trade);

        // Record automatically to journal
        TradeJournalEntity journal = new TradeJournalEntity();
        journal.setJournalId("JRN-" + trade.getTradeId());
        journal.setPair(trade.getPair());
        journal.setDirection(trade.getDirection());
        journal.setEntryPrice(trade.getEntryPrice());
        journal.setExitPrice(trade.getCurrentPrice() != null ? trade.getCurrentPrice() : trade.getEntryPrice());
        journal.setLotSize(trade.getLotSize());
        journal.setPnlUsd(trade.getUnrealizedPnlUsd() != null ? trade.getUnrealizedPnlUsd() : 0.0);
        journal.setPnlPips(trade.getUnrealizedPnlPips() != null ? trade.getUnrealizedPnlPips() : 0.0);
        journal.setSetupType(trade.getSetupType());
        journal.setEntryDateUtc(trade.getOpenedAtUtc());
        journal.setExitDateUtc(trade.getClosedAtUtc());
        tradeJournalRepository.save(journal);

        syncService.syncAllFiles();

        try {
            String outcome = (journal.getPnlUsd() != null && journal.getPnlUsd() >= 0) ? "WIN (سود)" : "LOSS (زیان)";
            telegramService.notifyPositionClosed(
                    trade.getPair(),
                    trade.getDirection(),
                    trade.getLotSize() != null ? trade.getLotSize() : 0.01,
                    trade.getEntryPrice() != null ? trade.getEntryPrice() : 0.0,
                    journal.getExitPrice(),
                    journal.getPnlUsd() != null ? journal.getPnlUsd() : 0.0,
                    outcome
            );
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of("status", "CLOSED", "tradeId", tradeId, "journalId", journal.getJournalId()));
    }

    // --- TRADE JOURNAL ---
    @GetMapping("/journal")
    public ResponseEntity<List<TradeJournalEntity>> getJournal() {
        return ResponseEntity.ok(tradeJournalRepository.findAllByOrderByCreatedAtUtcDesc());
    }

    @PostMapping("/journal")
    public ResponseEntity<TradeJournalEntity> addJournalEntry(@RequestBody TradeJournalEntity entry) {
        if (entry.getJournalId() == null || entry.getJournalId().isEmpty()) {
            entry.setJournalId("JRN-" + System.currentTimeMillis() % 100000);
        }
        TradeJournalEntity saved = tradeJournalRepository.save(entry);
        syncService.syncAllFiles();
        return ResponseEntity.ok(saved);
    }

    // --- PSYCHOLOGY LOGS ---
    @GetMapping("/psychology")
    public ResponseEntity<List<PsychologyLogEntity>> getPsychologyLogs() {
        return ResponseEntity.ok(psychologyLogRepository.findAllByOrderByRecordedAtUtcDesc());
    }

    @PostMapping("/psychology")
    public ResponseEntity<PsychologyLogEntity> recordPsychology(@RequestBody PsychologyLogEntity logEntry) {
        if (logEntry.getLogId() == null || logEntry.getLogId().isEmpty()) {
            logEntry.setLogId("PSY-" + System.currentTimeMillis() % 100000);
        }
        PsychologyLogEntity saved = psychologyLogRepository.save(logEntry);
        syncService.syncAllFiles();
        return ResponseEntity.ok(saved);
    }

    // --- MARKET STRUCTURE ---
    @GetMapping("/market-structure")
    public ResponseEntity<List<MarketStructureEntity>> getMarketStructure() {
        return ResponseEntity.ok(marketStructureRepository.findAll());
    }

    @PostMapping("/market-structure")
    public ResponseEntity<MarketStructureEntity> updateMarketStructure(@RequestBody MarketStructureEntity structure) {
        Optional<MarketStructureEntity> opt = marketStructureRepository
                .findByPairAndTimeframe(structure.getPair(), structure.getTimeframe());
        MarketStructureEntity target = opt.orElse(structure);

        target.setTrend(structure.getTrend());
        target.setLastStructureEvent(structure.getLastStructureEvent());
        if (structure.getKeySupportLevel() != null) target.setKeySupportLevel(structure.getKeySupportLevel());
        if (structure.getKeyResistanceLevel() != null) target.setKeyResistanceLevel(structure.getKeyResistanceLevel());
        if (structure.getCurrentPrice() != null) target.setCurrentPrice(structure.getCurrentPrice());

        MarketStructureEntity saved = marketStructureRepository.save(target);
        syncService.syncAllFiles();
        return ResponseEntity.ok(saved);
    }

    // --- USER SETTINGS ---
    @GetMapping("/settings")
    public ResponseEntity<UserSettingsEntity> getSettings() {
        UserSettingsEntity entity = userSettingsRepository.findByUserIdentifier("DEFAULT_USER")
                .orElseGet(() -> userSettingsRepository.save(new UserSettingsEntity()));
        return ResponseEntity.ok(entity);
    }

    @PostMapping("/settings")
    public ResponseEntity<UserSettingsEntity> updateSettings(@RequestBody UserSettingsEntity updated) {
        UserSettingsEntity current = userSettingsRepository.findByUserIdentifier("DEFAULT_USER")
                .orElse(new UserSettingsEntity());

        if (updated.getRiskPerTradePercent() != null) current.setRiskPerTradePercent(updated.getRiskPerTradePercent());
        if (updated.getMaxSingleCurrencyExposurePercent() != null) current.setMaxSingleCurrencyExposurePercent(updated.getMaxSingleCurrencyExposurePercent());
        if (updated.getMaxTotalPortfolioRiskPercent() != null) current.setMaxTotalPortfolioRiskPercent(updated.getMaxTotalPortfolioRiskPercent());
        if (updated.getDefaultLotSize() != null) current.setDefaultLotSize(updated.getDefaultLotSize());
        if (updated.getGeminiApiKey() != null) current.setGeminiApiKey(updated.getGeminiApiKey());
        if (updated.getPreferredLanguage() != null) current.setPreferredLanguage(updated.getPreferredLanguage());
        if (updated.getTheme() != null) current.setTheme(updated.getTheme());
        if (updated.getSoundAlertsEnabled() != null) current.setSoundAlertsEnabled(updated.getSoundAlertsEnabled());
        if (updated.getAutoSyncExcelLogs() != null) current.setAutoSyncExcelLogs(updated.getAutoSyncExcelLogs());

        UserSettingsEntity saved = userSettingsRepository.save(current);
        syncService.syncAllFiles();
        return ResponseEntity.ok(saved);
    }

    // --- RISK ENGINES (KELLY & MONTE CARLO) ---
    @GetMapping("/risk/snapshot")
    public ResponseEntity<PortfolioRiskSnapshot> getRiskSnapshot() {
        return ResponseEntity.ok(riskEngine.generatePortfolioRiskSnapshot());
    }

    @GetMapping("/risk/kelly")
    public ResponseEntity<KellyAnalysis> getKelly(
            @RequestParam(defaultValue = "0.55") double winRate,
            @RequestParam(defaultValue = "2.0") double riskReward) {
        return ResponseEntity.ok(riskEngine.calculateKellyCriterion(winRate, riskReward));
    }

    public record MonteCarloRequest(
            double initialBalance,
            double winRate,
            double riskRewardRatio,
            double riskPercentPerTrade,
            double ruinThresholdPercent,
            int numberOfSimulations,
            int tradesPerSimulation
    ) {}

    @PostMapping("/risk/monte-carlo")
    public ResponseEntity<MonteCarloResult> runMonteCarlo(@RequestBody MonteCarloRequest req) {
        MonteCarloResult result = riskEngine.runMonteCarloSimulation(
                req.initialBalance() > 0 ? req.initialBalance() : 50000.0,
                req.winRate() > 0 ? req.winRate() : 0.55,
                req.riskRewardRatio() > 0 ? req.riskRewardRatio() : 2.0,
                req.riskPercentPerTrade() > 0 ? req.riskPercentPerTrade() : 1.0,
                req.ruinThresholdPercent() > 0 ? req.ruinThresholdPercent() : 10.0,
                req.numberOfSimulations() > 0 ? req.numberOfSimulations() : 1000,
                req.tradesPerSimulation() > 0 ? req.tradesPerSimulation() : 100
        );
        return ResponseEntity.ok(result);
    }
}
