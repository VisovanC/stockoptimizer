package com.cv.stockoptimizer.service.optimization;

import com.cv.stockoptimizer.model.entity.MLModel;
import com.cv.stockoptimizer.model.entity.Portfolio;
import com.cv.stockoptimizer.model.entity.StockData;
import com.cv.stockoptimizer.model.entity.StockPrediction;
import com.cv.stockoptimizer.model.entity.TechnicalIndicator;
import com.cv.stockoptimizer.repository.MLModelRepository;
import com.cv.stockoptimizer.repository.PortfolioRepository;
import com.cv.stockoptimizer.repository.StockDataRepository;
import com.cv.stockoptimizer.repository.StockPredictionRepository;
import com.cv.stockoptimizer.repository.TechnicalIndicatorRepository;
import com.cv.stockoptimizer.service.ml.NeuralNetworkService;
import org.encog.neural.networks.BasicNetwork;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.cv.stockoptimizer.config.AIPortfolioConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import com.cv.stockoptimizer.service.history.PortfolioHistoryService;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AIPortfolioUpgraderService {

    private final PortfolioHistoryService historyService;
    private final PortfolioRepository portfolioRepository;
    private final StockDataRepository stockDataRepository;
    private final StockPredictionRepository predictionRepository;
    private final TechnicalIndicatorRepository indicatorRepository;
    private final MLModelRepository mlModelRepository;
    private final NeuralNetworkService neuralNetworkService;
    private final AIPortfolioConfig config;

    private static final int HISTORICAL_DAYS = 365;
    private static final int PREDICTION_HORIZON = 90;
    private static final double MAX_SINGLE_STOCK_ALLOCATION = 0.25;
    private static final double MIN_SINGLE_STOCK_ALLOCATION = 0.02;

    @Autowired
    public AIPortfolioUpgraderService(
            PortfolioRepository portfolioRepository,
            StockDataRepository stockDataRepository,
            StockPredictionRepository predictionRepository,
            TechnicalIndicatorRepository indicatorRepository,
            MLModelRepository mlModelRepository,
            NeuralNetworkService neuralNetworkService,
            AIPortfolioConfig config,
            PortfolioHistoryService historyService) {
        this.portfolioRepository = portfolioRepository;
        this.stockDataRepository = stockDataRepository;
        this.predictionRepository = predictionRepository;
        this.indicatorRepository = indicatorRepository;
        this.mlModelRepository = mlModelRepository;
        this.neuralNetworkService = neuralNetworkService;
        this.config = config;
        this.historyService = historyService;
    }

    @Cacheable(value = "portfolioRecommendations", key = "#portfolioId + '-' + #riskTolerance + '-' + #stockUniverseExpansion")
    public Map<String, Object> generatePortfolioUpgrade(
            String portfolioId,
            double riskTolerance,
            boolean stockUniverseExpansion) throws IOException {

        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));

        Set<String> currentSymbols = portfolio.getStocks().stream()
                .map(Portfolio.PortfolioStock::getSymbol)
                .collect(Collectors.toSet());

        Set<String> analyzedSymbols = new HashSet<>(currentSymbols);
        if (stockUniverseExpansion) {
            Set<String> expandedSymbols = expandStockUniverse(currentSymbols, portfolio.getUserId());
            analyzedSymbols.addAll(expandedSymbols);
        }

        Map<String, AnalysisData> stockAnalysisData = collectStockAnalysisData(analyzedSymbols, portfolio.getUserId());

        Map<String, Double> optimizedAllocations = calculateOptimalAllocations(
                stockAnalysisData, currentSymbols, riskTolerance);

        List<Map<String, Object>> recommendedActions = generateStockActions(
                portfolio, optimizedAllocations, stockAnalysisData);

        Map<String, Object> expectedPerformance = calculateExpectedPerformance(
                optimizedAllocations, stockAnalysisData);

        Map<String, Object> improvementMetrics = calculateImprovementMetrics(
                portfolio, optimizedAllocations, stockAnalysisData);

        Map<String, Object> results = new HashMap<>();
        results.put("portfolioId", portfolioId);
        results.put("riskTolerance", riskTolerance);
        results.put("universeExpanded", stockUniverseExpansion);
        results.put("currentSymbols", currentSymbols);
        results.put("analyzedSymbols", analyzedSymbols);
        results.put("recommendedAllocations", optimizedAllocations);
        results.put("recommendedActions", recommendedActions);
        results.put("expectedPerformance", expectedPerformance);
        results.put("improvementMetrics", improvementMetrics);
        results.put("generatedAt", LocalDateTime.now());
        results.put("aiConfidenceScore", calculateAIConfidenceScore(stockAnalysisData));

        return results;
    }

    @CacheEvict(value = "portfolioRecommendations", key = "#portfolioId + '*'")
    public Portfolio applyPortfolioUpgrade(String portfolioId, Map<String, Double> optimizedAllocations) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));

        double totalValue = portfolio.getTotalValue() != null ? portfolio.getTotalValue() : 0;
        if (totalValue <= 0) {
            throw new IllegalStateException("Cannot upgrade portfolio with zero or unknown value");
        }

        Set<String> currentSymbols = portfolio.getStocks().stream()
                .map(Portfolio.PortfolioStock::getSymbol)
                .collect(Collectors.toSet());

        Set<String> allSymbols = new HashSet<>(optimizedAllocations.keySet());

        Map<String, StockData> latestStockData = getLatestStockData(allSymbols, portfolio.getUserId());

        List<Portfolio.PortfolioStock> updatedStocks = new ArrayList<>();

        for (Map.Entry<String, Double> entry : optimizedAllocations.entrySet()) {
            String symbol = entry.getKey();
            double allocation = entry.getValue();

            if (allocation < MIN_SINGLE_STOCK_ALLOCATION) {
                continue;
            }

            if (!latestStockData.containsKey(symbol)) {
                continue;
            }

            StockData latestData = latestStockData.get(symbol);
            double currentPrice = latestData.getClose();

            double targetValue = totalValue * allocation;
            int shares = (int) Math.floor(targetValue / currentPrice);

            if (shares <= 0) {
                continue;
            }

            Portfolio.PortfolioStock stock = new Portfolio.PortfolioStock();
            stock.setSymbol(symbol);
            stock.setShares(shares);
            stock.setCurrentPrice(currentPrice);
            stock.setWeight(allocation * 100);

            boolean isExisting = false;
            for (Portfolio.PortfolioStock existingStock : portfolio.getStocks()) {
                if (existingStock.getSymbol().equals(symbol)) {
                    stock.setEntryPrice(existingStock.getEntryPrice());
                    stock.setEntryDate(existingStock.getEntryDate());
                    stock.setCompanyName(existingStock.getCompanyName());
                    isExisting = true;
                    break;
                }
            }

            if (!isExisting) {
                stock.setEntryPrice(currentPrice);
                stock.setEntryDate(LocalDateTime.now());
                stock.setCompanyName(getCompanyName(symbol));
            }

            double stockValue = shares * currentPrice;
            double cost = shares * stock.getEntryPrice();
            stock.setReturnValue(stockValue - cost);
            stock.setReturnPercentage(cost > 0 ? ((stockValue - cost) / cost) * 100 : 0);

            updatedStocks.add(stock);
        }

        portfolio.setStocks(updatedStocks);
        portfolio.setOptimizationStatus("UPGRADED_WITH_AI");
        portfolio.setLastOptimizedAt(LocalDateTime.now());
        portfolio.setUpdatedAt(LocalDateTime.now());

        updatePortfolioTotals(portfolio);

        historyService.recordAiRecommendation(
                portfolio,
                optimizedAllocations,
                0.5,
                "1.0");

        portfolio.setHasAiRecommendations(true);
        portfolio.setLastAiRecommendationDate(LocalDateTime.now());

        if (0.5 < 0.33) {
            portfolio.setAiRecommendationType("RISK_OPTIMIZED");
        } else if (0.5 < 0.67) {
            portfolio.setAiRecommendationType("BALANCED");
        } else {
            portfolio.setAiRecommendationType("RETURN_OPTIMIZED");
        }

        return portfolioRepository.save(portfolio);
    }

    private void updatePortfolioTotals(Portfolio portfolio) {
        double totalValue = 0;
        double totalCost = 0;

        for (Portfolio.PortfolioStock stock : portfolio.getStocks()) {
            double stockValue = stock.getCurrentPrice() * stock.getShares();
            double stockCost = stock.getEntryPrice() * stock.getShares();

            totalValue += stockValue;
            totalCost += stockCost;
        }

        portfolio.setTotalValue(totalValue);
        portfolio.setTotalReturn(totalValue - totalCost);
        portfolio.setTotalReturnPercentage(totalCost > 0 ? ((totalValue - totalCost) / totalCost) * 100 : 0);

        double riskScore = calculatePortfolioRiskScore(portfolio);
        portfolio.setRiskScore(riskScore);
    }

    private Set<String> expandStockUniverse(Set<String> currentSymbols, String userId) {
        Set<String> expandedSymbols = new HashSet<>();

        Set<String> userSymbols = stockDataRepository.findDistinctSymbolsByUserId(userId);

        List<String> potentialSymbols = Arrays.asList(
                "AAPL", "MSFT", "GOOGL", "AMZN", "META", "TSLA", "NVDA", "JPM",
                "JNJ", "V", "PG", "HD", "BAC", "MA", "DIS", "NFLX", "INTC", "VZ",
                "WMT", "UNH", "CVX", "KO", "PEP", "ABBV", "MRK", "TMO", "CSCO",
                "ADBE", "CRM", "AMD", "ORCL", "IBM", "GS", "MS", "BLK", "SCHW"
        );

        for (String symbol : userSymbols) {
            if (!currentSymbols.contains(symbol)) {
                expandedSymbols.add(symbol);
                if (expandedSymbols.size() >= config.getMaxExpansionStocks()) {
                    break;
                }
            }
        }

        if (expandedSymbols.size() < config.getMaxExpansionStocks()) {
            for (String symbol : potentialSymbols) {
                if (!currentSymbols.contains(symbol) && !expandedSymbols.contains(symbol)) {
                    expandedSymbols.add(symbol);
                    if (expandedSymbols.size() >= config.getMaxExpansionStocks()) {
                        break;
                    }
                }
            }
        }

        return expandedSymbols;
    }

    private Map<String, AnalysisData> collectStockAnalysisData(Set<String> symbols, String userId) throws IOException {
        Map<String, AnalysisData> analysisDataMap = new HashMap<>();
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(config.getHistoricalDays());

        for (String symbol : symbols) {
            try {
                AnalysisData data = new AnalysisData();
                data.symbol = symbol;

                List<StockData> historicalData = stockDataRepository
                        .findByUserIdAndSymbolAndDateBetweenOrderByDateAsc(userId, symbol, startDate, today);

                if (historicalData.isEmpty()) {
                    continue;
                }

                StockData latestData = historicalData.get(historicalData.size() - 1);
                data.currentPrice = latestData.getClose();

                for (int i = 1; i < historicalData.size(); i++) {
                    double previousClose = historicalData.get(i - 1).getClose();
                    double currentClose = historicalData.get(i).getClose();
                    double dailyReturn = (currentClose - previousClose) / previousClose;
                    data.historicalReturns.add(dailyReturn);
                }

                data.volatility = calculateVolatility(data.historicalReturns);

                double avgReturn = data.historicalReturns.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0);

                List<StockPrediction> predictions = predictionRepository
                        .findBySymbolOrderByPredictionDateDesc(symbol);

                StockPrediction prediction;
                if (!predictions.isEmpty() && userId.equals(predictions.get(0).getUserId())) {
                    prediction = predictions.get(0);
                } else {
                    List<StockPrediction> generatedPredictions = neuralNetworkService.predictFuturePrices(symbol, userId);
                    prediction = generatedPredictions.get(0);
                }

                data.predictedPrice = prediction.getPredictedPrice();
                data.predictedReturn = prediction.getPredictedChangePercentage() / 100.0;
                data.confidenceScore = prediction.getConfidenceScore() / 100.0;

                if (historicalData.size() >= 20) {
                    double recentPrice = historicalData.get(historicalData.size() - 20).getClose();
                    data.momentumScore = (data.currentPrice - recentPrice) / recentPrice;
                } else {
                    data.momentumScore = avgReturn * 20;
                }

                double riskFreeRate = 0.02 / 365;
                if (data.volatility > 0) {
                    data.sharpeRatio = (avgReturn - riskFreeRate) / data.volatility;
                } else {
                    data.sharpeRatio = 0;
                }

                data.technicalScore = calculateTechnicalScore(symbol, userId, today);

                analysisDataMap.put(symbol, data);
            } catch (Exception e) {
                System.err.println("Error collecting analysis data for " + symbol + ": " + e.getMessage());
            }
        }

        return analysisDataMap;
    }

    private double calculateTechnicalScore(String symbol, String userId, LocalDate date) {
        try {
            List<TechnicalIndicator> indicators = indicatorRepository
                    .findByUserIdAndSymbolAndDateBetweenOrderByDateAsc(
                            userId, symbol, date.minusDays(30), date);

            if (indicators.isEmpty()) {
                return 0.5;
            }

            TechnicalIndicator latest = indicators.get(indicators.size() - 1);
            double score = 0.5;

            if (latest.getRsi14() != null) {
                if (latest.getRsi14() < 30) {
                    score += 0.2;
                } else if (latest.getRsi14() > 70) {
                    score -= 0.2;
                }
            }

            if (latest.getSma20() != null && latest.getSma50() != null) {
                if (latest.getPrice() > latest.getSma20() && latest.getSma20() > latest.getSma50()) {
                    score += 0.15;
                } else if (latest.getPrice() < latest.getSma20() && latest.getSma20() < latest.getSma50()) {
                    score -= 0.15;
                }
            }

            if (latest.getMacdHistogram() != null) {
                if (latest.getMacdHistogram() > 0) {
                    score += 0.1;
                } else {
                    score -= 0.1;
                }
            }

            if (latest.getBollingerUpper() != null && latest.getBollingerLower() != null) {
                double bbRange = latest.getBollingerUpper() - latest.getBollingerLower();
                double bbPosition = (latest.getPrice() - latest.getBollingerLower()) / bbRange;

                if (bbPosition < 0.2) {
                    score += 0.15;
                } else if (bbPosition > 0.8) {
                    score -= 0.15;
                }
            }

            return Math.max(0, Math.min(1, score));
        } catch (Exception e) {
            return 0.5;
        }
    }

    private double calculateVolatility(List<Double> returns) {
        if (returns.isEmpty()) {
            return 0;
        }

        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double sumSquaredDiff = returns.stream()
                .mapToDouble(r -> Math.pow(r - mean, 2))
                .sum();

        return Math.sqrt(sumSquaredDiff / returns.size());
    }

    private Map<String, Double> calculateOptimalAllocations(
            Map<String, AnalysisData> analysisData,
            Set<String> currentSymbols,
            double riskTolerance) {

        Map<String, Double> allocations = new HashMap<>();
        Map<String, Double> stockScores = new HashMap<>();

        for (Map.Entry<String, AnalysisData> entry : analysisData.entrySet()) {
            String symbol = entry.getKey();
            AnalysisData data = entry.getValue();

            double score = 0;

            double returnScore = data.predictedReturn * data.confidenceScore;
            score += returnScore * 0.3;

            score += Math.tanh(data.sharpeRatio * 0.5) * 0.2;

            score += Math.tanh(data.momentumScore * 2) * 0.15;

            score += (data.technicalScore - 0.5) * 0.15;

            double volatilityPenalty = data.volatility * (1 - riskTolerance) * 0.2;
            score -= volatilityPenalty;

            score += (data.confidenceScore - 0.5) * 0.1;

            if (currentSymbols.contains(symbol)) {
                score *= 1.1;
            }

            stockScores.put(symbol, Math.max(0, score));
        }

        List<Map.Entry<String, Double>> sortedStocks = stockScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toList());

        double totalScore = stockScores.values().stream().mapToDouble(Double::doubleValue).sum();

        if (totalScore > 0) {
            int targetStocks = (int) (5 + (1 - riskTolerance) * 10);
            targetStocks = Math.min(targetStocks, sortedStocks.size());

            double allocatedWeight = 0;
            for (int i = 0; i < targetStocks; i++) {
                Map.Entry<String, Double> entry = sortedStocks.get(i);
                String symbol = entry.getKey();
                double score = entry.getValue();

                double baseAllocation = score / totalScore;

                double allocation = Math.min(baseAllocation * 1.5, config.getMaxStockAllocation());

                if (allocation >= config.getMinStockAllocation()) {
                    allocations.put(symbol, allocation);
                    allocatedWeight += allocation;
                }
            }

            if (allocatedWeight > 0 && Math.abs(allocatedWeight - 1.0) > 0.01) {
                for (String symbol : allocations.keySet()) {
                    allocations.put(symbol, allocations.get(symbol) / allocatedWeight);
                }
            }
        }

        if (allocations.isEmpty() && !analysisData.isEmpty()) {
            double equalWeight = 1.0 / currentSymbols.size();
            for (String symbol : currentSymbols) {
                if (analysisData.containsKey(symbol)) {
                    allocations.put(symbol, equalWeight);
                }
            }
        }

        return allocations;
    }

    private List<Map<String, Object>> generateStockActions(
            Portfolio portfolio,
            Map<String, Double> optimizedAllocations,
            Map<String, AnalysisData> analysisData) {

        List<Map<String, Object>> actions = new ArrayList<>();
        double totalValue = portfolio.getTotalValue() != null ? portfolio.getTotalValue() : 0;

        Map<String, Portfolio.PortfolioStock> currentHoldings = portfolio.getStocks().stream()
                .collect(Collectors.toMap(Portfolio.PortfolioStock::getSymbol, s -> s));

        for (Map.Entry<String, Double> entry : optimizedAllocations.entrySet()) {
            String symbol = entry.getKey();
            double targetAllocation = entry.getValue();

            Map<String, Object> action = new HashMap<>();
            action.put("symbol", symbol);

            Portfolio.PortfolioStock currentStock = currentHoldings.get(symbol);
            if (currentStock != null) {
                action.put("currentShares", currentStock.getShares());
                double currentValue = currentStock.getCurrentPrice() * currentStock.getShares();
                double currentAllocation = totalValue > 0 ? currentValue / totalValue : 0;

                double targetValue = totalValue * targetAllocation;
                int targetShares = (int) Math.floor(targetValue / currentStock.getCurrentPrice());
                action.put("targetShares", targetShares);
                action.put("currentPrice", currentStock.getCurrentPrice());

                if (targetShares > currentStock.getShares() * 1.1) {
                    action.put("action", "BUY");
                    AnalysisData data = analysisData.get(symbol);
                    action.put("reason", String.format("Increase allocation from %.1f%% to %.1f%%. Expected return: %.1f%%, Technical score: %.2f, Momentum: %.1f%%, Strong growth potential",
                            currentAllocation * 100, targetAllocation * 100,
                            data != null ? data.predictedReturn * 100 : 0,
                            data != null ? data.technicalScore : 0,
                            data != null ? data.momentumScore * 100 : 0));
                } else if (targetShares < currentStock.getShares() * 0.9) {
                    action.put("action", "SELL");
                    AnalysisData data = analysisData.get(symbol);
                    if (data != null && data.predictedReturn < 0) {
                        action.put("reason", String.format("Decrease allocation from %.1f%% to %.1f%%. Negative expected return: %.1f%%, Better opportunities in other holdings",
                                currentAllocation * 100, targetAllocation * 100, data.predictedReturn * 100));
                    } else {
                        action.put("reason", String.format("Decrease allocation from %.1f%% to %.1f%%. Rebalancing to optimize portfolio risk-return profile",
                                currentAllocation * 100, targetAllocation * 100));
                    }
                } else {
                    action.put("action", "HOLD");
                    AnalysisData data = analysisData.get(symbol);
                    action.put("reason", String.format("Current allocation of %.1f%% is optimal. Expected return: %.1f%%, Confidence: %.1f%%",
                            currentAllocation * 100,
                            data != null ? data.predictedReturn * 100 : 0,
                            data != null ? data.confidenceScore * 100 : 0));
                }
            } else {
                action.put("currentShares", 0);
                action.put("action", "BUY");

                AnalysisData data = analysisData.get(symbol);
                if (data != null) {
                    action.put("currentPrice", data.currentPrice);
                    int targetShares = (int) Math.floor((totalValue * targetAllocation) / data.currentPrice);
                    action.put("targetShares", targetShares);
                    action.put("reason", String.format("New position with %.1f%% allocation. Expected return: %.1f%%, Confidence: %.1f%%, Technical score: %.2f, Strong upward momentum",
                            targetAllocation * 100, data.predictedReturn * 100,
                            data.confidenceScore * 100, data.technicalScore));
                }
            }

            actions.add(action);
        }

        for (Portfolio.PortfolioStock stock : portfolio.getStocks()) {
            if (!optimizedAllocations.containsKey(stock.getSymbol())) {
                Map<String, Object> action = new HashMap<>();
                action.put("symbol", stock.getSymbol());
                action.put("action", "SELL");
                action.put("currentShares", stock.getShares());
                action.put("targetShares", 0);
                action.put("currentPrice", stock.getCurrentPrice());

                AnalysisData data = analysisData.get(stock.getSymbol());
                if (data != null && data.predictedReturn < -0.05) {
                    action.put("reason", String.format("Remove from portfolio - negative expected return: %.1f%%. Risk outweighs potential reward",
                            data.predictedReturn * 100));
                } else if (data != null && data.technicalScore < 0.3) {
                    action.put("reason", "Remove from portfolio - weak technical indicators suggest downward pressure");
                } else {
                    action.put("reason", "Remove from portfolio - AI analysis identifies superior opportunities in recommended holdings");
                }
                actions.add(action);
            }
        }

        actions.sort((a, b) -> {
            int actionPriority = getActionPriority((String) a.get("action")) - getActionPriority((String) b.get("action"));
            if (actionPriority != 0) return actionPriority;
            return Double.compare((Integer) b.get("targetShares"), (Integer) a.get("targetShares"));
        });

        return actions;
    }

    private int getActionPriority(String action) {
        switch (action) {
            case "BUY": return 1;
            case "SELL": return 2;
            case "HOLD": return 3;
            default: return 4;
        }
    }

    private Map<String, Object> calculateExpectedPerformance(
            Map<String, Double> allocations,
            Map<String, AnalysisData> analysisData) {

        Map<String, Object> performance = new HashMap<>();

        double expectedReturn = 0;
        double expectedVolatility = 0;
        double weightedSharpe = 0;

        for (Map.Entry<String, Double> entry : allocations.entrySet()) {
            String symbol = entry.getKey();
            double allocation = entry.getValue();
            AnalysisData data = analysisData.get(symbol);

            if (data != null) {
                expectedReturn += allocation * data.predictedReturn;
                expectedVolatility += allocation * allocation * data.volatility * data.volatility;
                weightedSharpe += allocation * data.sharpeRatio;
            }
        }

        expectedVolatility = Math.sqrt(expectedVolatility);

        double annualReturn = expectedReturn * 252;
        double annualVolatility = expectedVolatility * Math.sqrt(252);

        double riskFreeRate = 0.02;
        double sharpeRatio = annualVolatility > 0 ? (annualReturn - riskFreeRate) / annualVolatility : 0;

        performance.put("expectedAnnualReturn", annualReturn * 100);
        performance.put("expectedAnnualVolatility", annualVolatility * 100);
        performance.put("sharpeRatio", sharpeRatio);
        performance.put("riskAdjustedReturn", (annualReturn - annualVolatility) * 100);
        performance.put("weightedSharpeRatio", weightedSharpe);

        return performance;
    }

    private Map<String, Object> calculateImprovementMetrics(
            Portfolio portfolio,
            Map<String, Double> optimizedAllocations,
            Map<String, AnalysisData> analysisData) {

        Map<String, Object> metrics = new HashMap<>();

        double currentReturn = 0;
        double currentVolatility = 0;
        double currentSharpe = 0;
        double totalValue = portfolio.getTotalValue() != null ? portfolio.getTotalValue() : 0;

        if (totalValue > 0) {
            for (Portfolio.PortfolioStock stock : portfolio.getStocks()) {
                double allocation = (stock.getCurrentPrice() * stock.getShares()) / totalValue;
                AnalysisData data = analysisData.get(stock.getSymbol());

                if (data != null) {
                    currentReturn += allocation * data.predictedReturn;
                    currentVolatility += allocation * allocation * data.volatility * data.volatility;
                    currentSharpe += allocation * data.sharpeRatio;
                }
            }
            currentVolatility = Math.sqrt(currentVolatility);
        }

        Map<String, Object> optimizedPerformance = calculateExpectedPerformance(optimizedAllocations, analysisData);
        double optimizedReturn = (Double) optimizedPerformance.get("expectedAnnualReturn") / 100;
        double optimizedVolatility = (Double) optimizedPerformance.get("expectedAnnualVolatility") / 100;
        double optimizedSharpe = (Double) optimizedPerformance.get("sharpeRatio");

        double returnImprovement = (optimizedReturn - currentReturn * 252) * 100;
        double riskReduction = (currentVolatility * Math.sqrt(252) - optimizedVolatility) * 100;
        double sharpeImprovement = optimizedSharpe - currentSharpe;

        double improvementScore = 0;
        if (returnImprovement > 0) improvementScore += Math.min(50, returnImprovement * 5);
        if (riskReduction > 0) improvementScore += Math.min(30, riskReduction * 3);
        if (sharpeImprovement > 0) improvementScore += Math.min(20, sharpeImprovement * 10);

        metrics.put("currentExpectedReturn", currentReturn * 252 * 100);
        metrics.put("currentVolatility", currentVolatility * Math.sqrt(252) * 100);
        metrics.put("currentSharpeRatio", currentSharpe);
        metrics.put("returnImprovement", returnImprovement);
        metrics.put("riskReduction", riskReduction);
        metrics.put("sharpeImprovement", sharpeImprovement);
        metrics.put("overallImprovementScore", Math.min(100, improvementScore));

        return metrics;
    }

    private Map<String, StockData> getLatestStockData(Set<String> symbols, String userId) {
        Map<String, StockData> result = new HashMap<>();
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(5);

        for (String symbol : symbols) {
            List<StockData> recentData = stockDataRepository
                    .findByUserIdAndSymbolAndDateBetweenOrderByDateAsc(userId, symbol, startDate, today);

            if (!recentData.isEmpty()) {
                result.put(symbol, recentData.get(recentData.size() - 1));
            }
        }

        return result;
    }

    private double calculatePortfolioRiskScore(Portfolio portfolio) {
        int stockCount = portfolio.getStocks().size();
        double diversificationScore = Math.min(stockCount / 10.0, 1.0) * 30;

        double maxWeight = portfolio.getStocks().stream()
                .mapToDouble(Portfolio.PortfolioStock::getWeight)
                .max()
                .orElse(0);
        double concentrationRisk = maxWeight > 25 ? (maxWeight - 25) * 2 : 0;

        double volatilityScore = 50;

        return Math.min(100, diversificationScore + volatilityScore - concentrationRisk);
    }

    private String getCompanyName(String symbol) {
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
        commonStocks.put("NFLX", "Netflix, Inc.");
        commonStocks.put("ADBE", "Adobe Inc.");
        commonStocks.put("CRM", "Salesforce, Inc.");
        commonStocks.put("PFE", "Pfizer Inc.");
        commonStocks.put("TMO", "Thermo Fisher Scientific Inc.");
        commonStocks.put("CSCO", "Cisco Systems, Inc.");
        commonStocks.put("WMT", "Walmart Inc.");
        commonStocks.put("CVX", "Chevron Corporation");
        commonStocks.put("PEP", "PepsiCo, Inc.");
        commonStocks.put("ABBV", "AbbVie Inc.");
        commonStocks.put("KO", "The Coca-Cola Company");
        commonStocks.put("MRK", "Merck & Co., Inc.");
        commonStocks.put("VZ", "Verizon Communications Inc.");
        commonStocks.put("INTC", "Intel Corporation");
        commonStocks.put("AMD", "Advanced Micro Devices, Inc.");
        return commonStocks.getOrDefault(symbol.toUpperCase(), symbol + " Corp");
    }

    private double calculateAIConfidenceScore(Map<String, AnalysisData> analysisData) {
        if (analysisData.isEmpty()) return 0;

        double avgConfidence = analysisData.values().stream()
                .mapToDouble(data -> data.confidenceScore)
                .average()
                .orElse(0);

        double dataQuality = analysisData.values().stream()
                .mapToDouble(data -> Math.min(data.historicalReturns.size() / 250.0, 1.0))
                .average()
                .orElse(0);

        double technicalReliability = analysisData.values().stream()
                .mapToDouble(data -> Math.abs(data.technicalScore - 0.5) * 2)
                .average()
                .orElse(0);

        return (avgConfidence * 0.5 + dataQuality * 0.3 + technicalReliability * 0.2) * 100;
    }

    private static class AnalysisData {
        String symbol;
        double currentPrice;
        double predictedPrice;
        double predictedReturn;
        double volatility;
        double sharpeRatio;
        double confidenceScore;
        double momentumScore;
        double technicalScore;
        List<Double> historicalReturns = new ArrayList<>();
    }
}