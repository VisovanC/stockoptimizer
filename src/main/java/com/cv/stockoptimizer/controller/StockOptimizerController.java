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

import java.time.LocalDate;
import java.util.*;

/**
 * REST API for stock optimization services
 */
@RestController
@RequestMapping("/api/stock-optimizer")
public class StockOptimizerController {

    private final MarketDataCollectorService dataCollectorService;
    private final TechnicalIndicatorService indicatorService;
    private final NeuralNetworkService neuralNetworkService;
    private final StockDataRepository stockDataRepository;
    private final TechnicalIndicatorRepository technicalIndicatorRepository;
    private final MLModelRepository mlModelRepository;
    private final StockPredictionRepository stockPredictionRepository;

    @Autowired
    public StockOptimizerController(
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
    }

    /**
     * Get all available stock symbols in the database
     */
    @GetMapping("/symbols")
    public ResponseEntity<Set<String>> getAllSymbols() {
        Set<String> symbols = stockDataRepository.findDistinctSymbols();
        return ResponseEntity.ok(symbols);
    }

    /**
     * Collect historical data for a stock
     */
    @PostMapping("/data/collect/{symbol}")
    public ResponseEntity<?> collectData(
            @PathVariable String symbol,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        try {
            // Parse dates or use defaults (2 years)
            LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
            LocalDate start = startDate != null ? LocalDate.parse(startDate) : end.minusYears(2);

            // Collect data
            List<StockData> data = dataCollectorService.fetchHistoricalData(
                    symbol.toUpperCase(), start, end);

            // Save to database
            stockDataRepository.saveAll(data);

            // Return response
            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("startDate", start);
            response.put("endDate", end);
            response.put("dataPoints", data.size());
            response.put("status", "success");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get historical data for a stock
     */
    @GetMapping("/data/{symbol}")
    public ResponseEntity<List<StockData>> getStockData(
            @PathVariable String symbol,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        // Parse dates or use defaults (3 months)
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : end.minusMonths(3);

        List<StockData> data = stockDataRepository.findBySymbolAndDateBetweenOrderByDateAsc(
                symbol.toUpperCase(), start, end);

        return ResponseEntity.ok(data);
    }

    /**
     * Calculate technical indicators for a stock
     */
    @PostMapping("/indicators/calculate/{symbol}")
    public ResponseEntity<?> calculateIndicators(
            @PathVariable String symbol,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        try {
            // Parse dates or use defaults (2 years)
            LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
            LocalDate start = startDate != null ? LocalDate.parse(startDate) : end.minusYears(2);

            // Calculate indicators
            List<TechnicalIndicator> indicators = indicatorService.calculateAllIndicators(
                    symbol.toUpperCase(), start, end);

            // Return response
            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("startDate", start);
            response.put("endDate", end);
            response.put("indicators", indicators.size());
            response.put("status", "success");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get technical indicators for a stock
     */
    @GetMapping("/indicators/{symbol}")
    public ResponseEntity<List<TechnicalIndicator>> getIndicators(
            @PathVariable String symbol,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        // Parse dates or use defaults (3 months)
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : end.minusMonths(3);

        List<TechnicalIndicator> indicators = technicalIndicatorRepository
                .findBySymbolAndDateBetweenOrderByDateAsc(symbol.toUpperCase(), start, end);

        return ResponseEntity.ok(indicators);
    }

    /**
     * Train a neural network model for a stock
     */
    @PostMapping("/models/train/{symbol}")
    public ResponseEntity<?> trainModel(@PathVariable String symbol) {
        try {
            // Train model
            MLModel model = neuralNetworkService.trainModelForStock(symbol.toUpperCase());

            // Return response
            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("modelId", model.getId());
            response.put("trainingDate", model.getTrainingDate());
            response.put("trainingError", model.getTrainingError());
            response.put("status", "success");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get information about trained models
     */
    @GetMapping("/models")
    public ResponseEntity<List<MLModel>> getModels() {
        List<MLModel> models = mlModelRepository.findAll();
        return ResponseEntity.ok(models);
    }

    /**
     * Get information about a specific model
     */
    @GetMapping("/models/{symbol}")
    public ResponseEntity<?> getModel(@PathVariable String symbol) {
        Optional<MLModel> model = mlModelRepository.findBySymbol(symbol.toUpperCase());

        if (model.isPresent()) {
            return ResponseEntity.ok(model.get());
        } else {
            return ResponseEntity.status(404).body(Map.of(
                    "status", "error",
                    "message", "No model found for symbol: " + symbol
            ));
        }
    }

    /**
     * Generate price predictions for a stock
     */
    @PostMapping("/predictions/generate/{symbol}")
    public ResponseEntity<?> generatePrediction(@PathVariable String symbol) {
        try {
            // Generate predictions
            List<StockPrediction> predictions =
                    neuralNetworkService.predictFuturePrices(symbol.toUpperCase());

            if (predictions.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(
                        "status", "error",
                        "message", "No predictions could be generated"
                ));
            }

            // Get the first prediction
            StockPrediction prediction = predictions.get(0);

            // Return response
            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("currentPrice", prediction.getCurrentPrice());
            response.put("predictedPrice", prediction.getPredictedPrice());
            response.put("predictedChangePercentage", prediction.getPredictedChangePercentage());
            response.put("confidenceScore", prediction.getConfidenceScore());
            response.put("predictionDate", prediction.getPredictionDate());
            response.put("targetDate", prediction.getTargetDate());
            response.put("status", "success");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get predictions for a stock
     */
    @GetMapping("/predictions/{symbol}")
    public ResponseEntity<List<StockPrediction>> getPredictions(@PathVariable String symbol) {
        List<StockPrediction> predictions = stockPredictionRepository
                .findBySymbolOrderByPredictionDateDesc(symbol.toUpperCase());

        return ResponseEntity.ok(predictions);
    }

    /**
     * Run the complete end-to-end process for a stock
     */
    @PostMapping("/process/{symbol}")
    public ResponseEntity<?> processStock(@PathVariable String symbol) {
        Map<String, Object> result = new HashMap<>();
        result.put("symbol", symbol.toUpperCase());

        try {
            // Step 1: Collect historical data
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusYears(2);

            List<StockData> data = dataCollectorService.fetchHistoricalData(
                    symbol.toUpperCase(), startDate, endDate);
            stockDataRepository.saveAll(data);

            result.put("dataCollection", Map.of(
                    "status", "success",
                    "dataPoints", data.size()
            ));

            // Step 2: Calculate technical indicators
            List<TechnicalIndicator> indicators = indicatorService.calculateAllIndicators(
                    symbol.toUpperCase(), startDate, endDate);

            result.put("indicatorCalculation", Map.of(
                    "status", "success",
                    "indicators", indicators.size()
            ));

            // Step 3: Train ML model
            MLModel model = neuralNetworkService.trainModelForStock(symbol.toUpperCase());

            result.put("modelTraining", Map.of(
                    "status", "success",
                    "modelId", model.getId(),
                    "trainingError", model.getTrainingError()
            ));

            // Step 4: Make predictions
            List<StockPrediction> predictions =
                    neuralNetworkService.predictFuturePrices(symbol.toUpperCase());

            if (!predictions.isEmpty()) {
                StockPrediction prediction = predictions.get(0);
                result.put("prediction", Map.of(
                        "status", "success",
                        "currentPrice", prediction.getCurrentPrice(),
                        "predictedPrice", prediction.getPredictedPrice(),
                        "predictedChangePercentage", prediction.getPredictedChangePercentage(),
                        "confidenceScore", prediction.getConfidenceScore(),
                        "targetDate", prediction.getTargetDate()
                ));
            }

            result.put("status", "success");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Process multiple stocks in one request
     */
    @PostMapping("/process/batch")
    public ResponseEntity<?> processMultipleStocks(@RequestBody List<String> symbols) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> symbolResults = new HashMap<>();

        for (String symbol : symbols) {
            try {
                // Process each stock
                ResponseEntity<?> response = processStock(symbol);
                symbolResults.put(symbol, response.getBody());
            } catch (Exception e) {
                symbolResults.put(symbol, Map.of(
                        "status", "error",
                        "message", e.getMessage()
                ));
            }
        }

        result.put("results", symbolResults);
        result.put("status", "success");

        return ResponseEntity.ok(result);
    }
}