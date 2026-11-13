package com.institutional.forex;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Forex Intelligence Suite — Institutional Command Center
 * Principal Quantitative Architecture & Macro Forecasting Platform
 */
@SpringBootApplication(scanBasePackages = {"com.institutional.forex", "com.quantfx.suite"})
@EnableScheduling
@EnableAsync
@ConfigurationPropertiesScan(basePackages = {"com.institutional.forex", "com.quantfx.suite"})
public class ForexIntelligenceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForexIntelligenceApplication.class, args);
    }
}
