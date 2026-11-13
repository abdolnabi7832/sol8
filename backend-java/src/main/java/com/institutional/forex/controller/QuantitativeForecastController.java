package com.institutional.forex.controller;

import com.institutional.forex.registry.OmniIndicatorRegistry;
import com.quantfx.suite.engine.CurrencySpecificProfile.ScoringMode;
import com.quantfx.suite.engine.MacroSixPillarScorer;
import com.quantfx.suite.engine.MacroSixPillarScorer.CurrencyMacroScore;
import com.quantfx.suite.engine.PredictiveNowcastEngine;
import com.quantfx.suite.engine.PredictiveNowcastEngine.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * QuantitativeForecastController: Institutional REST API providing access to
 * the 6-Pillar Macro Scoring v2.0, Taylor Rule deviations, Nowcasting,
 * and 3-scenario simulations.
 */
@RestController
@RequestMapping("/api/v1/quant")
public class QuantitativeForecastController {

    private final MacroSixPillarScorer scorer;
    private final PredictiveNowcastEngine nowcastEngine;
    private final OmniIndicatorRegistry indicatorRegistry;

    public QuantitativeForecastController(MacroSixPillarScorer scorer,
                                         PredictiveNowcastEngine nowcastEngine,
                                         OmniIndicatorRegistry indicatorRegistry) {
        this.scorer = scorer;
        this.nowcastEngine = nowcastEngine;
        this.indicatorRegistry = indicatorRegistry;
    }

    /**
     * Retrieves overall quantitative macro score, economic regime, and Taylor rule
     * deviation for all 8 institutional G8 currencies.
     */
    @GetMapping("/scores")
    public ResponseEntity<List<CurrencyMacroScore>> getAllCurrencyScores() {
        List<CurrencyMacroScore> scores = new ArrayList<>();
        for (String ccy : indicatorRegistry.getSupportedCurrencies()) {
            scores.add(scorer.calculateCurrencyScore(ccy));
        }
        // Sort descending by macro strength
        scores.sort((a, b) -> Double.compare(b.finalScore(), a.finalScore()));
        return ResponseEntity.ok(scores);
    }

    /**
     * Retrieves detailed 6-pillar breakdown, Z-scores, momentum, surprise, and audit logs
     * for a single currency.
     */
    @GetMapping("/scores/{currency}")
    public ResponseEntity<CurrencyMacroScore> getCurrencyScoreDetail(
            @PathVariable String currency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime referenceTimeUtc) {

        String ccy = currency.toUpperCase();
        if (!indicatorRegistry.isCurrencySupported(ccy)) {
            return ResponseEntity.badRequest().build();
        }

        LocalDateTime ref = referenceTimeUtc != null ? referenceTimeUtc : LocalDateTime.now();
        CurrencyMacroScore score = scorer.calculateCurrencyScore(ccy, ref, ScoringMode.AUTO, Map.of());
        return ResponseEntity.ok(score);
    }

    /**
     * Calculates interactive simulated currency score with custom overrides (HYBRID mode).
     */
    @PostMapping("/scores/{currency}/simulate")
    public ResponseEntity<CurrencyMacroScore> simulateCurrencyScore(
            @PathVariable String currency,
            @RequestBody Map<String, Double> overrides,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime referenceTimeUtc) {

        String ccy = currency.toUpperCase();
        if (!indicatorRegistry.isCurrencySupported(ccy)) {
            return ResponseEntity.badRequest().build();
        }

        LocalDateTime ref = referenceTimeUtc != null ? referenceTimeUtc : LocalDateTime.now();
        CurrencyMacroScore score = scorer.calculateCurrencyScore(ccy, ref, ScoringMode.HYBRID, overrides);
        return ResponseEntity.ok(score);
    }

    /**
     * Nowcasts indicator release before official news publication.
     */
    @GetMapping("/nowcast/{currency}/{indicator}")
    public ResponseEntity<NowcastPrediction> getNowcastPrediction(
            @PathVariable String currency,
            @PathVariable String indicator) {

        String ccy = currency.toUpperCase();
        if (!indicatorRegistry.isCurrencySupported(ccy)) {
            return ResponseEntity.badRequest().build();
        }

        NowcastPrediction nowcast = nowcastEngine.nowcastIndicator(ccy, indicator);
        return ResponseEntity.ok(nowcast);
    }

    /**
     * Generates a 3-scenario branch simulation (BULLISH_BEAT, BASELINE_INLINE, BEARISH_MISS)
     * for an upcoming calendar event.
     */
    @GetMapping("/simulate-event/{eventId}")
    public ResponseEntity<EventSimulationResult> simulateEvent(@PathVariable String eventId) {
        try {
            EventSimulationResult result = nowcastEngine.simulateUpcomingEvent(eventId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    public record CustomScenarioRequest(
            String targetCurrency,
            Map<String, Double> customOverrides,
            LocalDateTime futureSimulatedDate
    ) {}

    /**
     * Interactive scenario simulator evaluating custom shifts across all 8 currencies
     * and re-ranking all 28 currency pairs.
     */
    @PostMapping("/simulate-custom-scenario")
    public ResponseEntity<CustomScenarioSimulation> simulateCustomScenario(
            @RequestBody CustomScenarioRequest request) {

        CustomScenarioSimulation result = nowcastEngine.simulateCustomScenario(
                request.targetCurrency(),
                request.customOverrides(),
                request.futureSimulatedDate()
        );
        return ResponseEntity.ok(result);
    }
}
