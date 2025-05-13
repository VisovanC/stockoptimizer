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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class BackendTestController {

    private final MarketDataCollectorService dataCollectorService;
    private final TechnicalIndicatorService indicatorService;
    private final NeuralNetworkService neuralNetworkService;
    private final StockDataRepository stockDataRepository;
    private final TechnicalIndicatorRepository technicalIndicatorRepository;
    private final MLModelRepository mlModelRepository;
    private final StockPredictionRepository stockPredictionRepository;

    @Autowired
    public BackendTestController(
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
     * Test endpoint to collect stock data for a specific symbol
     */
    @GetMapping("/collect-data/{symbol}")
    public ResponseEntity<Map<String, Object>> collectData(@PathVariable String symbol,
                                                           @RequestParam(defaultValue = "30") int days) {
        try {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(days);

            // Fetch data
            List<StockData> data = dataCollectorService.fetchHistoricalData(symbol, startDate, endDate);

            // Save to database
            stockDataRepository.saveAll(data);

            // Return summary
            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("dataPoints", data.size());
            response.put("startDate", startDate);
            response.put("endDate", endDate);
            response.put("status", "success");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Test endpoint to calculate technical indicators
     */
    @GetMapping("/calculate-indicators/{symbol}")
    public ResponseEntity<Map<String, Object>> calculateIndicators(@PathVariable String symbol) {
        try {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(30);

            // Calculate indicators
            List<TechnicalIndicator> indicators = indicatorService.calculateAllIndicators(symbol, startDate, endDate);

            // Return summary
            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("indicatorsCalculated", indicators.size());
            response.put("status", "success");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Test endpoint to train a model
     */
    @GetMapping("/train-model/{symbol}")
    public ResponseEntity<Map<String, Object>> trainModel(@PathVariable String symbol) {
        try {
            // Train model
            MLModel model = neuralNetworkService.trainModelForStock(symbol);

            // Return summary
            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("modelId", model.getId());
            response.put("trainingError", model.getTrainingError());
            response.put("trainingDate", model.getTrainingDate());
            response.put("modelType", model.getModelType());
            response.put("status", "success");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Test endpoint to make predictions
     */
    @GetMapping("/predict/{symbol}")
    public ResponseEntity<Map<String, Object>> predict(@PathVariable String symbol) {
        try {
            // Make prediction
            List<StockPrediction> predictions = neuralNetworkService.predictFuturePrices(symbol);
            StockPrediction prediction = predictions.get(0);

            // Return summary
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
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Test endpoint to check what's stored in the database
     */
    @GetMapping("/database-status")
    public ResponseEntity<Map<String, Object>> databaseStatus() {
        Map<String, Object> status = new HashMap<>();

        // Count documents in each collection
        status.put("stockDataCount", stockDataRepository.count());
        status.put("technicalIndicatorCount", technicalIndicatorRepository.count());
        status.put("mlModelCount", mlModelRepository.count());
        status.put("stockPredictionCount", stockPredictionRepository.count());

        // Get some sample data
        List<String> symbols = (List<String>) stockDataRepository.findDistinctSymbols();
        status.put("availableSymbols", symbols);

        if (!symbols.isEmpty()) {
            String sampleSymbol = symbols.iterator().next();
            status.put("sampleSymbol", sampleSymbol);

            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(7);

            status.put("recentStockData", stockDataRepository
                    .findBySymbolAndDateBetweenOrderByDateAsc(sampleSymbol, startDate, endDate)
                    .size());

            status.put("recentIndicators", technicalIndicatorRepository
                    .findBySymbolAndDateBetweenOrderByDateAsc(sampleSymbol, startDate, endDate)
                    .size());

            mlModelRepository.findBySymbol(sampleSymbol)
                    .ifPresent(model -> status.put("modelInfo", Map.of(
                            "trainingDate", model.getTrainingDate(),
                            "error", model.getTrainingError()
                    )));
        }

        return ResponseEntity.ok(status);
    }

    /**
     * Test the end-to-end flow for a given symbol
     */
    @GetMapping("/end-to-end/{symbol}")
    public ResponseEntity<Map<String, Object>> endToEndTest(@PathVariable String symbol) {
        Map<String, Object> result = new HashMap<>();
        result.put("symbol", symbol);

        try {
            // Step 1: Collect data
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusYears(2);
            List<StockData> data = dataCollectorService.fetchHistoricalData(symbol, startDate, endDate);
            stockDataRepository.saveAll(data);
            result.put("dataCollection", Map.of(
                    "status", "success",
                    "dataPointsCollected", data.size()
            ));

            // Step 2: Calculate indicators
            List<TechnicalIndicator> indicators = indicatorService.calculateAllIndicators(symbol, startDate, endDate);
            result.put("indicatorCalculation", Map.of(
                    "status", "success",
                    "indicatorsCalculated", indicators.size()
            ));

            // Step 3: Train model
            MLModel model = neuralNetworkService.trainModelForStock(symbol);
            result.put("modelTraining", Map.of(
                    "status", "success",
                    "modelId", model.getId(),
                    "trainingError", model.getTrainingError()
            ));

            // Step 4: Make predictions
            List<StockPrediction> predictions = neuralNetworkService.predictFuturePrices(symbol);
            StockPrediction prediction = predictions.get(0);
            result.put("prediction", Map.of(
                    "status", "success",
                    "currentPrice", prediction.getCurrentPrice(),
                    "predictedPrice", prediction.getPredictedPrice(),
                    "predictedChangePercentage", prediction.getPredictedChangePercentage()
            ));

            result.put("status", "success");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            result.put("status", "error");
            result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
}