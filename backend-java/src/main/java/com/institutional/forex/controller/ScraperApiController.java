package com.institutional.forex.controller;

import com.institutional.forex.engine.coordinator.DataIngestionCoordinator;
import com.institutional.forex.engine.coordinator.LiveSyncCoordinatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * ScraperApiController: Triggers immediate multi-source macro & calendar scraping
 * and broadcasts real-time result over WebSocket and Live Sync Telemetry.
 */
@RestController
@RequestMapping("/api/v1/scraper")
public class ScraperApiController {

    private final DataIngestionCoordinator coordinator;
    private final LiveSyncCoordinatorService liveSyncService;
    private final SimpMessagingTemplate messagingTemplate;

    public ScraperApiController(DataIngestionCoordinator coordinator,
                                LiveSyncCoordinatorService liveSyncService,
                                SimpMessagingTemplate messagingTemplate) {
        this.coordinator = coordinator;
        this.liveSyncService = liveSyncService;
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping("/sync-all")
    public ResponseEntity<Map<String, Object>> triggerSyncAll() {
        liveSyncService.triggerLiveSyncAsync();

        return ResponseEntity.ok(Map.of(
                "status", "DISPATCHED",
                "message", "Institutional Live Sync Telemetry pipeline launched. Real-time stream active at /api/v1/sync/stream."
        ));
    }
}
