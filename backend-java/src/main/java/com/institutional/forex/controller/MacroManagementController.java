package com.institutional.forex.controller;

import com.institutional.forex.engine.sync.FileDatabaseSyncService;
import com.institutional.forex.model.dto.MacroIndicatorDto;
import com.institutional.forex.model.entity.MacroIndicatorEntity;
import com.institutional.forex.repository.MacroIndicatorRepository;
import com.quantfx.suite.engine.CurrencySpecificProfile.ScoringMode;
import com.quantfx.suite.engine.MacroSixPillarScorer;
import com.quantfx.suite.engine.MacroSixPillarScorer.CurrencyMacroScore;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * MacroManagementController: Enables quantitative traders to update indicator values,
 * release dates, and switch operational modes (AUTO/MANUAL/HYBRID) with immediate
 * dual-storage synchronization and WebSocket market broadcasts.
 */
@RestController
@RequestMapping("/api/v1/macro")
public class MacroManagementController {

    private final MacroIndicatorRepository macroIndicatorRepository;
    private final MacroSixPillarScorer macroScorer;
    private final FileDatabaseSyncService syncService;
    private final SimpMessagingTemplate messagingTemplate;

    public MacroManagementController(MacroIndicatorRepository macroIndicatorRepository,
                                     MacroSixPillarScorer macroScorer,
                                     FileDatabaseSyncService syncService,
                                     SimpMessagingTemplate messagingTemplate) {
        this.macroIndicatorRepository = macroIndicatorRepository;
        this.macroScorer = macroScorer;
        this.syncService = syncService;
        this.messagingTemplate = messagingTemplate;
    }

    public record MacroUpdateRequest(
            String scoringMode, // AUTO, MANUAL, HYBRID
            Map<String, Double> indicatorValues, // Name -> Value
            Map<String, String> releaseDates // Name -> RawDate or ISO
    ) {}

    @PutMapping("/{ccy}")
    public ResponseEntity<CurrencyMacroScore> updateMacroData(
            @PathVariable String ccy,
            @RequestBody MacroUpdateRequest request) {

        String currency = ccy.toUpperCase();
        List<MacroIndicatorEntity> entities = macroIndicatorRepository
                .findByCurrencyOrderByPillarNumberAscIndicatorNameAsc(currency);

        if (entities.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        if (request.indicatorValues() != null) {
            for (MacroIndicatorEntity entity : entities) {
                String name = entity.getIndicatorName();
                if (request.indicatorValues().containsKey(name)) {
                    Double newVal = request.indicatorValues().get(name);
                    if (newVal != null) {
                        entity.setPreviousValue(entity.getLastValue());
                        entity.setLastValue(newVal);
                        entity.setUpdatedAtUtc(LocalDateTime.now());
                        macroIndicatorRepository.save(entity);
                    }
                }
            }
        }

        if (request.releaseDates() != null) {
            for (MacroIndicatorEntity entity : entities) {
                String name = entity.getIndicatorName();
                if (request.releaseDates().containsKey(name)) {
                    entity.setReleaseDateRaw(request.releaseDates().get(name));
                    macroIndicatorRepository.save(entity);
                }
            }
        }

        // Dual storage persistence to Excel_Logs
        syncService.syncAllFiles();

        ScoringMode mode = ScoringMode.AUTO;
        if (request.scoringMode() != null) {
            try {
                mode = ScoringMode.valueOf(request.scoringMode().toUpperCase());
            } catch (Exception ignored) {}
        }

        CurrencyMacroScore newScore = macroScorer.calculateCurrencyScore(currency, LocalDateTime.now(), mode,
                request.indicatorValues() != null ? request.indicatorValues() : Map.of());

        // Broadcast update via WebSocket
        try {
            messagingTemplate.convertAndSend("/topic/macro-updates", Map.of(
                    "currency", currency,
                    "score", newScore.finalScore(),
                    "regime", newScore.regime().name(),
                    "timestamp", LocalDateTime.now().toString()
            ));
        } catch (Exception ignored) {}

        return ResponseEntity.ok(newScore);
    }
}
