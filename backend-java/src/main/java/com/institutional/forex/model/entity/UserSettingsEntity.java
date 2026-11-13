package com.institutional.forex.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_settings")
public class UserSettingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_identifier", nullable = false, unique = true, length = 64)
    private String userIdentifier = "DEFAULT_USER";

    @Column(name = "risk_per_trade_percent", nullable = false)
    private Double riskPerTradePercent = 1.0;

    @Column(name = "max_single_currency_exposure_percent", nullable = false)
    private Double maxSingleCurrencyExposurePercent = 2.0;

    @Column(name = "max_total_portfolio_risk_percent", nullable = false)
    private Double maxTotalPortfolioRiskPercent = 5.0;

    @Column(name = "default_lot_size", nullable = false)
    private Double defaultLotSize = 0.10;

    @Column(name = "gemini_api_key", length = 512)
    private String geminiApiKey = System.getenv().getOrDefault("GEMINI_API_KEY", "");

    @Column(name = "preferred_language", nullable = false, length = 8)
    private String preferredLanguage = "fa"; // "fa" or "en"

    @Column(name = "theme", length = 16)
    private String theme = "DARK";

    @Column(name = "sound_alerts_enabled")
    private Boolean soundAlertsEnabled = true;

    @Column(name = "auto_sync_excel_logs")
    private Boolean autoSyncExcelLogs = true;

    @Column(name = "username", length = 64)
    private String username = "admin";

    @Column(name = "password_hash", length = 128)
    private String passwordHash = "admin";

    @Column(name = "auth_token", length = 256)
    private String authToken;

    @Column(name = "telegram_bot_token", length = 256)
    private String telegramBotToken = "9255473348:AAFiBI_x37LLxw-QWP8Abpsact2I6iuAoBU";

    @Column(name = "telegram_chat_id", length = 128)
    private String telegramChatId = "5792130899";

    @Column(name = "telegram_alerts_enabled")
    private Boolean telegramAlertsEnabled = true;

    @Column(name = "updated_at_utc", nullable = false)
    private LocalDateTime updatedAtUtc;

    public UserSettingsEntity() {
        this.updatedAtUtc = LocalDateTime.now();
    }

    @PrePersist
    @PreUpdate
    public void onUpdate() {
        this.updatedAtUtc = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUserIdentifier() { return userIdentifier; }
    public void setUserIdentifier(String userIdentifier) { this.userIdentifier = userIdentifier; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }
    public Double getRiskPerTradePercent() { return riskPerTradePercent; }
    public void setRiskPerTradePercent(Double riskPerTradePercent) { this.riskPerTradePercent = riskPerTradePercent; }
    public Double getMaxSingleCurrencyExposurePercent() { return maxSingleCurrencyExposurePercent; }
    public void setMaxSingleCurrencyExposurePercent(Double maxSingleCurrencyExposurePercent) { this.maxSingleCurrencyExposurePercent = maxSingleCurrencyExposurePercent; }
    public Double getMaxTotalPortfolioRiskPercent() { return maxTotalPortfolioRiskPercent; }
    public void setMaxTotalPortfolioRiskPercent(Double maxTotalPortfolioRiskPercent) { this.maxTotalPortfolioRiskPercent = maxTotalPortfolioRiskPercent; }
    public Double getDefaultLotSize() { return defaultLotSize; }
    public void setDefaultLotSize(Double defaultLotSize) { this.defaultLotSize = defaultLotSize; }
    public String getGeminiApiKey() { return geminiApiKey; }
    public void setGeminiApiKey(String geminiApiKey) { this.geminiApiKey = geminiApiKey; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    public Boolean getSoundAlertsEnabled() { return soundAlertsEnabled; }
    public void setSoundAlertsEnabled(Boolean soundAlertsEnabled) { this.soundAlertsEnabled = soundAlertsEnabled; }
    public Boolean getAutoSyncExcelLogs() { return autoSyncExcelLogs; }
    public void setAutoSyncExcelLogs(Boolean autoSyncExcelLogs) { this.autoSyncExcelLogs = autoSyncExcelLogs; }
    public String getTelegramBotToken() { return telegramBotToken; }
    public void setTelegramBotToken(String telegramBotToken) { this.telegramBotToken = telegramBotToken; }
    public String getTelegramChatId() { return telegramChatId; }
    public void setTelegramChatId(String telegramChatId) { this.telegramChatId = telegramChatId; }
    public Boolean getTelegramAlertsEnabled() { return telegramAlertsEnabled != null ? telegramAlertsEnabled : true; }
    public void setTelegramAlertsEnabled(Boolean telegramAlertsEnabled) { this.telegramAlertsEnabled = telegramAlertsEnabled; }
    public LocalDateTime getUpdatedAtUtc() { return updatedAtUtc; }
    public void setUpdatedAtUtc(LocalDateTime updatedAtUtc) { this.updatedAtUtc = updatedAtUtc; }
}
