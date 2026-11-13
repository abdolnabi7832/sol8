package com.institutional.forex.engine.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.institutional.forex.model.entity.UserSettingsEntity;
import com.institutional.forex.repository.UserSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class TelegramNotificationService {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationService.class);
    private static final String TELEGRAM_API_BASE = "https://api.telegram.org/bot";

    private final UserSettingsRepository userSettingsRepository;
    private final ObjectMapper objectMapper;
    private final ConcurrentLinkedDeque<Map<String, Object>> recentAlerts = new ConcurrentLinkedDeque<>();

    public TelegramNotificationService(UserSettingsRepository userSettingsRepository, ObjectMapper objectMapper) {
        this.userSettingsRepository = userSettingsRepository;
        this.objectMapper = objectMapper;
    }

    private HttpClient createHttpClient() {
        // Detect local proxy (e.g. v2ray / socks on 10808 or 20809) for Iranian local environments
        int[] candidatePorts = {10808, 20809, 1080, 7890};
        Integer activeProxyPort = null;
        for (int p : candidatePorts) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress("127.0.0.1", p), 120);
                activeProxyPort = p;
                break;
            } catch (Exception ignored) {}
        }

        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL);

        if (activeProxyPort != null) {
            log.debug("[Telegram] Auto-detected local proxy on port {}", activeProxyPort);
            builder.proxy(ProxySelector.of(new InetSocketAddress("127.0.0.1", activeProxyPort)));
        }
        return builder.build();
    }

    public record TelegramConfig(String botToken, String chatId, boolean enabled) {}
    public record TelegramDispatchResult(boolean success, int httpCode, String response, String error) {}

    public TelegramConfig getConfig() {
        UserSettingsEntity s = userSettingsRepository.findByUserIdentifier("DEFAULT_USER").orElse(null);
        if (s == null) {
            return new TelegramConfig("", "", true);
        }
        return new TelegramConfig(
                s.getTelegramBotToken() != null ? s.getTelegramBotToken() : "",
                s.getTelegramChatId() != null ? s.getTelegramChatId() : "",
                s.getTelegramAlertsEnabled() != null ? s.getTelegramAlertsEnabled() : true
        );
    }

    public List<Map<String, Object>> getRecentAlerts() {
        return new ArrayList<>(recentAlerts);
    }

    public TelegramDispatchResult sendCustomHtml(String token, String chatId, String htmlMessage) {
        if (token == null || token.isBlank() || chatId == null || chatId.isBlank()) {
            return new TelegramDispatchResult(false, 0, null, "Bot Token یا Chat ID مشخص نشده است.");
        }

        try {
            String url = TELEGRAM_API_BASE + token.trim() + "/sendMessage";
            Map<String, Object> body = Map.of(
                    "chat_id", chatId.trim(),
                    "text", htmlMessage,
                    "parse_mode", "HTML",
                    "disable_web_page_preview", true
            );
            String jsonPayload = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> resp = createHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            boolean ok = resp.statusCode() >= 200 && resp.statusCode() < 300;
            String timeNow = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            Map<String, Object> logEntry = new LinkedHashMap<>();
            logEntry.put("timestamp", timeNow);
            logEntry.put("status", ok ? "SUCCESS" : "FAILED");
            logEntry.put("httpCode", resp.statusCode());
            logEntry.put("preview", htmlMessage.replaceAll("<[^>]*>", "").substring(0, Math.min(100, htmlMessage.length())));
            logEntry.put("rawResponse", resp.body());

            recentAlerts.addFirst(logEntry);
            while (recentAlerts.size() > 50) {
                recentAlerts.removeLast();
            }

            if (ok) {
                log.info("[Telegram] Alert dispatched successfully to chat {}", chatId);
                return new TelegramDispatchResult(true, resp.statusCode(), resp.body(), null);
            } else {
                log.warn("[Telegram Error] HTTP {}: {}", resp.statusCode(), resp.body());
                return new TelegramDispatchResult(false, resp.statusCode(), resp.body(), "Telegram API Error: " + resp.body());
            }

        } catch (Exception ex) {
            log.error("[Telegram Exception] {}", ex.getMessage());
            return new TelegramDispatchResult(false, 0, null, "خطا در برقراری ارتباط با سرور تلگرام: " + ex.getMessage());
        }
    }

    public TelegramDispatchResult sendActiveAlert(String htmlMessage) {
        TelegramConfig cfg = getConfig();
        if (!cfg.enabled()) {
            log.info("[Telegram] Alerts disabled by user in settings.");
            return new TelegramDispatchResult(false, 0, null, "هشدارهای تلگرام غیرفعال است.");
        }
        return sendCustomHtml(cfg.botToken(), cfg.chatId(), htmlMessage);
    }

    public TelegramDispatchResult sendTestMessage(String candidateToken, String candidateChatId) {
        String token = (candidateToken != null && !candidateToken.isBlank()) ? candidateToken : getConfig().botToken();
        String chatId = (candidateChatId != null && !candidateChatId.isBlank()) ? candidateChatId : getConfig().chatId();

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String msg = """
                🚀 <b>FOREX INTELLIGENCE SUITE — TELEGRAM COCKPIT</b>
                ━━━━━━━━━━━━━━━━━━━━━
                ✅ <b>تست موفقیت‌آمیز ارتباط با ربات تلگرام</b>
                💼 <b>حساب MT5:</b> <code>#57452535486</code> (GTCGlobalTrade-Demo)
                💰 <b>بالانس فعلی:</b> <code>$100.00 USD</code>
                🛡️ <b>اهرم (Leverage):</b> <code>1:500</code>
                ⏱️ <b>زمان ارسال:</b> %s
                ━━━━━━━━━━━━━━━━━━━━━
                📡 سیستم هوشمند آماده دریافت و مخابره لحظه‌ای موقعیت‌ها، حد ضرر (SL)، حد سود (TP) و نتایج معاملات به صورت خودکار می‌باشد.
                """.formatted(now);

        return sendCustomHtml(token, chatId, msg);
    }

    public void notifyPositionOpened(String symbol, String type, double lots, double openPrice,
                                      double sl, double tp, double pillarScore, String reason) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String emoji = "BUY".equalsIgnoreCase(type) ? "🟢 <b>BUY (LONG)</b>" : "🔴 <b>SELL (SHORT)</b>";
        String slStr = sl > 0 ? String.format("%.5f", sl) : "Not Set";
        String tpStr = tp > 0 ? String.format("%.5f", tp) : "Not Set";

        String msg = """
                ⚡ <b>[پوزیشن جدید معاملاتی]</b>
                ━━━━━━━━━━━━━━━━━━━━━
                📈 <b>نماد:</b> <code>%s</code>
                🎯 <b>جهت:</b> %s
                📦 <b>حجم:</b> <code>%.2f Lot</code>
                💵 <b>قیمت ورود:</b> <code>%.5f</code>
                🛡️ <b>حد ضرر (SL):</b> <code>%s</code>
                🎯 <b>حد سود (TP):</b> <code>%s</code>
                🧠 <b>امتیاز ۶ رکن آنتون کریل:</b> <code>%+.2f</code>
                📝 <b>دلیل سیگنال:</b> <i>%s</i>
                ━━━━━━━━━━━━━━━━━━━━━
                💼 <b>حساب:</b> <code>#57452535486</code> (GTC-Demo $100)
                ⏱️ <b>زمان ثبت:</b> %s
                """.formatted(symbol, emoji, lots, openPrice, slStr, tpStr, pillarScore, reason, now);

        sendActiveAlert(msg);
    }

    public void notifyPositionClosed(String symbol, String type, double lots, double openPrice,
                                      double closePrice, double pnl, String outcome) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String pnlEmoji = pnl >= 0 ? "💰 <b>سود: +$" + String.format("%.2f", pnl) + "</b> 🟢"
                                   : "🔻 <b>زیان: -$" + String.format("%.2f", Math.abs(pnl)) + "</b> 🔴";

        String msg = """
                🏁 <b>[بسته شدن پوزیشن / نتیجه معامله]</b>
                ━━━━━━━━━━━━━━━━━━━━━
                📈 <b>نماد:</b> <code>%s</code> (%s)
                📦 <b>حجم:</b> <code>%.2f Lot</code>
                💵 <b>ورود:</b> <code>%.5f</code> &rarr; <b>خروج:</b> <code>%.5f</code>
                📊 <b>نتیجه نهایی:</b> %s
                🏆 <b>وضعیت:</b> <b>%s</b>
                ━━━━━━━━━━━━━━━━━━━━━
                💼 <b>حساب معاملاتی:</b> <code>#57452535486</code>
                ⏱️ <b>زمان بسته‌شدن:</b> %s
                """.formatted(symbol, type, lots, openPrice, closePrice, pnlEmoji, outcome, now);

        sendActiveAlert(msg);
    }
}

