package com.institutional.forex.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Storage storage = new Storage();
    private Ingestion ingestion = new Ingestion();
    private Endpoints endpoints = new Endpoints();

    public static class Storage {
        private String excelLogsPath = "./data/Excel_Logs";
        private String databasePath = "./data/h2db";
        private String atomicTempExtension = ".tmp";
        private int maxLockRetryAttempts = 5;
        private long lockRetryWaitMillis = 800;

        public String getExcelLogsPath() { return excelLogsPath; }
        public void setExcelLogsPath(String excelLogsPath) { this.excelLogsPath = excelLogsPath; }
        public String getDatabasePath() { return databasePath; }
        public void setDatabasePath(String databasePath) { this.databasePath = databasePath; }
        public String getAtomicTempExtension() { return atomicTempExtension; }
        public void setAtomicTempExtension(String atomicTempExtension) { this.atomicTempExtension = atomicTempExtension; }
        public int getMaxLockRetryAttempts() { return maxLockRetryAttempts; }
        public void setMaxLockRetryAttempts(int maxLockRetryAttempts) { this.maxLockRetryAttempts = maxLockRetryAttempts; }
        public long getLockRetryWaitMillis() { return lockRetryWaitMillis; }
        public void setLockRetryWaitMillis(long lockRetryWaitMillis) { this.lockRetryWaitMillis = lockRetryWaitMillis; }

        public Path getExcelLogsDirectory() {
            return Paths.get(excelLogsPath).toAbsolutePath().normalize();
        }

        public Path getDatabaseDirectory() {
            return Paths.get(databasePath).toAbsolutePath().normalize();
        }
    }

    public static class Ingestion {
        private boolean enabled = true;
        private boolean initSeedOnStartup = true;
        private boolean runOnStartup = true;
        private long jitterMinMillis = 1500;
        private long jitterMaxMillis = 3500;
        private int maxRetries = 3;
        private double backoffMultiplier = 2.0;
        private int connectTimeoutMillis = 10000;
        private int readTimeoutMillis = 20000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isInitSeedOnStartup() { return initSeedOnStartup; }
        public void setInitSeedOnStartup(boolean initSeedOnStartup) { this.initSeedOnStartup = initSeedOnStartup; }
        public boolean isRunOnStartup() { return runOnStartup; }
        public void setRunOnStartup(boolean runOnStartup) { this.runOnStartup = runOnStartup; }
        public long getJitterMinMillis() { return jitterMinMillis; }
        public void setJitterMinMillis(long jitterMinMillis) { this.jitterMinMillis = jitterMinMillis; }
        public long getJitterMaxMillis() { return jitterMaxMillis; }
        public void setJitterMaxMillis(long jitterMaxMillis) { this.jitterMaxMillis = jitterMaxMillis; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
        public double getBackoffMultiplier() { return backoffMultiplier; }
        public void setBackoffMultiplier(double backoffMultiplier) { this.backoffMultiplier = backoffMultiplier; }
        public int getConnectTimeoutMillis() { return connectTimeoutMillis; }
        public void setConnectTimeoutMillis(int connectTimeoutMillis) { this.connectTimeoutMillis = connectTimeoutMillis; }
        public int getReadTimeoutMillis() { return readTimeoutMillis; }
        public void setReadTimeoutMillis(int readTimeoutMillis) { this.readTimeoutMillis = readTimeoutMillis; }
    }

    public static class Endpoints {
        private String tradingEconomicsBaseUrl = "https://tradingeconomics.com";
        private String forexFactoryCalendarUrl = "https://www.forexfactory.com/calendar";
        private String forexFactoryJsonFeedUrl = "https://nfs.faireconomy.media/ff_calendar_thisweek.json";

        public String getTradingEconomicsBaseUrl() { return tradingEconomicsBaseUrl; }
        public void setTradingEconomicsBaseUrl(String tradingEconomicsBaseUrl) { this.tradingEconomicsBaseUrl = tradingEconomicsBaseUrl; }
        public String getForexFactoryCalendarUrl() { return forexFactoryCalendarUrl; }
        public void setForexFactoryCalendarUrl(String forexFactoryCalendarUrl) { this.forexFactoryCalendarUrl = forexFactoryCalendarUrl; }
        public String getForexFactoryJsonFeedUrl() { return forexFactoryJsonFeedUrl; }
        public void setForexFactoryJsonFeedUrl(String forexFactoryJsonFeedUrl) { this.forexFactoryJsonFeedUrl = forexFactoryJsonFeedUrl; }
    }

    public Storage getStorage() { return storage; }
    public void setStorage(Storage storage) { this.storage = storage; }
    public Ingestion getIngestion() { return ingestion; }
    public void setIngestion(Ingestion ingestion) { this.ingestion = ingestion; }
    public Endpoints getEndpoints() { return endpoints; }
    public void setEndpoints(Endpoints endpoints) { this.endpoints = endpoints; }
}
