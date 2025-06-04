package com.cv.stockoptimizer.service.ml;

import com.cv.stockoptimizer.model.entity.MLModel;
import com.cv.stockoptimizer.model.entity.Portfolio;
import com.cv.stockoptimizer.model.entity.StockData;
import com.cv.stockoptimizer.repository.MLModelRepository;
import com.cv.stockoptimizer.repository.PortfolioRepository;
import com.cv.stockoptimizer.repository.StockDataRepository;
import com.cv.stockoptimizer.service.data.MarketDataCollectorService;
import com.cv.stockoptimizer.service.data.TechnicalIndicatorService;
import com.cv.stockoptimizer.service.optimization.AIPortfolioUpgraderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class PortfolioMLService {

    private static final Logger logger = LoggerFactory.getLogger(PortfolioMLService.class);

    private final PortfolioRepository portfolioRepository;
    private final MarketDataCollectorService dataCollectorService;
    private final TechnicalIndicatorService indicatorService;
    private final NeuralNetworkService neuralNetworkService;
    private final MLModelRepository mlModelRepository;
    private final StockDataRepository stockDataRepository;
    private final AIPortfolioUpgraderService aiPortfolioUpgraderService;

    // Track training progress
    private final Map<String, TrainingProgress> trainingProgressMap = new ConcurrentHashMap<>();

    @Autowired
    public PortfolioMLService(
            PortfolioRepository portfolioRepository,
            MarketDataCollectorService dataCollectorService,
            TechnicalIndicatorService indicatorService,
            NeuralNetworkService neuralNetworkService,
            MLModelRepository mlModelRepository,
            StockDataRepository stockDataRepository,
            AIPortfolioUpgraderService aiPortfolioUpgraderService) {
        this.portfolioRepository = portfolioRepository;
        this.dataCollectorService = dataCollectorService;
        this.indicatorService = indicatorService;
        this.neuralNetworkService = neuralNetworkService;
        this.mlModelRepository = mlModelRepository;
        this.stockDataRepository = stockDataRepository;
        this.aiPortfolioUpgraderService = aiPortfolioUpgraderService;
    }

    /**
     * Train ML models for all stocks in a portfolio
     */
    public Map<String, Object> trainModelsForPortfolio(String portfolioId, boolean useSampleData) throws IOException {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));

        String userId = portfolio.getUserId();
        Set<String> symbols = portfolio.getStocks().stream()
                .map(Portfolio.PortfolioStock::getSymbol)
                .collect(Collectors.toSet());

        logger.info("Starting ML training for portfolio {} with {} stocks", portfolioId, symbols.size());

        // Initialize training progress
        TrainingProgress progress = new TrainingProgress(symbols.size());
        trainingProgressMap.put(portfolioId, progress);

        Map<String, Object> result = new HashMap<>();
        result.put("portfolioId", portfolioId);
        result.put("totalStocks", symbols.size());
        result.put("status", "training_started");

        // Start async training
        CompletableFuture.runAsync(() -> {
            try {
                trainStocksAsync(symbols, userId, portfolioId, useSampleData, progress);
            } catch (Exception e) {
                logger.error("Error in async training for portfolio {}: {}", portfolioId, e.getMessage());
                progress.setStatus("failed");
                progress.setError(e.getMessage());
            }
        });

        return result;
    }

    /**
     * Async method to train stocks
     */
    private void trainStocksAsync(Set<String> symbols, String userId, String portfolioId,
                                  boolean useSampleData, TrainingProgress progress) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusYears(2);

        for (String symbol : symbols) {
            try {
                progress.setCurrentStock(symbol);
                logger.info("Processing stock {} for portfolio {}", symbol, portfolioId);

                // Step 1: Collect historical data
                progress.setCurrentStep("Collecting data for " + symbol);
                List<StockData> historicalData;

                if (useSampleData) {
                    historicalData = dataCollectorService.generateSampleData(symbol, startDate, endDate, userId);
                } else {
                    try {
                        historicalData = dataCollectorService.fetchHistoricalData(symbol, startDate, endDate, userId);
                    } catch (Exception e) {
                        logger.warn("Yahoo Finance failed for {}, using sample data", symbol);
                        historicalData = dataCollectorService.generateSampleData(symbol, startDate, endDate, userId);
                    }
                }

                if (!historicalData.isEmpty()) {
                    stockDataRepository.saveAll(historicalData);
                    logger.info("Saved {} data points for {}", historicalData.size(), symbol);
                }

                // Step 2: Calculate technical indicators
                progress.setCurrentStep("Calculating indicators for " + symbol);
                List<?> indicators = indicatorService.calculateAllIndicators(symbol, startDate, endDate, userId);
                logger.info("Calculated {} indicators for {}", indicators.size(), symbol);

                // Step 3: Train neural network model
                progress.setCurrentStep("Training model for " + symbol);
                MLModel model = neuralNetworkService.trainModelForStock(symbol, userId);
                logger.info("Trained model for {} with error: {}", symbol, model.getTrainingError());

                progress.incrementCompleted();
                progress.addTrainedModel(symbol, model);

            } catch (Exception e) {
                logger.error("Error training model for {}: {}", symbol, e.getMessage());
                progress.addFailedStock(symbol, e.getMessage());
            }
        }

        progress.setStatus("completed");
        progress.setCurrentStep("Training completed");

        // Update portfolio to indicate ML models are available
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
        if (portfolio != null) {
            portfolio.setHasAiRecommendations(true);
            portfolioRepository.save(portfolio);
        }
    }

    /**
     * Get ML training status for a portfolio
     */
    public Map<String, Object> getPortfolioMLStatus(String portfolioId) {
        Map<String, Object> status = new HashMap<>();

        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));

        String userId = portfolio.getUserId();
        Set<String> symbols = portfolio.getStocks().stream()
                .map(Portfolio.PortfolioStock::getSymbol)
                .collect(Collectors.toSet());

        // Check training progress
        TrainingProgress progress = trainingProgressMap.get(portfolioId);
        if (progress != null) {
            status.put("trainingInProgress", !progress.isCompleted());
            status.put("progress", progress.toMap());
        } else {
            status.put("trainingInProgress", false);
        }

        // Check existing models
        Map<String, Object> modelStatus = new HashMap<>();
        for (String symbol : symbols) {
            Optional<MLModel> model = mlModelRepository.findByUserIdAndSymbol(userId, symbol);
            if (model.isPresent()) {
                Map<String, Object> modelInfo = new HashMap<>();
                modelInfo.put("exists", true);
                modelInfo.put("trainingDate", model.get().getTrainingDate());
                modelInfo.put("trainingError", model.get().getTrainingError());
                modelStatus.put(symbol, modelInfo);
            } else {
                modelStatus.put(symbol, Map.of("exists", false));
            }
        }

        status.put("models", modelStatus);
        status.put("totalStocks", symbols.size());
        status.put("modelsReady", modelStatus.values().stream()
                .filter(m -> ((Map<?, ?>) m).get("exists").equals(true))
                .count());

        return status;
    }

    /**
     * Generate personalized recommendations after training
     */
    public Map<String, Object> generatePersonalizedRecommendations(
            String portfolioId, double riskTolerance, boolean expandUniverse) throws IOException {

        // Check if all models are trained
        Map<String, Object> mlStatus = getPortfolioMLStatus(portfolioId);
        Long modelsReady = (Long) mlStatus.get("modelsReady");
        Integer totalStocks = (Integer) mlStatus.get("totalStocks");

        if (modelsReady < totalStocks) {
            throw new IllegalStateException(
                    String.format("ML models not ready. %d of %d models trained. Please train models first.",
                            modelsReady, totalStocks));
        }

        // Use the existing AI portfolio upgrader service with the trained models
        return aiPortfolioUpgraderService.generatePortfolioUpgrade(portfolioId, riskTolerance, expandUniverse);
    }

    /**
     * Inner class to track training progress
     */
    private static class TrainingProgress {
        private final int totalStocks;
        private int completedStocks = 0;
        private String status = "in_progress";
        private String currentStock = "";
        private String currentStep = "";
        private String error = null;
        private final Map<String, MLModel> trainedModels = new ConcurrentHashMap<>();
        private final Map<String, String> failedStocks = new ConcurrentHashMap<>();

        public TrainingProgress(int totalStocks) {
            this.totalStocks = totalStocks;
        }

        public synchronized void incrementCompleted() {
            this.completedStocks++;
        }

        public boolean isCompleted() {
            return "completed".equals(status) || "failed".equals(status);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("totalStocks", totalStocks);
            map.put("completedStocks", completedStocks);
            map.put("status", status);
            map.put("currentStock", currentStock);
            map.put("currentStep", currentStep);
            map.put("percentComplete", (completedStocks * 100.0) / totalStocks);
            map.put("trainedSymbols", new ArrayList<>(trainedModels.keySet()));
            map.put("failedStocks", failedStocks);
            if (error != null) {
                map.put("error", error);
            }
            return map;
        }

        // Getters and setters
        public void setStatus(String status) { this.status = status; }
        public void setCurrentStock(String currentStock) { this.currentStock = currentStock; }
        public void setCurrentStep(String currentStep) { this.currentStep = currentStep; }
        public void setError(String error) { this.error = error; }
        public void addTrainedModel(String symbol, MLModel model) { this.trainedModels.put(symbol, model); }
        public void addFailedStock(String symbol, String error) { this.failedStocks.put(symbol, error); }
    }
}