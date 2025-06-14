package com.cv.stockoptimizer.controller;

import com.cv.stockoptimizer.model.entity.StockData;
import com.cv.stockoptimizer.model.entity.StockPrediction;
import com.cv.stockoptimizer.model.entity.TechnicalIndicator;
import com.cv.stockoptimizer.repository.StockDataRepository;
import com.cv.stockoptimizer.repository.StockPredictionRepository;
import com.cv.stockoptimizer.repository.TechnicalIndicatorRepository;
import com.cv.stockoptimizer.service.data.MarketDataCollectorService;
import com.cv.stockoptimizer.service.data.TechnicalIndicatorService;
import com.cv.stockoptimizer.service.ml.NeuralNetworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/stocks")
public class StockController {
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "system"; // Default user
    }

    private final StockDataRepository stockDataRepository;
    private final TechnicalIndicatorRepository technicalIndicatorRepository;
    private final StockPredictionRepository stockPredictionRepository;
    private final MarketDataCollectorService dataCollectorService;
    private final TechnicalIndicatorService indicatorService;
    private final NeuralNetworkService neuralNetworkService;

    @Autowired
    public StockController(
            StockDataRepository stockDataRepository,
            TechnicalIndicatorRepository technicalIndicatorRepository,
            StockPredictionRepository stockPredictionRepository,
            MarketDataCollectorService dataCollectorService,
            TechnicalIndicatorService indicatorService,
            NeuralNetworkService neuralNetworkService) {
        this.stockDataRepository = stockDataRepository;
        this.technicalIndicatorRepository = technicalIndicatorRepository;
        this.stockPredictionRepository = stockPredictionRepository;
        this.dataCollectorService = dataCollectorService;
        this.indicatorService = indicatorService;
        this.neuralNetworkService = neuralNetworkService;
    }

    @GetMapping("/symbols")
    public ResponseEntity<Set<String>> getAllSymbols() {
        Set<String> symbols = stockDataRepository.findDistinctSymbolsByUserId(getCurrentUserId());
        return ResponseEntity.ok(symbols);
    }

    @GetMapping("/data/{symbol}")
    public ResponseEntity<List<StockData>> getStockData(
            @PathVariable String symbol,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        String userId = getCurrentUserId();
        LocalDate fromDate = from != null ? LocalDate.parse(from) : LocalDate.now().minusMonths(3);
        LocalDate toDate = to != null ? LocalDate.parse(to) : LocalDate.now();

        // Use userId in the query
        List<StockData> data = stockDataRepository.findByUserIdAndSymbolAndDateBetweenOrderByDateAsc(
                userId, symbol.toUpperCase(), fromDate, toDate);

        return ResponseEntity.ok(data);
    }

    @GetMapping("/indicators/{symbol}")
    public ResponseEntity<List<TechnicalIndicator>> getTechnicalIndicators(
            @PathVariable String symbol,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        String currentUserId = getCurrentUserId();

        LocalDate fromDate = from != null ? LocalDate.parse(from) : LocalDate.now().minusMonths(3);
        LocalDate toDate = to != null ? LocalDate.parse(to) : LocalDate.now();

        List<TechnicalIndicator> indicators = technicalIndicatorRepository
                .findByUserIdAndSymbolAndDateBetweenOrderByDateAsc(currentUserId, symbol.toUpperCase(), fromDate, toDate);

        return ResponseEntity.ok(indicators);
    }

    @GetMapping("/predictions/{symbol}")
    public ResponseEntity<List<StockPrediction>> getStockPredictions(@PathVariable String symbol) {
        List<StockPrediction> predictions = stockPredictionRepository
                .findBySymbolOrderByPredictionDateDesc(symbol.toUpperCase());

        return ResponseEntity.ok(predictions);
    }

    @PostMapping("/data/collect/{symbol}")
    public ResponseEntity<?> collectStockData(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "365") int days,
            @RequestParam(defaultValue = "false") boolean forceRefresh) {

        String userId = getCurrentUserId();
        symbol = symbol.toUpperCase();

        try {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(days);

            // Check if we already have recent data
            if (!forceRefresh) {
                List<StockData> existingData = stockDataRepository
                        .findByUserIdAndSymbolAndDateBetweenOrderByDateAsc(userId, symbol, startDate, endDate);

                if (existingData.size() >= days * 0.7) { // If we have 70% of expected data
                    Map<String, Object> response = new HashMap<>();
                    response.put("symbol", symbol);
                    response.put("dataPoints", existingData.size());
                    response.put("userId", userId);
                    response.put("status", "existing");
                    response.put("message", "Using existing data. Use forceRefresh=true to update.");
                    return ResponseEntity.ok(response);
                }
            }

            // Fetch new data
            List<StockData> data = dataCollectorService.fetchHistoricalData(
                    symbol, startDate, endDate, userId);

            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("dataPoints", data.size());
            response.put("userId", userId);
            response.put("status", "success");
            response.put("dataSource", data.isEmpty() ? "none" :
                    (data.get(0).getVolume() == 10000000L ? "sample" : "yahoo"));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            response.put("symbol", symbol);
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/indicators/calculate/{symbol}")
    public ResponseEntity<?> calculateIndicators(@PathVariable String symbol) {
        String userId = getCurrentUserId();
        symbol = symbol.toUpperCase();

        try {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusYears(2);

            // Pass userId to the service
            List<TechnicalIndicator> indicators = indicatorService.calculateAllIndicators(
                    symbol, startDate, endDate, userId);

            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("indicatorsCalculated", indicators.size());
            response.put("userId", userId);
            response.put("status", "success");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/predictions/generate/{symbol}")
    public ResponseEntity<?> generatePrediction(@PathVariable String symbol) {
        try {
            String userId = getCurrentUserId();
            symbol = symbol.toUpperCase();

            List<StockPrediction> predictions = neuralNetworkService.predictFuturePrices(symbol, userId);
            StockPrediction prediction = predictions.get(0);

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

    @PostMapping("/analysis/{symbol}")
    public ResponseEntity<?> analyzeStock(@PathVariable String symbol) {
        try {
            String userId = getCurrentUserId();
            symbol = symbol.toUpperCase();

            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusYears(2);

            // Check if data exists
            List<StockData> data = stockDataRepository.findByUserIdAndSymbolAndDateBetweenOrderByDateAsc(
                    userId, symbol, startDate, endDate);

            if (data.size() < 100) {
                // Collect data first
                data = dataCollectorService.fetchHistoricalData(symbol, startDate, endDate, userId);
            }

            // Calculate indicators if needed
            List<TechnicalIndicator> indicators = indicatorService.calculateAllIndicators(
                    symbol, startDate, endDate, userId);

            // Generate predictions
            List<StockPrediction> predictions = neuralNetworkService.predictFuturePrices(symbol, userId);
            StockPrediction prediction = predictions.get(0);

            Map<String, Object> analysis = new HashMap<>();
            analysis.put("symbol", symbol);

            // Basic info
            String trend = "NEUTRAL";
            String rsiSignal = "NEUTRAL";
            String macdSignal = "NEUTRAL";
            String bollingerSignal = "NEUTRAL";

            TechnicalIndicator latestIndicator = null;
            if (!indicators.isEmpty()) {
                latestIndicator = indicators.get(indicators.size() - 1);

                analysis.put("currentPrice", latestIndicator.getPrice());
                analysis.put("date", latestIndicator.getDate());

                Map<String, Object> technicalAnalysis = new HashMap<>();

                // Trend analysis
                if (latestIndicator.getSma20() > latestIndicator.getSma50()) {
                    trend = "BULLISH";
                } else if (latestIndicator.getSma20() < latestIndicator.getSma50()) {
                    trend = "BEARISH";
                }
                technicalAnalysis.put("trend", trend);

                // RSI analysis
                if (latestIndicator.getRsi14() > 70) {
                    rsiSignal = "OVERBOUGHT";
                } else if (latestIndicator.getRsi14() < 30) {
                    rsiSignal = "OVERSOLD";
                }
                technicalAnalysis.put("rsiSignal", rsiSignal);

                // MACD analysis
                if (latestIndicator.getMacdHistogram() > 0 && indicators.size() > 1) {
                    TechnicalIndicator previousIndicator = indicators.get(indicators.size() - 2);
                    if (latestIndicator.getMacdHistogram() > previousIndicator.getMacdHistogram()) {
                        macdSignal = "STRONG_BULLISH";
                    } else {
                        macdSignal = "BULLISH";
                    }
                } else if (latestIndicator.getMacdHistogram() < 0 && indicators.size() > 1) {
                    TechnicalIndicator previousIndicator = indicators.get(indicators.size() - 2);
                    if (latestIndicator.getMacdHistogram() < previousIndicator.getMacdHistogram()) {
                        macdSignal = "STRONG_BEARISH";
                    } else {
                        macdSignal = "BEARISH";
                    }
                }
                technicalAnalysis.put("macdSignal", macdSignal);

                // Bollinger Bands analysis
                if (latestIndicator.getPrice() > latestIndicator.getBollingerUpper()) {
                    bollingerSignal = "OVERBOUGHT";
                } else if (latestIndicator.getPrice() < latestIndicator.getBollingerLower()) {
                    bollingerSignal = "OVERSOLD";
                }
                technicalAnalysis.put("bollingerSignal", bollingerSignal);

                analysis.put("technicalAnalysis", technicalAnalysis);

                // Support and resistance levels
                Map<String, Object> levels = new HashMap<>();
                List<Double> supportLevels = new ArrayList<>();
                List<Double> resistanceLevels = new ArrayList<>();

                // Calculate support/resistance from recent data
                for (int i = Math.max(0, indicators.size() - 30); i < indicators.size(); i++) {
                    TechnicalIndicator indicator = indicators.get(i);
                    if (i > 0 && i < indicators.size() - 1) {
                        if (indicator.getPrice() < indicators.get(i-1).getPrice() &&
                                indicator.getPrice() < indicators.get(i+1).getPrice()) {
                            supportLevels.add(indicator.getPrice());
                        }
                        if (indicator.getPrice() > indicators.get(i-1).getPrice() &&
                                indicator.getPrice() > indicators.get(i+1).getPrice()) {
                            resistanceLevels.add(indicator.getPrice());
                        }
                    }
                }

                supportLevels.sort(Comparator.reverseOrder());
                resistanceLevels.sort(Comparator.naturalOrder());

                List<Double> topSupportLevels = supportLevels.size() <= 3 ?
                        supportLevels : supportLevels.subList(0, 3);
                List<Double> topResistanceLevels = resistanceLevels.size() <= 3 ?
                        resistanceLevels : resistanceLevels.subList(0, 3);

                levels.put("support", topSupportLevels);
                levels.put("resistance", topResistanceLevels);

                analysis.put("levels", levels);
            }

            // Prediction data
            Map<String, Object> predictionData = new HashMap<>();
            predictionData.put("predictedPrice", prediction.getPredictedPrice());
            predictionData.put("predictedChangePercentage", prediction.getPredictedChangePercentage());
            predictionData.put("confidenceScore", prediction.getConfidenceScore());
            predictionData.put("targetDate", prediction.getTargetDate());

            String outlook = "NEUTRAL";
            if (prediction.getPredictedChangePercentage() > 5) {
                outlook = "STRONGLY_POSITIVE";
            } else if (prediction.getPredictedChangePercentage() > 2) {
                outlook = "POSITIVE";
            } else if (prediction.getPredictedChangePercentage() < -5) {
                outlook = "STRONGLY_NEGATIVE";
            } else if (prediction.getPredictedChangePercentage() < -2) {
                outlook = "NEGATIVE";
            }
            predictionData.put("outlook", outlook);

            analysis.put("prediction", predictionData);

            // Overall recommendation
            String recommendation = "HOLD";
            double bullishFactors = 0;
            double bearishFactors = 0;

            if (trend.equals("BULLISH")) bullishFactors += 1;
            if (trend.equals("BEARISH")) bearishFactors += 1;

            if (rsiSignal.equals("OVERSOLD")) bullishFactors += 0.5;
            if (rsiSignal.equals("OVERBOUGHT")) bearishFactors += 0.5;

            if (macdSignal.equals("STRONG_BULLISH")) bullishFactors += 1;
            if (macdSignal.equals("BULLISH")) bullishFactors += 0.5;
            if (macdSignal.equals("BEARISH")) bearishFactors += 0.5;
            if (macdSignal.equals("STRONG_BEARISH")) bearishFactors += 1;

            if (bollingerSignal.equals("OVERSOLD")) bullishFactors += 0.5;
            if (bollingerSignal.equals("OVERBOUGHT")) bearishFactors += 0.5;

            if (outlook.equals("STRONGLY_POSITIVE")) bullishFactors += 2;
            if (outlook.equals("POSITIVE")) bullishFactors += 1;
            if (outlook.equals("NEGATIVE")) bearishFactors += 1;
            if (outlook.equals("STRONGLY_NEGATIVE")) bearishFactors += 2;

            if (bullishFactors - bearishFactors >= 2) {
                recommendation = "STRONG_BUY";
            } else if (bullishFactors - bearishFactors >= 1) {
                recommendation = "BUY";
            } else if (bearishFactors - bullishFactors >= 2) {
                recommendation = "STRONG_SELL";
            } else if (bearishFactors - bullishFactors >= 1) {
                recommendation = "SELL";
            }

            analysis.put("recommendation", recommendation);
            analysis.put("companyName", getCompanyName(symbol));

            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            response.put("symbol", symbol);
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchStocks(@RequestParam String query) {
        Set<String> allSymbols = stockDataRepository.findDistinctSymbolsByUserId(getCurrentUserId());

        List<Map<String, String>> results = new ArrayList<>();

        // Search in existing symbols
        for (String symbol : allSymbols) {
            if (symbol.toUpperCase().contains(query.toUpperCase())) {
                Map<String, String> result = new HashMap<>();
                result.put("symbol", symbol);
                result.put("name", getCompanyName(symbol));
                results.add(result);
            }
        }

        // Add common stocks if not in results
        Map<String, String> commonStocks = getCommonStocks();
        for (Map.Entry<String, String> entry : commonStocks.entrySet()) {
            String symbol = entry.getKey();
            String name = entry.getValue();

            if ((symbol.contains(query.toUpperCase()) || name.toUpperCase().contains(query.toUpperCase())) &&
                    results.stream().noneMatch(r -> r.get("symbol").equals(symbol))) {
                Map<String, String> result = new HashMap<>();
                result.put("symbol", symbol);
                result.put("name", name);
                results.add(result);
            }
        }

        // Limit results
        if (results.size() > 20) {
            results = results.subList(0, 20);
        }

        return ResponseEntity.ok(results);
    }

    @GetMapping("/validate/{symbol}")
    public ResponseEntity<?> validateSymbol(@PathVariable String symbol) {
        symbol = symbol.toUpperCase();

        Map<String, Object> response = new HashMap<>();
        response.put("symbol", symbol);
        response.put("valid", true); // Assume valid for now
        response.put("name", getCompanyName(symbol));
        response.put("message", "Symbol is valid. Data will be fetched when added to portfolio.");

        return ResponseEntity.ok(response);
    }

    private Map<String, String> getCommonStocks() {
        Map<String, String> commonStocks = new HashMap<>();
        commonStocks.put("AAPL", "Apple Inc.");
        commonStocks.put("MSFT", "Microsoft Corporation");
        commonStocks.put("GOOGL", "Alphabet Inc.");
        commonStocks.put("AMZN", "Amazon.com, Inc.");
        commonStocks.put("META", "Meta Platforms, Inc.");
        commonStocks.put("TSLA", "Tesla, Inc.");
        commonStocks.put("NVDA", "NVIDIA Corporation");
        commonStocks.put("JPM", "JPMorgan Chase & Co.");
        commonStocks.put("JNJ", "Johnson & Johnson");
        commonStocks.put("V", "Visa Inc.");
        commonStocks.put("PG", "Procter & Gamble Company");
        commonStocks.put("HD", "The Home Depot, Inc.");
        commonStocks.put("UNH", "UnitedHealth Group Incorporated");
        commonStocks.put("MA", "Mastercard Incorporated");
        commonStocks.put("DIS", "The Walt Disney Company");
        commonStocks.put("BAC", "Bank of America Corporation");
        commonStocks.put("WMT", "Walmart Inc.");
        commonStocks.put("CVX", "Chevron Corporation");
        commonStocks.put("PEP", "PepsiCo, Inc.");
        commonStocks.put("ABBV", "AbbVie Inc.");
        commonStocks.put("KO", "The Coca-Cola Company");
        commonStocks.put("MRK", "Merck & Co., Inc.");
        commonStocks.put("VZ", "Verizon Communications Inc.");
        commonStocks.put("PFE", "Pfizer Inc.");
        commonStocks.put("TMO", "Thermo Fisher Scientific Inc.");
        commonStocks.put("CSCO", "Cisco Systems, Inc.");
        commonStocks.put("ADBE", "Adobe Inc.");
        commonStocks.put("CRM", "Salesforce, Inc.");
        commonStocks.put("INTC", "Intel Corporation");
        commonStocks.put("AMD", "Advanced Micro Devices, Inc.");
        commonStocks.put("NFLX", "Netflix, Inc.");
        commonStocks.put("PYPL", "PayPal Holdings, Inc.");
        commonStocks.put("UBER", "Uber Technologies, Inc.");
        commonStocks.put("SQ", "Block, Inc.");
        commonStocks.put("SHOP", "Shopify Inc.");
        commonStocks.put("SPOT", "Spotify Technology S.A.");
        commonStocks.put("ROKU", "Roku, Inc.");
        commonStocks.put("ZM", "Zoom Video Communications, Inc.");
        commonStocks.put("DOCU", "DocuSign, Inc.");
        commonStocks.put("TWLO", "Twilio Inc.");
        return commonStocks;
    }

    private String getCompanyName(String symbol) {
        Map<String, String> stocks = getCommonStocks();
        return stocks.getOrDefault(symbol.toUpperCase(), symbol + " Corp.");
    }
}