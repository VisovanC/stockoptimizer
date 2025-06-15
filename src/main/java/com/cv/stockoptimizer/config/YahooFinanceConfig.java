package com.cv.stockoptimizer.config;

import org.springframework.context.annotation.Configuration;
import javax.annotation.PostConstruct;
import java.util.Properties;

@Configuration
public class YahooFinanceConfig {

    @PostConstruct
    public void configureYahooFinance() {
        System.setProperty("yahoofinance.connection.timeout", "30000");
        System.setProperty("yahoofinance.read.timeout", "30000");

        System.setProperty("http.agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

        System.setProperty("https.protocols", "TLSv1.2,TLSv1.3");

        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");

        Properties props = System.getProperties();
        props.setProperty("http.keepAlive", "true");
        props.setProperty("http.maxConnections", "5");

        System.out.println("Yahoo Finance configuration initialized");
        System.out.println("Connection timeout: 30 seconds");
        System.out.println("Read timeout: 30 seconds");
    }
}