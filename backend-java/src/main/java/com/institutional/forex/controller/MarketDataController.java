package com.institutional.forex.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;

/**
 * MarketDataController: Serves real MT5 candlestick history, live spot rates,
 * and market telemetry to the frontend workstation and strategy engines.
 */
@RestController
@RequestMapping("/api/v1/market")
public class MarketDataController {

    private static final Logger log = LoggerFactory.getLogger(MarketDataController.class);
    private static final String CANDLES_PATH = "./data/Excel_Logs/mt5_candles.json";
    private static final String MT5_DAEMON_URL = "http://127.0.0.1:8085/api/mt5/all_candles";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public MarketDataController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(800))
                .build();
    }

    @GetMapping("/candles")
    public ResponseEntity<Map<String, Object>> getAllCandles() {
        // 1. Try reading from active local JSON cache
        File file = new File(CANDLES_PATH);
        if (file.exists() && file.length() > 100) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = objectMapper.readValue(file, Map.class);
                Map<?, ?> candles = (Map<?, ?>) data.get("candles");
                if (candles != null && !candles.isEmpty()) {
                    return ResponseEntity.ok(data);
                }
            } catch (Exception e) {
                log.warn("[MarketData] Error reading {}: {}", CANDLES_PATH, e.getMessage());
            }
        }

        // 2. Fallback: Query MT5 Python daemon on port 8085
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(MT5_DAEMON_URL))
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = objectMapper.readValue(resp.body(), Map.class);
                return ResponseEntity.ok(data);
            }
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Collections.emptyMap());
    }

    @GetMapping("/prices")
    public ResponseEntity<Map<String, Object>> getLivePrices() {
        File file = new File(CANDLES_PATH);
        if (file.exists() && file.length() > 100) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = objectMapper.readValue(file, Map.class);
                @SuppressWarnings("unchecked")
                Map<String, Object> rates = (Map<String, Object>) data.get("rates");
                if (rates != null && !rates.isEmpty()) {
                    return ResponseEntity.ok(rates);
                }
            } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(Collections.emptyMap());
    }

    @GetMapping("/candles/{pair}")
    public ResponseEntity<Object> getPairCandles(
            @PathVariable String pair,
            @RequestParam(required = false, defaultValue = "D1") String timeframe,
            @RequestParam(required = false, defaultValue = "60") int count) {
        
        // 1. Check local cache first for D1, H4, and H1
        File file = new File(CANDLES_PATH);
        if (file.exists() && file.length() > 100) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = objectMapper.readValue(file, Map.class);
                String mapKey = "D1".equalsIgnoreCase(timeframe) ? "candles"
                        : ("H4".equalsIgnoreCase(timeframe) ? "candles_h4"
                        : ("H1".equalsIgnoreCase(timeframe) ? "candles_h1" : "candles"));
                @SuppressWarnings("unchecked")
                Map<String, Object> candlesMap = (Map<String, Object>) data.get(mapKey);
                if (candlesMap != null && candlesMap.containsKey(pair.toUpperCase())) {
                    Object list = candlesMap.get(pair.toUpperCase());
                    if (list instanceof java.util.List && !((java.util.List<?>) list).isEmpty()) {
                        return ResponseEntity.ok(list);
                    }
                }
            } catch (Exception ignored) {}
        }

        // 2. Query MT5 daemon for real/live timeframe candles (dynamic fallback)
        try {
            String daemonUrl = "http://127.0.0.1:8085/api/mt5/candles?symbol=" + pair.toUpperCase() 
                    + "&timeframe=" + timeframe.toUpperCase() + "&count=" + count;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(daemonUrl))
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                Object data = objectMapper.readValue(resp.body(), Object.class);
                return ResponseEntity.ok(data);
            }
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Collections.emptyList());
    }
}

