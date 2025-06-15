package com.cv.stockoptimizer.service.metrics;

import com.cv.stockoptimizer.model.entity.Portfolio;
import com.cv.stockoptimizer.model.entity.StockData;
import com.cv.stockoptimizer.repository.PortfolioRepository;
import com.cv.stockoptimizer.repository.StockDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AIMetricsService {

    private static final Logger logger = LoggerFactory.getLogger(AIMetricsService.class);

    private final Map<String, RecommendationMetrics> metricsMap = new ConcurrentHashMap<>();

    private final PortfolioRepository portfolioRepository;
    private final StockDataRepository stockDataRepository;

    @Autowired
    public AIMetricsService(
            PortfolioRepository portfolioRepository,
            StockDataRepository stockDataRepository) {
        this.portfolioRepository = portfolioRepository;
        this.stockDataRepository = stockDataRepository;
    }

    public void recordRecommendationApplication(String portfolioId, Map<String, Double> allocations) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
        if (portfolio == null) {
            return;
        }

        RecommendationMetrics metrics = new RecommendationMetrics();
        metrics.portfolioId = portfolioId;
        metrics.applicationDate = LocalDateTime.now();
        metrics.allocations = new HashMap<>(allocations);
        metrics.initialValue = portfolio.getTotalValue();

        metricsMap.put(portfolioId, metrics);
        logger.info("Recorded AI recommendation application for portfolio {}", portfolioId);
    }

    @Scheduled(cron = "0 0 5 * * 0")
    public void trackRecommendationPerformance() {
        logger.info("Tracking AI recommendation performance");

        LocalDate today = LocalDate.now();

        for (Map.Entry<String, RecommendationMetrics> entry : metricsMap.entrySet()) {
            String portfolioId = entry.getKey();
            RecommendationMetrics metrics = entry.getValue();

            try {
                Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
                if (portfolio == null) {
                    continue;
                }

                long daysSinceApplication = java.time.temporal.ChronoUnit.DAYS.between(
                        metrics.applicationDate.toLocalDate(), today);

                double currentValue = portfolio.getTotalValue() != null ? portfolio.getTotalValue() : 0;
                double valueChange = currentValue - metrics.initialValue;
                double percentageChange = metrics.initialValue > 0
                        ? (valueChange / metrics.initialValue) * 100
                        : 0;

                metrics.latestValue = currentValue;
                metrics.latestValueChange = valueChange;
                metrics.latestPercentageChange = percentageChange;
                metrics.daysSinceApplication = daysSinceApplication;
                metrics.lastTrackedDate = LocalDateTime.now();

                logger.info("Portfolio {} AI recommendation performance after {} days: {}% change",
                        portfolioId, daysSinceApplication, String.format("%.2f", percentageChange));

                if (daysSinceApplication > 180) {
                    metricsMap.remove(portfolioId);
                    logger.info("Removed old performance tracking for portfolio {}", portfolioId);
                }
            } catch (Exception e) {
                logger.error("Error tracking performance for portfolio {}: {}",
                        portfolioId, e.getMessage());
            }
        }

        logger.info("Completed AI recommendation performance tracking for {} portfolios",
                metricsMap.size());
    }

    public Map<String, Object> getRecommendationPerformance(String portfolioId) {
        Map<String, Object> result = new HashMap<>();

        RecommendationMetrics metrics = metricsMap.get(portfolioId);
        if (metrics == null) {
            result.put("found", false);
            result.put("message", "No AI recommendation performance data available");
            return result;
        }

        result.put("found", true);
        result.put("portfolioId", portfolioId);
        result.put("applicationDate", metrics.applicationDate);
        result.put("initialValue", metrics.initialValue);
        result.put("currentValue", metrics.latestValue);
        result.put("valueChange", metrics.latestValueChange);
        result.put("percentageChange", metrics.latestPercentageChange);
        result.put("daysSinceApplication", metrics.daysSinceApplication);
        result.put("lastTrackedDate", metrics.lastTrackedDate);

        try {
            double benchmarkReturn = calculateBenchmarkReturn("^GSPC",
                    metrics.applicationDate.toLocalDate(), LocalDate.now());
            result.put("benchmarkReturn", benchmarkReturn);
            result.put("outperformance", metrics.latestPercentageChange - benchmarkReturn);
        } catch (Exception e) {
            logger.error("Error calculating benchmark return: {}", e.getMessage());
        }

        return result;
    }

    private double calculateBenchmarkReturn(String symbol, LocalDate from, LocalDate to) {
        List<StockData> data = stockDataRepository
                .findBySymbolAndDateBetweenOrderByDateAsc(symbol, from, to);

        if (data.size() < 2) {
            return 0;
        }

        double startPrice = data.get(0).getClose();
        double endPrice = data.get(data.size() - 1).getClose();

        return ((endPrice - startPrice) / startPrice) * 100;
    }

    public Map<String, Object> getAggregatePerformanceStats() {
        Map<String, Object> stats = new HashMap<>();

        if (metricsMap.isEmpty()) {
            stats.put("recommendationCount", 0);
            return stats;
        }

        int totalCount = metricsMap.size();
        int positivePerformanceCount = 0;
        double totalPercentageChange = 0;
        double bestPerformance = Double.MIN_VALUE;
        double worstPerformance = Double.MAX_VALUE;

        for (RecommendationMetrics metrics : metricsMap.values()) {
            if (metrics.latestPercentageChange > 0) {
                positivePerformanceCount++;
            }

            totalPercentageChange += metrics.latestPercentageChange;
            bestPerformance = Math.max(bestPerformance, metrics.latestPercentageChange);
            worstPerformance = Math.min(worstPerformance, metrics.latestPercentageChange);
        }

        stats.put("recommendationCount", totalCount);
        stats.put("positivePerformanceCount", positivePerformanceCount);
        stats.put("positivePerformancePercentage",
                (double) positivePerformanceCount / totalCount * 100);
        stats.put("averagePerformance", totalPercentageChange / totalCount);
        stats.put("bestPerformance", bestPerformance);
        stats.put("worstPerformance", worstPerformance);

        return stats;
    }

    private static class RecommendationMetrics {
        String portfolioId;
        LocalDateTime applicationDate;
        Map<String, Double> allocations;
        double initialValue;
        double latestValue;
        double latestValueChange;
        double latestPercentageChange;
        long daysSinceApplication;
        LocalDateTime lastTrackedDate;
    }
}