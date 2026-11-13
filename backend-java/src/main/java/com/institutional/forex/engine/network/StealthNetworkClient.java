package com.institutional.forex.engine.network;

import com.institutional.forex.config.AppProperties;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * StealthNetworkClient: Anti-blocking HTTP client implementing intelligent User-Agent rotation,
 * persistent cookie store, realistic human jitter, exponential backoff retry,
 * and resilient local proxy auto-detection for filtered network environments.
 */
@Component
public class StealthNetworkClient {

    private static final Logger log = LoggerFactory.getLogger(StealthNetworkClient.class);

    private final AppProperties appProperties;
    private final Map<String, String> cookieJar = new ConcurrentHashMap<>();

    private Integer detectLocalProxyPort() {
        int[] candidatePorts = {10808, 20809, 1080, 7890};
        for (int p : candidatePorts) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress("127.0.0.1", p), 100);
                return p;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36 Edg/129.0.0.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14.6; rv:131.0) Gecko/20100101 Firefox/131.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6_1) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    );

    public StealthNetworkClient(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * Executes human-like stealth jitter delay before initiating outbound HTTP connection.
     */
    public void applyStealthJitter() {
        long min = appProperties.getIngestion().getJitterMinMillis();
        long max = appProperties.getIngestion().getJitterMaxMillis();
        if (max <= min) {
            max = min + 500;
        }
        long jitter = ThreadLocalRandom.current().nextLong(min, max + 1);
        try {
            log.debug("Stealth jitter waiting {} ms before next HTTP dispatch...", jitter);
            Thread.sleep(jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Stealth jitter interrupted: {}", e.getMessage());
        }
    }

    /**
     * Returns a rotating random realistic User-Agent string.
     */
    public String getRandomUserAgent() {
        int index = ThreadLocalRandom.current().nextInt(USER_AGENTS.size());
        return USER_AGENTS.get(index);
    }

    /**
     * Fetches HTML Document using JSoup with rotating headers, cookie synchronization, and exponential backoff retry.
     */
    public Document fetchDocument(String url) throws Exception {
        int maxRetries = Math.max(2, appProperties.getIngestion().getMaxRetries());
        double multiplier = appProperties.getIngestion().getBackoffMultiplier();
        long currentBackoff = 1200;

        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            applyStealthJitter();
            String userAgent = getRandomUserAgent();

            try {
                log.info("[HTTP Fetch Document] Attempt {}/{} -> {}", attempt, maxRetries, url);

                Connection connection = Jsoup.connect(url);
                Integer proxyPort = detectLocalProxyPort();
                if (proxyPort != null) {
                    connection.proxy("127.0.0.1", proxyPort);
                }
                connection.userAgent(userAgent)
                        .timeout(appProperties.getIngestion().getReadTimeoutMillis())
                        .maxBodySize(0)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .header("Accept-Encoding", "gzip, deflate")
                        .header("Cache-Control", "no-cache")
                        .header("Pragma", "no-cache")
                        .header("Sec-Ch-Ua", "\"Chromium\";v=\"130\", \"Google Chrome\";v=\"130\", \"Not-A.Brand\";v=\"99\"")
                        .header("Sec-Ch-Ua-Mobile", "?0")
                        .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                        .header("Sec-Fetch-Dest", "document")
                        .header("Sec-Fetch-Mode", "navigate")
                        .header("Sec-Fetch-Site", "none")
                        .header("Sec-Fetch-User", "?1")
                        .header("Upgrade-Insecure-Requests", "1")
                        .cookies(cookieJar)
                        .followRedirects(true)
                        .ignoreHttpErrors(false);

                Connection.Response response = connection.execute();

                // Persist any returned cookies in cookie jar
                cookieJar.putAll(response.cookies());

                Document doc = response.parse();
                log.info("[HTTP 200 OK] Successfully retrieved document from: {} (Size: {} chars)", url, doc.text().length());
                return doc;

            } catch (Exception ex) {
                lastException = ex;
                log.warn("[HTTP Fetch Error] Attempt {}/{} failed for {}: {}", attempt, maxRetries, url, ex.getMessage());

                if (attempt < maxRetries) {
                    log.info("Backing off for {} ms before retry...", currentBackoff);
                    try {
                        Thread.sleep(currentBackoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ie;
                    }
                    currentBackoff = (long) (currentBackoff * multiplier);
                }
            }
        }

        throw new IllegalStateException("Failed to fetch document after " + maxRetries + " attempts from: " + url, lastException);
    }

    /**
     * Fetches raw JSON payload from REST API endpoints with stealth headers and exponential backoff.
     */
    public String fetchJson(String urlString) throws Exception {
        int maxRetries = Math.max(2, appProperties.getIngestion().getMaxRetries());
        double multiplier = appProperties.getIngestion().getBackoffMultiplier();
        long currentBackoff = 1200;

        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            applyStealthJitter();
            String userAgent = getRandomUserAgent();

            HttpURLConnection conn = null;
            try {
                log.info("[HTTP Fetch JSON] Attempt {}/{} -> {}", attempt, maxRetries, urlString);
                URL url = URI.create(urlString).toURL();
                Integer proxyPort = detectLocalProxyPort();
                Proxy netProxy = proxyPort != null ? new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", proxyPort)) : Proxy.NO_PROXY;
                conn = (HttpURLConnection) url.openConnection(netProxy);
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(appProperties.getIngestion().getConnectTimeoutMillis());
                conn.setReadTimeout(appProperties.getIngestion().getReadTimeoutMillis());
                conn.setRequestProperty("User-Agent", userAgent);
                conn.setRequestProperty("Accept", "application/json, text/plain, */*");
                conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");

                // Attach cookies
                if (!cookieJar.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (Map.Entry<String, String> cookie : cookieJar.entrySet()) {
                        if (!sb.isEmpty()) sb.append("; ");
                        sb.append(cookie.getKey()).append("=").append(cookie.getValue());
                    }
                    conn.setRequestProperty("Cookie", sb.toString());
                }

                int responseCode = conn.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        log.info("[HTTP JSON 200 OK] Retrieved payload (Length: {} bytes)", response.length());
                        return response.toString();
                    }
                } else {
                    throw new IllegalStateException("HTTP server returned error code " + responseCode + " for URL: " + urlString);
                }

            } catch (Exception ex) {
                lastException = ex;
                log.warn("[HTTP JSON Error] Attempt {}/{} failed for {}: {}", attempt, maxRetries, urlString, ex.getMessage());

                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(currentBackoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ie;
                    }
                    currentBackoff = (long) (currentBackoff * multiplier);
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }

        throw new IllegalStateException("Failed to fetch JSON after " + maxRetries + " attempts from: " + urlString, lastException);
    }
}
