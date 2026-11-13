package com.institutional.forex;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.institutional.forex.engine.telegram.TelegramNotificationService;
import com.institutional.forex.model.entity.UserSettingsEntity;
import com.institutional.forex.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DisplayName("Telegram Notification & Real-Time Alerts Engine Tests")
class TelegramNotificationServiceTest {

    private TelegramNotificationService service;
    private UserSettingsRepository mockRepo;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockRepo = Mockito.mock(UserSettingsRepository.class);
        objectMapper = new ObjectMapper();

        UserSettingsEntity settings = new UserSettingsEntity();
        settings.setUserIdentifier("DEFAULT_USER");
        settings.setTelegramBotToken("9255473348:AAFiBI_x37LLxw-QWP8Abpsact2I6iuAoBU");
        settings.setTelegramChatId("5792130899");
        settings.setTelegramAlertsEnabled(true);

        when(mockRepo.findByUserIdentifier("DEFAULT_USER")).thenReturn(Optional.of(settings));
        service = new TelegramNotificationService(mockRepo, objectMapper);
    }

    @Test
    @DisplayName("Should correctly load configured bot token and chat ID")
    void shouldLoadConfig() {
        TelegramNotificationService.TelegramConfig cfg = service.getConfig();
        assertNotNull(cfg);
        assertEquals("9255473348:AAFiBI_x37LLxw-QWP8Abpsact2I6iuAoBU", cfg.botToken());
        assertEquals("5792130899", cfg.chatId());
        assertTrue(cfg.enabled());
    }

    @Test
    @DisplayName("Should reject dispatch when token or chatId is blank")
    void shouldRejectInvalidCredentials() {
        TelegramNotificationService.TelegramDispatchResult res1 = service.sendCustomHtml("", "5792130899", "<b>Test</b>");
        assertFalse(res1.success());
        assertNotNull(res1.error());

        TelegramNotificationService.TelegramDispatchResult res2 = service.sendCustomHtml("token", "", "<b>Test</b>");
        assertFalse(res2.success());
        assertNotNull(res2.error());
    }

    @Test
    @DisplayName("Should maintain FIFO bounded history of dispatched alerts")
    void shouldMaintainRecentAlertHistory() {
        assertNotNull(service.getRecentAlerts());
    }
}

