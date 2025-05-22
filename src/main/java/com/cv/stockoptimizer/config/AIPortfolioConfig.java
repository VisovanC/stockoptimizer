package com.cv.stockoptimizer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ai.portfolio")
public class AIPortfolioConfig {

    private int historicalDays = 365;
    private int predictionHorizon = 90;
    private double maxStockAllocation = 0.25;
    private double minStockAllocation = 0.02;
    private int optimizationIterations = 1000;
    private double defaultRiskTolerance = 0.5;
    private boolean enableUniverseExpansion = true;
    private int maxExpansionStocks = 10;
    private int cacheTimeoutMinutes = 30;

    // Getters and setters
    public int getHistoricalDays() {
        return historicalDays;
    }

    public void setHistoricalDays(int historicalDays) {
        this.historicalDays = historicalDays;
    }

    public int getPredictionHorizon() {
        return predictionHorizon;
    }

    public void setPredictionHorizon(int predictionHorizon) {
        this.predictionHorizon = predictionHorizon;
    }

    public double getMaxStockAllocation() {
        return maxStockAllocation;
    }

    public void setMaxStockAllocation(double maxStockAllocation) {
        this.maxStockAllocation = maxStockAllocation;
    }

    public double getMinStockAllocation() {
        return minStockAllocation;
    }

    public void setMinStockAllocation(double minStockAllocation) {
        this.minStockAllocation = minStockAllocation;
    }

    public int getOptimizationIterations() {
        return optimizationIterations;
    }

    public void setOptimizationIterations(int optimizationIterations) {
        this.optimizationIterations = optimizationIterations;
    }

    public double getDefaultRiskTolerance() {
        return defaultRiskTolerance;
    }

    public void setDefaultRiskTolerance(double defaultRiskTolerance) {
        this.defaultRiskTolerance = defaultRiskTolerance;
    }

    public boolean isEnableUniverseExpansion() {
        return enableUniverseExpansion;
    }

    public void setEnableUniverseExpansion(boolean enableUniverseExpansion) {
        this.enableUniverseExpansion = enableUniverseExpansion;
    }

    public int getMaxExpansionStocks() {
        return maxExpansionStocks;
    }

    public void setMaxExpansionStocks(int maxExpansionStocks) {
        this.maxExpansionStocks = maxExpansionStocks;
    }

    public int getCacheTimeoutMinutes() {
        return cacheTimeoutMinutes;
    }

    public void setCacheTimeoutMinutes(int cacheTimeoutMinutes) {
        this.cacheTimeoutMinutes = cacheTimeoutMinutes;
    }
}