package com.institutional.forex.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.institutional.forex.model.entity.*;
import com.institutional.forex.repository.*;
import com.quantfx.suite.engine.MacroSixPillarScorer;
import com.quantfx.suite.engine.PairSignalMatrixEngine;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/report")
public class ReportController {

    private final MacroIndicatorRepository macroRepository;
    private final ActiveTradeRepository activeTradeRepository;
    private final TradeJournalRepository tradeJournalRepository;
    private final PortfolioRepository portfolioRepository;
    private final MarketStructureRepository marketStructureRepository;
    private final PsychologyLogRepository psychologyRepository;
    private final EconomicEventRepository calendarRepository;
    private final MacroSixPillarScorer macroScorer;
    private final PairSignalMatrixEngine pairEngine;
    private final ObjectMapper objectMapper;

    public ReportController(MacroIndicatorRepository macroRepository,
                            ActiveTradeRepository activeTradeRepository,
                            TradeJournalRepository tradeJournalRepository,
                            PortfolioRepository portfolioRepository,
                            MarketStructureRepository marketStructureRepository,
                            PsychologyLogRepository psychologyRepository,
                            EconomicEventRepository calendarRepository,
                            MacroSixPillarScorer macroScorer,
                            PairSignalMatrixEngine pairEngine,
                            ObjectMapper objectMapper) {
        this.macroRepository = macroRepository;
        this.activeTradeRepository = activeTradeRepository;
        this.tradeJournalRepository = tradeJournalRepository;
        this.portfolioRepository = portfolioRepository;
        this.marketStructureRepository = marketStructureRepository;
        this.psychologyRepository = psychologyRepository;
        this.calendarRepository = calendarRepository;
        this.macroScorer = macroScorer;
        this.pairEngine = pairEngine;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(defaultValue = "30d") String period, // "24h", "7d", "30d", "90d", "all"
            @RequestParam(defaultValue = "json") String format  // "json" or "txt"
    ) {
        LocalDateTime cutoff = resolveCutoffDate(period);

        // Filter Trade Journals by period
        List<TradeJournalEntity> journal = tradeJournalRepository.findAll().stream()
                .filter(t -> t.getExitDateUtc() == null || t.getExitDateUtc().isAfter(cutoff))
                .sorted(Comparator.comparing(TradeJournalEntity::getExitDateUtc, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        // Active Trades
        List<ActiveTradeEntity> activeTrades = activeTradeRepository.findByStatus("OPEN");

        // Portfolio
        PortfolioEntity portfolio = portfolioRepository.findFirstByOrderByUpdatedAtUtcDesc()
                .orElse(new PortfolioEntity());

        // 8 Currency Macro Scores
        Map<String, Object> macroScores = new LinkedHashMap<>();
        for (String ccy : List.of("USD", "EUR", "GBP", "JPY", "AUD", "CAD", "CHF", "NZD")) {
            MacroSixPillarScorer.CurrencyMacroScore s = macroScorer.calculateCurrencyScore(ccy);
            macroScores.put(ccy, Map.of(
                    "score", s.finalScore(),
                    "regime", s.regime().name(),
                    "taylorRate", s.taylorInterestRate(),
                    "actualRate", s.actualInterestRate(),
                    "confidence", s.confidenceScore()
            ));
        }

        // 28 Pairs Signals
        List<PairSignalMatrixEngine.PairSignal> signals = pairEngine.evaluateAll28Pairs();

        // Psychology logs in period
        List<PsychologyLogEntity> psychology = psychologyRepository.findAll().stream()
                .filter(p -> p.getRecordedAtUtc() == null || p.getRecordedAtUtc().isAfter(cutoff))
                .collect(Collectors.toList());

        // Summary Calculations
        int totalTrades = journal.size();
        long winningTrades = journal.stream().filter(t -> t.getPnlUsd() != null && t.getPnlUsd() > 0).count();
        double winRate = totalTrades > 0 ? (double) winningTrades / totalTrades * 100.0 : 0.0;
        double netPnlUsd = journal.stream().mapToDouble(t -> t.getPnlUsd() != null ? t.getPnlUsd() : 0.0).sum();

        String filenameTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        if ("txt".equalsIgnoreCase(format)) {
            StringBuilder sb = new StringBuilder();
            sb.append("========================================================================================\n");
            sb.append("   FOREX INTELLIGENCE SUITE — INSTITUTIONAL QUANTITATIVE AUDIT REPORT\n");
            sb.append("   Generated At: ").append(LocalDateTime.now()).append(" | Filter Period: ").append(period.toUpperCase()).append("\n");
            sb.append("   Methodology: Anton Kreil 6-Pillars | 7-Gate Risk Protocol | Fractional Kelly\n");
            sb.append("========================================================================================\n\n");

            sb.append("--- 1. PORTFOLIO & PERFORMANCE METRICS ---\n");
            sb.append(String.format("Account Balance: $%.2f USD\n", portfolio.getAccountBalance()));
            sb.append(String.format("Account Equity:  $%.2f USD\n", portfolio.getEquity()));
            sb.append(String.format("Period Total Closed Trades: %d\n", totalTrades));
            sb.append(String.format("Winning Trades: %d (Win Rate: %.2f%%)\n", winningTrades, winRate));
            sb.append(String.format("Net Realized PnL: %+.2f USD\n", netPnlUsd));
            sb.append(String.format("Current Active Open Trades: %d\n\n", activeTrades.size()));

            sb.append("--- 2. ACTIVE OPEN POSITIONS (MT5 AUDIT) ---\n");
            if (activeTrades.isEmpty()) {
                sb.append("No active open positions.\n\n");
            } else {
                sb.append(String.format("%-10s %-8s %-8s %-12s %-12s %-12s %-12s\n",
                        "Pair", "Dir", "Lots", "Entry", "SL", "TP", "Unrealized$"));
                sb.append("-".repeat(80)).append("\n");
                for (ActiveTradeEntity at : activeTrades) {
                    sb.append(String.format("%-10s %-8s %-8.2f %-12.5f %-12.5f %-12.5f %-+12.2f\n",
                            at.getPair(), at.getDirection(), at.getLotSize(),
                            at.getEntryPrice(), at.getStopLoss(), at.getTakeProfit() != null ? at.getTakeProfit() : 0.0,
                            at.getUnrealizedPnlUsd() != null ? at.getUnrealizedPnlUsd() : 0.0));
                }
                sb.append("\n");
            }

            sb.append("--- 3. 6-PILLAR MACRO REGIME MATRIX (8 CURRENCIES) ---\n");
            sb.append(String.format("%-6s %-8s %-25s %-12s %-12s %-10s\n",
                    "CCY", "Score", "Macro Regime", "Taylor Rate", "Actual Rate", "Confidence"));
            sb.append("-".repeat(80)).append("\n");
            macroScores.forEach((ccy, data) -> {
                Map<?, ?> m = (Map<?, ?>) data;
                sb.append(String.format("%-6s %-+8.2f %-25s %-12.2f%% %-12.2f%% %-10.1f%%\n",
                        ccy, m.get("score"), m.get("regime"), m.get("taylorRate"), m.get("actualRate"), m.get("confidence")));
            });
            sb.append("\n");

            sb.append("--- 4. TOP 7-GATE CONFLUENCE PAIR SIGNALS ---\n");
            sb.append(String.format("%-10s %-24s %-10s %-12s %-15s %-15s\n",
                    "Pair", "Classification", "Delta", "Confidence", "Gate4(News)", "Gate5(Structure)"));
            sb.append("-".repeat(90)).append("\n");
            signals.stream().limit(10).forEach(p -> {
                sb.append(String.format("%-10s %-24s %-+10.2f %-12.1f%% %-15s %-15s\n",
                        p.pair(), p.signalClassification(), p.deltaScore(), p.overallConfidencePercent(),
                        p.gates().gate4NewsStatus().name(), p.gates().structureStatus()));
            });
            sb.append("\n");

            sb.append("--- 5. CLOSED TRADE JOURNAL AUDIT LOGS ---\n");
            if (journal.isEmpty()) {
                sb.append("No closed trades in the selected period.\n");
            } else {
                sb.append(String.format("%-10s %-6s %-6s %-10s %-10s %-12s %-8s %-10s\n",
                        "Pair", "Dir", "Lots", "Entry", "Exit", "PnL ($)", "PnL (R)", "Exit Date"));
                sb.append("-".repeat(80)).append("\n");
                for (TradeJournalEntity tj : journal) {
                    sb.append(String.format("%-10s %-6s %-6.2f %-10.5f %-10.5f %-+12.2f %-+8.1fR %-10s\n",
                            tj.getPair(), tj.getDirection(), tj.getLotSize() != null ? tj.getLotSize() : 0.0,
                            tj.getEntryPrice() != null ? tj.getEntryPrice() : 0.0,
                            tj.getExitPrice() != null ? tj.getExitPrice() : 0.0,
                            tj.getPnlUsd(), tj.getPnlR() != null ? tj.getPnlR() : 0.0,
                            tj.getExitDateUtc() != null ? tj.getExitDateUtc().format(DateTimeFormatter.ISO_LOCAL_DATE) : "N/A"));
                }
            }
            sb.append("\n============================= END OF QUANTITATIVE REPORT =============================\n");

            byte[] outputBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"forex_audit_report_" + filenameTimestamp + ".txt\"")
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(outputBytes);
        } else {
            // JSON format
            Map<String, Object> reportPayload = new LinkedHashMap<>();
            reportPayload.put("reportTitle", "Forex Intelligence Suite Institutional Quantitative Audit");
            reportPayload.put("generatedAtUtc", LocalDateTime.now().toString());
            reportPayload.put("filterPeriod", period);
            reportPayload.put("portfolio", portfolio);
            reportPayload.put("performanceSummary", Map.of(
                    "totalClosedTrades", totalTrades,
                    "winningTrades", winningTrades,
                    "winRatePercent", winRate,
                    "netRealizedPnlUsd", netPnlUsd
            ));
            reportPayload.put("activeTrades", activeTrades);
            reportPayload.put("macroSixPillarScores", macroScores);
            reportPayload.put("pairSignals", signals);
            reportPayload.put("tradeJournal", journal);
            reportPayload.put("psychologyLogs", psychology);

            try {
                byte[] jsonBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(reportPayload);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"forex_audit_report_" + filenameTimestamp + ".json\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(jsonBytes);
            } catch (Exception e) {
                return ResponseEntity.internalServerError().build();
            }
        }
    }

    private LocalDateTime resolveCutoffDate(String period) {
        LocalDateTime now = LocalDateTime.now();
        return switch (period.toLowerCase()) {
            case "24h" -> now.minusHours(24);
            case "7d" -> now.minusDays(7);
            case "30d" -> now.minusDays(30);
            case "90d" -> now.minusDays(90);
            default -> LocalDateTime.of(2000, 1, 1, 0, 0); // "all"
        };
    }
}
