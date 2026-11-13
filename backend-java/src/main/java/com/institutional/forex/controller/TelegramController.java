package com.institutional.forex.controller;

import com.institutional.forex.engine.sync.FileDatabaseSyncService;
import com.institutional.forex.engine.telegram.TelegramNotificationService;
import com.institutional.forex.model.entity.UserSettingsEntity;
import com.institutional.forex.repository.UserSettingsRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/telegram")
public class TelegramController {

    private final TelegramNotificationService telegramService;
    private final UserSettingsRepository userSettingsRepository;
    private final FileDatabaseSyncService syncService;

    public TelegramController(TelegramNotificationService telegramService,
                              UserSettingsRepository userSettingsRepository,
                              FileDatabaseSyncService syncService) {
        this.telegramService = telegramService;
        this.userSettingsRepository = userSettingsRepository;
        this.syncService = syncService;
    }

    public record SaveTelegramConfigRequest(String botToken, String chatId, Boolean enabled) {}
    public record TestTelegramRequest(String botToken, String chatId) {}
    public record BroadcastMessageRequest(String message) {}

    @GetMapping("/config")
    public ResponseEntity<?> getConfig() {
        TelegramNotificationService.TelegramConfig cfg = telegramService.getConfig();
        String maskedToken = "";
        if (cfg.botToken() != null && cfg.botToken().length() > 8) {
            maskedToken = cfg.botToken().substring(0, 4) + "••••••••" + cfg.botToken().substring(cfg.botToken().length() - 4);
        } else if (cfg.botToken() != null && !cfg.botToken().isBlank()) {
            maskedToken = "••••••••";
        }

        return ResponseEntity.ok(Map.of(
                "botToken", cfg.botToken() != null ? cfg.botToken() : "",
                "maskedToken", maskedToken,
                "chatId", cfg.chatId() != null ? cfg.chatId() : "",
                "enabled", cfg.enabled(),
                "configured", (cfg.botToken() != null && !cfg.botToken().isBlank() && cfg.chatId() != null && !cfg.chatId().isBlank())
        ));
    }

    @PostMapping("/config")
    public ResponseEntity<?> saveConfig(@RequestBody SaveTelegramConfigRequest req) {
        UserSettingsEntity settings = userSettingsRepository.findByUserIdentifier("DEFAULT_USER")
                .orElseGet(() -> userSettingsRepository.save(new UserSettingsEntity()));

        if (req.botToken() != null) {
            settings.setTelegramBotToken(req.botToken().trim());
        }
        if (req.chatId() != null) {
            settings.setTelegramChatId(req.chatId().trim());
        }
        if (req.enabled() != null) {
            settings.setTelegramAlertsEnabled(req.enabled());
        }

        userSettingsRepository.save(settings);
        syncService.syncAllFiles();

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "تنظیمات ربات تلگرام با موفقیت ذخیره شد.",
                "configured", (settings.getTelegramBotToken() != null && !settings.getTelegramBotToken().isBlank() && settings.getTelegramChatId() != null && !settings.getTelegramChatId().isBlank())
        ));
    }

    @PostMapping("/test")
    public ResponseEntity<?> sendTestAlert(@RequestBody(required = false) TestTelegramRequest req) {
        String token = req != null ? req.botToken() : null;
        String chatId = req != null ? req.chatId() : null;

        TelegramNotificationService.TelegramDispatchResult result = telegramService.sendTestMessage(token, chatId);
        if (result.success()) {
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "پیام تست با موفقیت به چت تلگرام شما ارسال شد! ✅",
                    "httpCode", result.httpCode()
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILED",
                    "message", result.error() != null ? result.error() : "ارسال پیام تلگرام با شکست مواجه شد.",
                    "httpCode", result.httpCode(),
                    "rawResponse", result.response() != null ? result.response() : ""
            ));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<?> getAlertHistory() {
        return ResponseEntity.ok(telegramService.getRecentAlerts());
    }

    @PostMapping("/broadcast")
    public ResponseEntity<?> sendBroadcast(@RequestBody BroadcastMessageRequest req) {
        if (req.message() == null || req.message().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "متن پیام خالی است."));
        }
        TelegramNotificationService.TelegramDispatchResult result = telegramService.sendActiveAlert(req.message());
        if (result.success()) {
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "پیام با موفقیت مخابره شد."));
        } else {
            return ResponseEntity.badRequest().body(Map.of("status", "FAILED", "error", result.error()));
        }
    }
}

