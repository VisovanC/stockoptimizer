package com.cv.stockoptimizer;

import com.cv.stockoptimizer.model.entity.MLModel;
import com.cv.stockoptimizer.model.entity.Portfolio;
import com.cv.stockoptimizer.model.entity.StockData;
import com.cv.stockoptimizer.model.entity.StockPrediction;
import com.cv.stockoptimizer.model.entity.TechnicalIndicator;
import com.cv.stockoptimizer.repository.PortfolioRepository;
import com.cv.stockoptimizer.repository.StockDataRepository;
import com.cv.stockoptimizer.service.data.MarketDataCollectorService;
import com.cv.stockoptimizer.service.data.TechnicalIndicatorService;
import com.cv.stockoptimizer.service.ml.NeuralNetworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class StockServiceRunner implements CommandLineRunner {

    private final MarketDataCollectorService dataCollectorService;
    private final TechnicalIndicatorService indicatorService;
    private final NeuralNetworkService neuralNetworkService;
    private final StockDataRepository stockDataRepository;
    private final PortfolioRepository portfolioRepository;

    @Autowired
    public StockServiceRunner(
            MarketDataCollectorService dataCollectorService,
            TechnicalIndicatorService indicatorService,
            NeuralNetworkService neuralNetworkService,
            StockDataRepository stockDataRepository,
            PortfolioRepository portfolioRepository) {
        this.dataCollectorService = dataCollectorService;
        this.indicatorService = indicatorService;
        this.neuralNetworkService = neuralNetworkService;
        this.stockDataRepository = stockDataRepository;
        this.portfolioRepository = portfolioRepository;
    }

    @Override
    public void run(String... args) throws Exception {

        // Step 1: Collect historical data for basic stocks
        //collectDataForBasicStocks();

        // Step 2: Calculate technical indicators for those stocks
       // calculateIndicatorsForBasicStocks();

        // Step 3: Train neural network models for the stocks
       // trainModelsForBasicStocks();

        // Step 4: Make predictions using the trained models
      //  makePredictionsForBasicStocks();

        // Or run the complete end-to-end process for a single stock
        // processStockEndToEnd("AAPL");

        // Or process multiple stocks in one go
        // processMultipleStocksEndToEnd(Arrays.asList("AAPL", "MSFT", "GOOGL"));

        System.out.println("StockServiceRunner completed");
        System.out.println("StockServiceRunner: Automatic initialization disabled.");
        System.out.println("Data collection is now user-specific through API endpoints.");

        System.out.println("Current system status:");
        System.out.println("- Total portfolios: " + portfolioRepository.count());
    }

    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }

    private void collectDataForBasicStocks() {
        System.out.println("Starting data collection for basic stocks...");

        List<String> basicStocks = Arrays.asList("AAPL", "MSFT", "GOOGL", "AMZN", "TSLA");
        String userId = getCurrentUserId();
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusYears(2);

        for (String symbol : basicStocks) {
            try {
                System.out.println("Collecting data for " + symbol + "...");
                List<StockData> data = dataCollectorService.fetchHistoricalData(
                        symbol, startDate, endDate, userId);
                stockDataRepository.saveAll(data);

                System.out.println("Successfully collected " + data.size() +
                        " data points for " + symbol);
            } catch (Exception e) {
                System.err.println("Error collecting data for " + symbol + ": " +
                        e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("Data collection completed!");
    }

    private void calculateIndicatorsForBasicStocks() {
        System.out.println("Starting technical indicator calculation...");
        List<String> basicStocks = Arrays.asList("AAPL", "MSFT", "GOOGL", "AMZN", "TSLA");
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusYears(2);

        for (String symbol : basicStocks) {
            try {
                System.out.println("Calculating indicators for " + symbol + "...");
                List<TechnicalIndicator> indicators = indicatorService
                        .calculateAllIndicators(symbol, startDate, endDate, getCurrentUserId());

                System.out.println("Successfully calculated " + indicators.size() +
                        " indicators for " + symbol);

                if (!indicators.isEmpty()) {
                    TechnicalIndicator latest = indicators.get(indicators.size() - 1);
                    System.out.println("Latest indicators for " + symbol + " on " +
                            latest.getDate() + ":");
                    System.out.println("  Price: " + latest.getPrice());
                    System.out.println("  SMA20: " + latest.getSma20());
                    System.out.println("  RSI14: " + latest.getRsi14());
                    System.out.println("  MACD Line: " + latest.getMacdLine());
                    System.out.println("  Bollinger Upper: " + latest.getBollingerUpper());
                    System.out.println("  Bollinger Lower: " + latest.getBollingerLower());
                }
            } catch (Exception e) {
                System.err.println("Error calculating indicators for " + symbol + ": " +
                        e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("Technical indicator calculation completed!");
    }

    private void trainModelsForBasicStocks() {
        System.out.println("Starting neural network model training...");
        List<String> basicStocks = Arrays.asList("AAPL", "MSFT", "GOOGL", "AMZN", "TSLA");

        for (String symbol : basicStocks) {
            try {
                System.out.println("Training model for " + symbol + "...");
                MLModel model = neuralNetworkService.trainModelForStock(getCurrentUserId(), symbol);

                System.out.println("Successfully trained model for " + symbol + ":");
                System.out.println("  Model ID: " + model.getId());
                System.out.println("  Training Date: " + model.getTrainingDate());
                System.out.println("  Training Error: " + model.getTrainingError());
                System.out.println("  Model Type: " + model.getModelType());
                System.out.println("  Model Features: " + model.getFeatures());
            } catch (Exception e) {
                System.err.println("Error training model for " + symbol + ": " +
                        e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("Neural network model training completed!");
    }

    private void makePredictionsForBasicStocks() {
        System.out.println("Starting stock price predictions...");
        List<String> basicStocks = Arrays.asList("AAPL", "MSFT", "GOOGL", "AMZN", "TSLA");

        for (String symbol : basicStocks) {
            try {
                System.out.println("Making predictions for " + symbol + "...");
                List<StockPrediction> predictions =
                        neuralNetworkService.predictFuturePrices(symbol, getCurrentUserId());

                if (!predictions.isEmpty()) {
                    StockPrediction prediction = predictions.get(0);
                    System.out.println("Prediction for " + symbol + ":");
                    System.out.println("  Current Price: $" + prediction.getCurrentPrice());
                    System.out.println("  Predicted Price: $" + prediction.getPredictedPrice());
                    System.out.println("  Change Percentage: " +
                            prediction.getPredictedChangePercentage() + "%");
                    System.out.println("  Confidence Score: " +
                            prediction.getConfidenceScore());
                    System.out.println("  Prediction Date: " +
                            prediction.getPredictionDate());
                    System.out.println("  Target Date: " + prediction.getTargetDate());
                } else {
                    System.out.println("No predictions generated for " + symbol);
                }
            } catch (Exception e) {
                System.err.println("Error making predictions for " + symbol + ": " +
                        e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("Stock price predictions completed!");
    }

    private void processStockEndToEnd(String symbol) {
        System.out.println("Starting end-to-end process for " + symbol + "...");

        try {
            System.out.println("Step 1: Collecting historical data...");
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusYears(2);

            List<StockData> data = dataCollectorService.fetchHistoricalData(
                    symbol, startDate, endDate, getCurrentUserId());
            stockDataRepository.saveAll(data);

            System.out.println("Collected " + data.size() + " data points");
            System.out.println("Step 2: Calculating technical indicators...");
            List<TechnicalIndicator> indicators = indicatorService
                    .calculateAllIndicators(symbol, startDate, endDate, getCurrentUserId());

            System.out.println("Calculated " + indicators.size() + " indicators");

            System.out.println("Step 3: Training neural network model...");
            MLModel model = neuralNetworkService.trainModelForStock(symbol);

            System.out.println("Model trained with error: " + model.getTrainingError());

            System.out.println("Step 4: Making predictions...");
            List<StockPrediction> predictions =
                    neuralNetworkService.predictFuturePrices(symbol, getCurrentUserId());

            if (!predictions.isEmpty()) {
                StockPrediction prediction = predictions.get(0);
                System.out.println("Prediction results:");
                System.out.println("  Current Price: $" + prediction.getCurrentPrice());
                System.out.println("  Predicted Price: $" + prediction.getPredictedPrice());
                System.out.println("  Change Percentage: " +
                        prediction.getPredictedChangePercentage() + "%");
                System.out.println("  Target Date: " + prediction.getTargetDate());
            } else {
                System.out.println("No predictions generated");
            }

            System.out.println("End-to-end process for " + symbol + " completed successfully!");
        } catch (Exception e) {
            System.err.println("Error in end-to-end process for " + symbol + ": " +
                    e.getMessage());
            e.printStackTrace();
        }
    }

    private void processMultipleStocksEndToEnd(List<String> symbols) {
        System.out.println("Starting end-to-end process for multiple stocks: " + symbols);

        for (String symbol : symbols) {
            try {
                processStockEndToEnd(symbol);
            } catch (Exception e) {
                System.err.println("Error processing " + symbol + ": " + e.getMessage());
            }
        }

        System.out.println("End-to-end process for multiple stocks completed!");
    }

    public void trainModelsForUserPortfolio(String userId) {
        System.out.println("Training models for user portfolio (user ID: " + userId + ")...");

        try {
            List<Portfolio> userPortfolios = portfolioRepository.findByUserId(userId);

            if (userPortfolios.isEmpty()) {
                System.out.println("User has no portfolios");
                return;
            }

            Set<String> userStocks = new HashSet<>();
            for (Portfolio portfolio : userPortfolios) {
                for (Portfolio.PortfolioStock stock : portfolio.getStocks()) {
                    userStocks.add(stock.getSymbol());
                }
            }

            System.out.println("Found " + userStocks.size() +
                    " unique stocks in user portfolios");

            for (String symbol : userStocks) {
                try {
                    LocalDate endDate = LocalDate.now();
                    LocalDate startDate = endDate.minusYears(2);
                    List<StockData> existingData = stockDataRepository
                            .findByUserIdAndSymbolAndDateBetweenOrderByDateAsc(
                                    getCurrentUserId() ,symbol, startDate, endDate);

                    if (existingData.size() < 100) {
                        System.out.println("Collecting data for " + symbol + "...");
                        List<StockData> newData = dataCollectorService
                                .fetchHistoricalData(symbol, startDate, endDate, getCurrentUserId());
                        stockDataRepository.saveAll(newData);
                    }
                    System.out.println("Calculating indicators for " + symbol + "...");
                    indicatorService.calculateAllIndicators(symbol, startDate, endDate, getCurrentUserId());

                    System.out.println("Training model for " + symbol + "...");
                    MLModel model = neuralNetworkService.trainModelForStock(symbol);

                    System.out.println("Successfully trained model for " + symbol);
                } catch (Exception e) {
                    System.err.println("Error processing " + symbol + ": " +
                            e.getMessage());
                }
            }

            System.out.println("Completed training models for user portfolio");
        } catch (Exception e) {
            System.err.println("Error processing user portfolio: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void ensureModelsDirectoryExists() {
        try {
            java.io.File modelsDir = new java.io.File("./models");
            if (!modelsDir.exists()) {
                boolean created = modelsDir.mkdirs();
                if (created) {
                    System.out.println("Created models directory");
                } else {
                    System.err.println("Failed to create models directory");
                }
            }
        } catch (Exception e) {
            System.err.println("Error creating models directory: " + e.getMessage());
        }
    }
}