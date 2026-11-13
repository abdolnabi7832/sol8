package com.institutional.forex.controller;

import com.institutional.forex.engine.gemini.GeminiKeyPoolService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * GeminiController: REST API endpoints for Gemini Decision 1 AI,
 * Multi-Key Rotation Pool, live institutional evaluation, and hybrid backtests.
 */
@RestController
@RequestMapping("/api/v1/gemini")
public class GeminiController {

    private final GeminiKeyPoolService keyPoolService;

    public GeminiController(GeminiKeyPoolService keyPoolService) {
        this.keyPoolService = keyPoolService;
    }

    @GetMapping("/keys")
    public ResponseEntity<List<Map<String, Object>>> listKeys() {
        return ResponseEntity.ok(keyPoolService.listKeys());
    }

    @PostMapping("/keys")
    public ResponseEntity<?> addKey(@RequestBody Map<String, String> body) {
        String key = body.get("apiKey");
        String label = body.get("label");
        String model = body.get("model");
        if (key == null || key.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "API Key is required."));
        }
        var entry = keyPoolService.addKey(label, key, model);
        return ResponseEntity.ok(entry.toSanitizedMap());
    }

    @DeleteMapping("/keys/{id}")
    public ResponseEntity<?> deleteKey(@PathVariable String id) {
        boolean ok = keyPoolService.deleteKey(id);
        return ResponseEntity.ok(Map.of("success", ok, "id", id));
    }

    @PostMapping("/keys/{id}/test")
    public ResponseEntity<?> testKey(@PathVariable String id) {
        Map<String, Object> res = keyPoolService.testKey(id);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/decision")
    public ResponseEntity<?> evaluateDecision(@RequestBody Map<String, Object> payload) {
        String pair = (String) payload.getOrDefault("pair", "EURUSD");
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) payload.getOrDefault("context", payload);
        Map<String, Object> decision = keyPoolService.evaluateDecision(pair, context);
        return ResponseEntity.ok(decision);
    }

    @PostMapping("/backtest")
    public ResponseEntity<?> runBacktest(@RequestBody Map<String, Object> payload) {
        String pair = (String) payload.getOrDefault("pair", "EURUSD");
        Map<String, Object> backtestResult = keyPoolService.runAiStrategyBacktest(pair);
        return ResponseEntity.ok(backtestResult);
    }
}

