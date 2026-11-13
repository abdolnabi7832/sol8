package com.institutional.forex.controller;

import com.institutional.forex.engine.normalizer.CalendarFilterAndNormalizer;
import com.institutional.forex.model.dto.EconomicEventDto;
import com.institutional.forex.model.dto.MacroIndicatorDto;
import com.institutional.forex.model.entity.*;
import com.institutional.forex.registry.OmniIndicatorRegistry;
import com.institutional.forex.repository.*;
import com.quantfx.suite.engine.MacroSixPillarScorer;
import com.quantfx.suite.engine.MacroSixPillarScorer.CurrencyMacroScore;
import com.quantfx.suite.engine.MonteCarloRiskEngine;
import com.quantfx.suite.engine.MonteCarloRiskEngine.PortfolioRiskSnapshot;
import com.quantfx.suite.engine.PairSignalMatrixEngine;
import com.quantfx.suite.engine.PairSignalMatrixEngine.PairSignal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * BootstrapController: Delivers the entire unified institutional state of the
 * command center in a single high-performance payload for frontend instant bootstrap.
 */
@RestController
@RequestMapping("/api/v1/bootstrap")
public class BootstrapController {

    private final MacroIndicatorRepository macroIndicatorRepository;
    private final EconomicEventRepository economicEventRepository;
    private final PortfolioRepository portfolioRepository;
    private final ActiveTradeRepository activeTradeRepository;
    private final TradeJournalRepository tradeJournalRepository;
    private final PsychologyLogRepository psychologyLogRepository;
    private final MarketStructureRepository marketStructureRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final OmniIndicatorRegistry indicatorRegistry;
    private final MacroSixPillarScorer macroScorer;
    private final PairSignalMatrixEngine pairEngine;
    private final MonteCarloRiskEngine riskEngine;
    private final CalendarFilterAndNormalizer calendarNormalizer;

    public BootstrapController(MacroIndicatorRepository macroIndicatorRepository,
                               EconomicEventRepository economicEventRepository,
                               PortfolioRepository portfolioRepository,
                               ActiveTradeRepository activeTradeRepository,
                               TradeJournalRepository tradeJournalRepository,
                               PsychologyLogRepository psychologyLogRepository,
                               MarketStructureRepository marketStructureRepository,
                               UserSettingsRepository userSettingsRepository,
                               OmniIndicatorRegistry indicatorRegistry,
                               MacroSixPillarScorer macroScorer,
                               PairSignalMatrixEngine pairEngine,
                               MonteCarloRiskEngine riskEngine,
                               CalendarFilterAndNormalizer calendarNormalizer) {
        this.macroIndicatorRepository = macroIndicatorRepository;
        this.economicEventRepository = economicEventRepository;
        this.portfolioRepository = portfolioRepository;
        this.activeTradeRepository = activeTradeRepository;
        this.tradeJournalRepository = tradeJournalRepository;
        this.psychologyLogRepository = psychologyLogRepository;
        this.marketStructureRepository = marketStructureRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.indicatorRegistry = indicatorRegistry;
        this.macroScorer = macroScorer;
        this.pairEngine = pairEngine;
        this.riskEngine = riskEngine;
        this.calendarNormalizer = calendarNormalizer;
    }

    public record BootstrapPayload(
            Map<String, List<MacroIndicatorDto>> macroIndicatorsByCurrency,
            Map<String, CurrencyMacroScore> currencyMacroScores,
            List<PairSignal> pairSignals,
            List<EconomicEventDto> economicCalendar,
            PortfolioRiskSnapshot portfolioRisk,
            List<ActiveTradeEntity> activeTrades,
            List<TradeJournalEntity> tradeJournal,
            List<PsychologyLogEntity> psychologyLogs,
            List<MarketStructureEntity> marketStructures,
            UserSettingsEntity userSettings,
            LocalDateTime systemTimestampUtc
    ) {}

    @GetMapping
    public ResponseEntity<BootstrapPayload> getBootstrapState() {
        // 1. Indicators grouped by currency
        Map<String, List<MacroIndicatorDto>> indicatorsByCcy = new LinkedHashMap<>();
        Map<String, CurrencyMacroScore> scoresByCcy = new LinkedHashMap<>();

        for (String ccy : indicatorRegistry.getSupportedCurrencies()) {
            List<MacroIndicatorEntity> entities = macroIndicatorRepository
                    .findByCurrencyOrderByPillarNumberAscIndicatorNameAsc(ccy);
            indicatorsByCcy.put(ccy, entities.stream().map(MacroIndicatorDto::fromEntity).collect(Collectors.toList()));
            scoresByCcy.put(ccy, macroScorer.calculateCurrencyScore(ccy));
        }

        // 2. 28 Pair signals through 7-Gate Risk Shield
        List<PairSignal> pairSignals = pairEngine.evaluateAll28Pairs();

        // 3. Calendar events (normalized for impact level & UTC timestamps)
        List<EconomicEventEntity> calEvents = economicEventRepository.findAll();
        for (EconomicEventEntity ev : calEvents) {
            calendarNormalizer.classifyAndNormalize(ev);
        }
        List<EconomicEventDto> calDtos = calEvents.stream()
                .map(EconomicEventDto::fromEntity)
                .collect(Collectors.toList());

        // 4. Portfolio risk snapshot
        PortfolioRiskSnapshot riskSnapshot = riskEngine.generatePortfolioRiskSnapshot();

        // 5. Active Trades, Journal, Psychology, Market Structure, Settings
        List<ActiveTradeEntity> openTrades = activeTradeRepository.findAll();
        List<TradeJournalEntity> journalEntries = tradeJournalRepository.findAllByOrderByCreatedAtUtcDesc();
        List<PsychologyLogEntity> psychologyLogs = psychologyLogRepository.findAllByOrderByRecordedAtUtcDesc();
        List<MarketStructureEntity> structures = marketStructureRepository.findAll();
        UserSettingsEntity settings = userSettingsRepository.findByUserIdentifier("DEFAULT_USER")
                .orElse(new UserSettingsEntity());

        BootstrapPayload payload = new BootstrapPayload(
                indicatorsByCcy,
                scoresByCcy,
                pairSignals,
                calDtos,
                riskSnapshot,
                openTrades,
                journalEntries,
                psychologyLogs,
                structures,
                settings,
                LocalDateTime.now()
        );

        return ResponseEntity.ok(payload);
    }
}
