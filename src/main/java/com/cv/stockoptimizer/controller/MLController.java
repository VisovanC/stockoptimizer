package com.cv.stockoptimizer.controller;

import com.cv.stockoptimizer.model.entity.MLModel;
import com.cv.stockoptimizer.model.entity.StockData;
import com.cv.stockoptimizer.model.entity.StockPrediction;
import com.cv.stockoptimizer.model.entity.TechnicalIndicator;
import com.cv.stockoptimizer.repository.MLModelRepository;
import com.cv.stockoptimizer.repository.StockDataRepository;
import com.cv.stockoptimizer.repository.StockPredictionRepository;
import com.cv.stockoptimizer.repository.TechnicalIndicatorRepository;
import com.cv.stockoptimizer.service.data.MarketDataCollectorService;
import com.cv.stockoptimizer.service.data.TechnicalIndicatorService;
import com.cv.stockoptimizer.service.ml.NeuralNetworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/ml")
@CrossOrigin(origins = "*")
public class MLController {

    private final MarketDataCollectorService dataCollectorService;
    private final TechnicalIndicatorService indicatorService;
    private final NeuralNetworkService neuralNetworkService;
    private final StockDataRepository stockDataRepository;
    private final TechnicalIndicatorRepository technicalIndicatorRepository;
    private final MLModelRepository mlModelRepository;
    private final StockPredictionRepository stockPredictionRepository;

    private static final List<String> BASIC_STOCKS = Arrays.asList("AAPL", "MSFT", "GOOGL", "AMZN", "TSLA");

    private String getCurrentUserId() {
        return "system";
    }

    @Autowired
    public MLController(
            MarketDataCollectorService dataCollectorService,
            TechnicalIndicatorService indicatorService,
            NeuralNetworkService neuralNetworkService,
            StockDataRepository stockDataRepository,
            TechnicalIndicatorRepository technicalIndicatorRepository,
            MLModelRepository mlModelRepository,
            StockPredictionRepository stockPredictionRepository) {
        this.dataCollectorService = dataCollectorService;
        this.indicatorService = indicatorService;
        this.neuralNetworkService = neuralNetworkService;
        this.stockDataRepository = stockDataRepository;
        this.technicalIndicatorRepository = technicalIndicatorRepository;
        this.mlModelRepository = mlModelRepository;
        this.stockPredictionRepository = stockPredictionRepository;

        ensureModelsDirectoryExists();
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();

        try {
            long stockDataCount = stockDataRepository.count();
            Set<String> symbols = stockDataRepository.findDistinctSymbolsByUserId(getCurrentUserId());
            long modelCount = mlModelRepository.count();
            long predictionCount = stockPredictionRepository.count();

            status.put("stockDataCount", stockDataCount);
            status.put("availableSymbolsCount", symbols.size());
            status.put("availableSymbols", symbols);
            status.put("modelCount", modelCount);
            status.put("predictionCount", predictionCount);
            status.put("ready", stockDataCount > 0);
            status.put("modelsDirectoryExists", new File("./models").exists());

            return ResponseEntity.ok(status);
        } catch (Exception e) {
            status.put("error", e.getMessage());
            status.put("ready", false);
            return ResponseEntity.status(500).body(status);
        }
    }

    @PostMapping("/start-data-collection")
    public ResponseEntity<Map<String, Object>> startDataCollection(
            @RequestParam(value = "useSampleData", defaultValue = "false") boolean useSampleData) {
        Map<String, Object> response = new HashMap<>();

        try {
            System.out.println("Starting data collection for basic stocks...");
            System.out.println("Mode: " + (useSampleData ? "Sample Data" : "Yahoo Finance"));

            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusYears(2);

            List<String> successfulStocks = new ArrayList<>();
            List<String> failedStocks = new ArrayList<>();
            int totalDataPoints = 0;

            for (int i = 0; i < BASIC_STOCKS.size(); i++) {
                String symbol = BASIC_STOCKS.get(i);
                try {
                    if (i > 0 && !useSampleData) {
                        Thread.sleep(4000);
                    }

                    System.out.println("Collecting data for " + symbol + "...");
                    List<StockData> data;

                    if (useSampleData) {
                        data = dataCollectorService.generateSampleData(symbol, startDate, endDate, getCurrentUserId());
                        stockDataRepository.saveAll(data);
                        successfulStocks.add(symbol);
                        totalDataPoints += data.size();
                        System.out.println("Generated " + data.size() + " sample data points for " + symbol);
                    } else {
                        try {
                            data = dataCollectorService.fetchHistoricalData(symbol, startDate, endDate, getCurrentUserId());

                            if (!data.isEmpty()) {
                                successfulStocks.add(symbol);
                                totalDataPoints += data.size();
                                System.out.println("Successfully collected " + data.size() + " data points for " + symbol);
                            } else {
                                failedStocks.add(symbol);
                                System.out.println("No data retrieved for " + symbol);
                            }
                        } catch (Exception e) {
                            System.err.println("Yahoo Finance error for " + symbol + ": " + e.getMessage());
                            failedStocks.add(symbol);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error collecting data for " + symbol + ": " + e.getMessage());
                    failedStocks.add(symbol);
                }
            }

            System.out.println("Calculating technical indicators...");
            for (String symbol : successfulStocks) {
                try {
                    String cleanSymbol = symbol.replace(" (sample)", "");
                    indicatorService.calculateAllIndicators(cleanSymbol, startDate, endDate, getCurrentUserId());
                    System.out.println("Calculated indicators for " + cleanSymbol);
                } catch (Exception e) {
                    System.err.println("Error calculating indicators for " + symbol + ": " + e.getMessage());
                }
            }

            response.put("status", "success");
            response.put("stocksProcessed", BASIC_STOCKS.size());
            response.put("successfulStocks", successfulStocks.size());
            response.put("failedStocks", failedStocks);
            response.put("totalDataPoints", totalDataPoints);
            response.put("collectedSymbols", successfulStocks);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/train-models")
    public ResponseEntity<Map<String, Object>> trainModels() {
        Map<String, Object> response = new HashMap<>();
        String userId = getCurrentUserId();

        try {
            System.out.println("Starting model training for user: " + userId);

            Set<String> availableSymbols = stockDataRepository.findDistinctSymbolsByUserId(userId);
            List<String> trainedSymbols = new ArrayList<>();
            List<String> failedSymbols = new ArrayList<>();

            for (String symbol : availableSymbols) {
                try {
                    LocalDate endDate = LocalDate.now();
                    LocalDate startDate = endDate.minusYears(2);
                    List<StockData> data = stockDataRepository.findByUserIdAndSymbolAndDateBetweenOrderByDateAsc(
                            userId, symbol, startDate, endDate);

                    if (data.size() < 100) {
                        System.out.println("Not enough data for " + symbol + " (" + data.size() + " points)");
                        failedSymbols.add(symbol + " (insufficient data)");
                        continue;
                    }

                    System.out.println("Training model for " + symbol + "...");
                    MLModel model = neuralNetworkService.trainModelForStock(symbol, userId);
                    trainedSymbols.add(symbol);
                    System.out.println("Successfully trained model for " + symbol +
                            " with error: " + model.getTrainingError());
                } catch (Exception e) {
                    System.err.println("Error training model for " + symbol + ": " + e.getMessage());
                    e.printStackTrace();
                    failedSymbols.add(symbol + " (" + e.getMessage() + ")");
                }
            }

            response.put("status", "success");
            response.put("availableSymbols", availableSymbols.size());
            response.put("trainedModels", trainedSymbols.size());
            response.put("trainedSymbols", trainedSymbols);
            response.put("failedSymbols", failedSymbols);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/generate-predictions")
    public ResponseEntity<Map<String, Object>> generatePredictions() {
        Map<String, Object> response = new HashMap<>();

        try {
            System.out.println("Generating predictions...");

            List<MLModel> models = mlModelRepository.findAll();
            List<String> predictedSymbols = new ArrayList<>();
            List<String> failedSymbols = new ArrayList<>();

            for (MLModel model : models) {
                try {
                    System.out.println("Generating prediction for " + model.getSymbol() + "...");
                    List<StockPrediction> predictions = neuralNetworkService.predictFuturePrices(model.getUserId(), model.getSymbol());

                    if (!predictions.isEmpty()) {
                        predictedSymbols.add(model.getSymbol());
                        StockPrediction prediction = predictions.get(0);
                        System.out.println("Prediction for " + model.getSymbol() + ": " +
                                prediction.getPredictedChangePercentage() + "%");
                    }
                } catch (Exception e) {
                    System.err.println("Error generating prediction for " + model.getSymbol() + ": " + e.getMessage());
                    failedSymbols.add(model.getSymbol());
                }
            }

            response.put("status", "success");
            response.put("generatedPredictions", predictedSymbols.size());
            response.put("predictedSymbols", predictedSymbols);
            response.put("failedSymbols", failedSymbols);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/full-ml-pipeline")
    public ResponseEntity<Map<String, Object>> runFullMLPipeline(
            @RequestParam(value = "useSampleData", defaultValue = "false") boolean useSampleData) {
        Map<String, Object> response = new HashMap<>();

        try {
            System.out.println("=== Starting Full ML Pipeline ===");
            System.out.println("Using sample data: " + useSampleData);

            System.out.println("\n--- Step 1: Data Collection ---");
            Map<String, Object> dataCollection = startDataCollection(useSampleData).getBody();

            System.out.println("\n--- Step 2: Model Training ---");
            Thread.sleep(2000);
            ResponseEntity<Map<String, Object>> trainResponse = trainModels();
            Map<String, Object> modelTraining = trainResponse.getBody();

            if (modelTraining.get("status") != "success") {
                response.put("status", "partial");
                response.put("message", "Data collection succeeded but model training had issues");
            }

            System.out.println("\n--- Step 3: Prediction Generation ---");
            Thread.sleep(2000);
            ResponseEntity<Map<String, Object>> predictResponse = generatePredictions();
            Map<String, Object> predictionGeneration = predictResponse.getBody();

            response.put("status", "success");
            response.put("dataCollection", dataCollection);
            response.put("modelTraining", modelTraining);
            response.put("predictionGeneration", predictionGeneration);

            System.out.println("\n=== Full ML Pipeline Completed ===");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    private void ensureModelsDirectoryExists() {
        try {
            File modelsDir = new File("./models");
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