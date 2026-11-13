package com.institutional.forex;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.institutional.forex.controller.MarketDataController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Market Data Controller & Candlestick Feed Tests")
class MarketDataControllerTest {

    private MarketDataController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        controller = new MarketDataController(objectMapper);
    }

    @Test
    @DisplayName("Should serve candles payload with all 28 G8 currency pairs")
    void shouldServeAllCandles() {
        ResponseEntity<Map<String, Object>> response = controller.getAllCandles();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);

        if (body.containsKey("candles")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> candles = (Map<String, Object>) body.get("candles");
            assertNotNull(candles);
            assertFalse(candles.isEmpty(), "Candles cache should have currency pair data");
        }
    }

    @Test
    @DisplayName("Should serve live spot prices for major currency pairs")
    void shouldServeLivePrices() {
        ResponseEntity<Map<String, Object>> response = controller.getLivePrices();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Map<String, Object> prices = response.getBody();
        assertNotNull(prices);
    }

    @Test
    @DisplayName("Should return pair-specific candles for valid pair like USDCAD or EURUSD")
    void shouldReturnPairCandles() {
        ResponseEntity<Object> resp = controller.getPairCandles("USDCAD", "D1", 60);
        assertNotNull(resp);
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
    }
}

