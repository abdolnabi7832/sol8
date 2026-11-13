package com.institutional.forex.controller;

import com.institutional.forex.engine.coordinator.DataIngestionCoordinator;
import com.institutional.forex.engine.sync.FileDatabaseSyncService;
import com.institutional.forex.model.dto.IngestionStatusDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * IngestionController: Operations and health monitoring for data scraping and disk synchronization.
 */
@RestController
@RequestMapping("/api/v1/ingestion")
public class IngestionController {

    private final DataIngestionCoordinator coordinator;
    private final FileDatabaseSyncService fileDatabaseSyncService;

    public IngestionController(DataIngestionCoordinator coordinator,
                               FileDatabaseSyncService fileDatabaseSyncService) {
        this.coordinator = coordinator;
        this.fileDatabaseSyncService = fileDatabaseSyncService;
    }

    /**
     * Manually triggers the full data ingestion and synchronization pipeline asynchronously.
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> triggerIngestion() {
        Thread.ofVirtual().start(coordinator::triggerIngestionPipeline);
        return ResponseEntity.ok(Map.of(
                "status", "TRIGGERED",
                "message", "Stealth data ingestion pipeline launched in virtual background thread"
        ));
    }

    /**
     * Retrieves the current diagnostic execution status of the ingestion pipeline.
     */
    @GetMapping("/status")
    public ResponseEntity<IngestionStatusDto> getStatus() {
        return ResponseEntity.ok(coordinator.getStatus());
    }

    /**
     * Manually triggers dual storage file synchronization (JSON/CSV).
     */
    @PostMapping("/sync-files")
    public ResponseEntity<Map<String, String>> syncFiles() {
        Map<String, String> syncResult = fileDatabaseSyncService.syncAllFiles();
        return ResponseEntity.ok(syncResult);
    }
}
