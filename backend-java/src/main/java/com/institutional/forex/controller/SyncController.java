package com.institutional.forex.controller;

import com.institutional.forex.engine.coordinator.LiveSyncCoordinatorService;
import com.institutional.forex.model.dto.SyncSessionStatus;
import com.institutional.forex.model.dto.SyncSessionStatus.SyncChangeLog;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * SyncController: Institutional REST & SSE Endpoints delivering live sync telemetry,
 * real-time change diffs, and non-blocking background synchronization triggers.
 */
@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final LiveSyncCoordinatorService liveSyncService;

    public SyncController(LiveSyncCoordinatorService liveSyncService) {
        this.liveSyncService = liveSyncService;
    }

    /**
     * Pollable snapshot of current sync session status, task progress, and currency states.
     */
    @GetMapping("/status")
    public ResponseEntity<SyncSessionStatus> getSyncStatus() {
        return ResponseEntity.ok(liveSyncService.getCurrentStatus());
    }

    /**
     * Real-time Server-Sent Events (SSE) telemetry stream pushing live progress updates.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSyncTelemetry() {
        SseEmitter emitter = new SseEmitter(180_000L); // 3 minutes timeout
        liveSyncService.registerSseEmitter(emitter);
        return emitter;
    }

    /**
     * Starts the institutional live synchronization pipeline asynchronously.
     */
    @PostMapping("/start")
    public ResponseEntity<SyncSessionStatus> startSyncPipeline() {
        liveSyncService.triggerLiveSyncAsync();
        return ResponseEntity.accepted().body(liveSyncService.getCurrentStatus());
    }

    /**
     * Retrieves the list of newly discovered data changes (diffs) from the latest synchronization run.
     */
    @GetMapping("/changelogs")
    public ResponseEntity<List<SyncChangeLog>> getRecentChangeLogs() {
        return ResponseEntity.ok(liveSyncService.getCurrentStatus().getChangeLogs());
    }

    @GetMapping(value = {"/health", "/api/v1/telemetry/health"})
    public ResponseEntity<java.util.Map<String, Object>> getHealth() {
        return ResponseEntity.ok(java.util.Map.of(
                "status", "UP",
                "service", "Forex Intelligence Suite Enterprise",
                "version", "v6.0 PRO",
                "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }
}
