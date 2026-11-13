package com.institutional.forex.controller;

import com.institutional.forex.model.dto.EconomicEventDto;
import com.institutional.forex.model.entity.EconomicEventEntity;
import com.quantfx.suite.engine.PredictiveNowcastEngine;
import com.quantfx.suite.engine.PredictiveNowcastEngine.CustomScenarioSimulation;
import com.quantfx.suite.engine.PredictiveNowcastEngine.EventSimulationResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * PredictiveScenarioApiController: REST endpoints executing future event nowcasts,
 * filtering simulatable quantitative releases, and what-if scenario simulations.
 */
@RestController
@RequestMapping("/api/v1/predict")
public class PredictiveScenarioApiController {

    private final PredictiveNowcastEngine nowcastEngine;

    public PredictiveScenarioApiController(PredictiveNowcastEngine nowcastEngine) {
        this.nowcastEngine = nowcastEngine;
    }

    /**
     * Returns strictly quant-simulatable upcoming events with HIGH or MED impact.
     */
    @GetMapping("/simulatable-events")
    public ResponseEntity<List<EconomicEventDto>> getSimulatableEvents(
            @RequestParam(required = false, defaultValue = "ALL") String currency) {
        List<EconomicEventEntity> entities = nowcastEngine.getSimulatableUpcomingEvents(currency);
        List<EconomicEventDto> dtos = entities.stream().map(EconomicEventDto::fromEntity).toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/event/{eventId}")
    public ResponseEntity<?> simulateEvent(@PathVariable String eventId) {
        try {
            EventSimulationResult result = nowcastEngine.simulateUpcomingEvent(eventId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "REJECTED_NON_QUANT",
                    "error", e.getMessage()
            ));
        }
    }

    public record WhatIfScenarioRequest(
            String targetCurrency,
            Map<String, Double> customOverrides,
            LocalDateTime futureSimulatedDate
    ) {}

    @PostMapping("/what-if")
    public ResponseEntity<CustomScenarioSimulation> simulateWhatIf(@RequestBody WhatIfScenarioRequest request) {
        CustomScenarioSimulation result = nowcastEngine.simulateCustomScenario(
                request.targetCurrency(),
                request.customOverrides(),
                request.futureSimulatedDate()
        );
        return ResponseEntity.ok(result);
    }
}
