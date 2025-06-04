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

/**
 * AI-based service for upgrading investment portfolios
 * Uses multiple machine learning approaches to optimize portfolio allocation
 */
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

    // Configuration constants
    private static final int HISTORICAL_DAYS = 365; // 1 year of data for analysis
    private static final int PREDICTION_HORIZON = 90; // Looking ahead 3 months
    private static final double MAX_SINGLE_STOCK_ALLOCATION = 0.25; // Max 25% in any one stock
    private static final double MIN_SINGLE_STOCK_ALLOCATION = 0.02; // Min 2% or don't include

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

    /**
     * Main method to generate an upgraded portfolio based on AI analysis
     *
     * @param portfolioId ID of the portfolio to upgrade
     * @param riskTolerance User's risk tolerance (0-1 scale where 0 is low risk and 1 is high risk)
     * @param stockUniverseExpansion Whether to consider adding new stocks to the portfolio
     * @return Map containing upgrade recommendations and statistics
     */
    @Cacheable(value = "portfolioRecommendations", key = "#portfolioId + '-' + #riskTolerance + '-' + #stockUniverseExpansion")
    public Map<String, Object> generatePortfolioUpgrade(
            String portfolioId,
            double riskTolerance,
            boolean stockUniverseExpansion) throws IOException {

        // Get the current portfolio
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));

        // Gather all required data
        Set<String> currentSymbols = portfolio.getStocks().stream()
                .map(Portfolio.PortfolioStock::getSymbol)
                .collect(Collectors.toSet());

        // Expand universe with related/recommended stocks if requested
        Set<String> analyzedSymbols = new HashSet<>(currentSymbols);
        if (stockUniverseExpansion) {
            Set<String> expandedSymbols = expandStockUniverse(currentSymbols);
            analyzedSymbols.addAll(expandedSymbols);
        }

        // Collect required data for analysis
        Map<String, AnalysisData> stockAnalysisData = collectStockAnalysisData(analyzedSymbols, portfolio.getUserId());

        // Create portfolio allocation model using predictive AI
        Map<String, Double> optimizedAllocations = calculateOptimalAllocations(
                stockAnalysisData, currentSymbols, riskTolerance);

        // Generate specific action recommendations
        List<StockAction> recommendedActions = generateStockActions(
                portfolio, optimizedAllocations, stockAnalysisData);

        // Calculate expected performance metrics
        Map<String, Object> expectedPerformance = calculateExpectedPerformance(
                optimizedAllocations, stockAnalysisData);

        // Calculate improvement metrics versus current portfolio
        Map<String, Object> improvementMetrics = calculateImprovementMetrics(
                portfolio, optimizedAllocations, stockAnalysisData);

        // Create response with all recommendations and data
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

    /**
     * Apply the recommended upgrades to the portfolio
     *
     * @param portfolioId ID of the portfolio to upgrade
     * @param optimizedAllocations Map of symbol to allocation percentage
     * @return Updated portfolio entity
     */
    @CacheEvict(value = "portfolioRecommendations", key = "#portfolioId + '*'")
    public Portfolio applyPortfolioUpgrade(String portfolioId, Map<String, Double> optimizedAllocations) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));

        // Current total portfolio value
        double totalValue = portfolio.getTotalValue() != null ? portfolio.getTotalValue() : 0;
        if (totalValue <= 0) {
            throw new IllegalStateException("Cannot upgrade portfolio with zero or unknown value");
        }

        // Get current stock data for new symbols
        Set<String> currentSymbols = portfolio.getStocks().stream()
                .map(Portfolio.PortfolioStock::getSymbol)
                .collect(Collectors.toSet());

        Set<String> allSymbols = new HashSet<>(optimizedAllocations.keySet());

        Map<String, StockData> latestStockData = getLatestStockData(allSymbols);

        // Create updated portfolio stocks list
        List<Portfolio.PortfolioStock> updatedStocks = new ArrayList<>();

        // Process each symbol in the optimized allocations
        for (Map.Entry<String, Double> entry : optimizedAllocations.entrySet()) {
            String symbol = entry.getKey();
            double allocation = entry.getValue();

            if (allocation < MIN_SINGLE_STOCK_ALLOCATION) {
                continue; // Skip stocks with very small allocations
            }

            // Get latest price
            if (!latestStockData.containsKey(symbol)) {
                continue; // Skip if no price data
            }

            StockData latestData = latestStockData.get(symbol);
            double currentPrice = latestData.getClose();

            // Calculate target value and shares
            double targetValue = totalValue * allocation;
            int shares = (int) Math.floor(targetValue / currentPrice);

            if (shares <= 0) {
                continue; // Skip if no shares to buy
            }

            // Create new stock entry
            Portfolio.PortfolioStock stock = new Portfolio.PortfolioStock();
            stock.setSymbol(symbol);
            stock.setShares(shares);
            stock.setCurrentPrice(currentPrice);
            stock.setWeight(allocation * 100); // Convert to percentage

            // If existing stock, keep original entry data
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

            // If new stock, set entry data
            if (!isExisting) {
                stock.setEntryPrice(currentPrice);
                stock.setEntryDate(LocalDateTime.now());
                stock.setCompanyName(getCompanyName(symbol));
            }

            // Calculate returns
            double stockValue = shares * currentPrice;
            double cost = shares * stock.getEntryPrice();
            stock.setReturnValue(stockValue - cost);
            stock.setReturnPercentage(cost > 0 ? ((stockValue - cost) / cost) * 100 : 0);

            updatedStocks.add(stock);
        }

        // Update portfolio
        portfolio.setStocks(updatedStocks);
        portfolio.setOptimizationStatus("UPGRADED_WITH_AI");
        portfolio.setLastOptimizedAt(LocalDateTime.now());
        portfolio.setUpdatedAt(LocalDateTime.now());

        // Recalculate portfolio totals
        updatePortfolioTotals(portfolio);

        // Record AI recommendation history
        historyService.recordAiRecommendation(
                portfolio,
                optimizedAllocations,
                0.5, // Default risk tolerance - you can pass this as parameter
                "1.0"); // Version number

        // Update portfolio AI recommendation fields
        portfolio.setHasAiRecommendations(true);
        portfolio.setLastAiRecommendationDate(LocalDateTime.now());

        // Determine recommendation type
        if (0.5 < 0.33) {
            portfolio.setAiRecommendationType("RISK_OPTIMIZED");
        } else if (0.5 < 0.67) {
            portfolio.setAiRecommendationType("BALANCED");
        } else {
            portfolio.setAiRecommendationType("RETURN_OPTIMIZED");
        }

        // Save and return
        return portfolioRepository.save(portfolio);
    }

    // Private helper methods

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

        // Update risk score
        double riskScore = calculatePortfolioRiskScore(portfolio);
        portfolio.setRiskScore(riskScore);
    }

    private Set<String> expandStockUniverse(Set<String> currentSymbols) {
        Set<String> expandedSymbols = new HashSet<>();
        Set<String> allAvailableSymbols = stockDataRepository.findDistinctSymbols();

        List<String> potentialSymbols = Arrays.asList(
                "AAPL", "MSFT", "GOOGL", "AMZN", "META", "TSLA", "NVDA", "JPM",
                "JNJ", "V", "PG", "HD", "BAC", "MA", "DIS", "NFLX", "INTC", "VZ"
        );

        for (String symbol : potentialSymbols) {
            if (!currentSymbols.contains(symbol) && allAvailableSymbols.contains(symbol)) {
                expandedSymbols.add(symbol);
                if (expandedSymbols.size() >= config.getMaxExpansionStocks()) {
                    break;
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
                        .findBySymbolAndDateBetweenOrderByDateAsc(symbol, startDate, today);

                if (historicalData.isEmpty()) {
                    continue;
                }

                StockData latestData = historicalData.get(historicalData.size() - 1);
                data.currentPrice = latestData.getClose();

                // Calculate historical returns
                for (int i = 1; i < historicalData.size(); i++) {
                    double previousClose = historicalData.get(i - 1).getClose();
                    double currentClose = historicalData.get(i).getClose();
                    double dailyReturn = (currentClose - previousClose) / previousClose;
                    data.historicalReturns.add(dailyReturn);
                }

                data.volatility = calculateVolatility(data.historicalReturns);

                // Get or generate prediction
                List<StockPrediction> predictions = predictionRepository
                        .findBySymbolOrderByPredictionDateDesc(symbol);

                StockPrediction prediction;
                if (!predictions.isEmpty()) {
                    prediction = predictions.get(0);
                } else {
                    List<StockPrediction> generatedPredictions = neuralNetworkService.predictFuturePrices(symbol, userId);
                    prediction = generatedPredictions.get(0);
                }

                data.predictedPrice = prediction.getPredictedPrice();
                data.predictedReturn = prediction.getPredictedChangePercentage() / 100.0;
                data.confidenceScore = prediction.getConfidenceScore() / 100.0;

                // Calculate Sharpe ratio
                double riskFreeRate = 0.02 / 365;
                double avgReturn = data.historicalReturns.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0);

                if (data.volatility > 0) {
                    data.sharpeRatio = (avgReturn - riskFreeRate) / data.volatility;
                } else {
                    data.sharpeRatio = 0;
                }

                analysisDataMap.put(symbol, data);
            } catch (Exception e) {
                System.err.println("Error collecting analysis data for " + symbol + ": " + e.getMessage());
            }
        }

        return analysisDataMap;
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

        // Score each stock based on return, risk, and confidence
        Map<String, Double> stockScores = new HashMap<>();

        for (Map.Entry<String, AnalysisData> entry : analysisData.entrySet()) {
            String symbol = entry.getKey();
            AnalysisData data = entry.getValue();

            // Calculate composite score
            double returnScore = data.predictedReturn * data.confidenceScore;
            double riskAdjustedScore = returnScore - (data.volatility * (1 - riskTolerance));
            double sharpeBonus = Math.max(0, data.sharpeRatio * 0.1);

            double totalScore = riskAdjustedScore + sharpeBonus;

            // Give bonus to existing stocks (less transaction costs)
            if (currentSymbols.contains(symbol)) {
                totalScore *= 1.1;
            }

            stockScores.put(symbol, Math.max(0, totalScore));
        }

        // Normalize scores to create allocations
        double totalScore = stockScores.values().stream().mapToDouble(Double::doubleValue).sum();

        if (totalScore > 0) {
            for (Map.Entry<String, Double> entry : stockScores.entrySet()) {
                double allocation = entry.getValue() / totalScore;

                // Apply constraints
                allocation = Math.min(allocation, config.getMaxStockAllocation());
                if (allocation >= config.getMinStockAllocation()) {
                    allocations.put(entry.getKey(), allocation);
                }
            }
        }

        // Re-normalize after constraints
        double sum = allocations.values().stream().mapToDouble(Double::doubleValue).sum();
        if (sum > 0 && Math.abs(sum - 1.0) > 0.01) {
            for (String symbol : allocations.keySet()) {
                allocations.put(symbol, allocations.get(symbol) / sum);
            }
        }

        return allocations;
    }

    private List<StockAction> generateStockActions(
            Portfolio portfolio,
            Map<String, Double> optimizedAllocations,
            Map<String, AnalysisData> analysisData) {

        List<StockAction> actions = new ArrayList<>();
        double totalValue = portfolio.getTotalValue() != null ? portfolio.getTotalValue() : 0;

        // Current holdings
        Map<String, Portfolio.PortfolioStock> currentHoldings = portfolio.getStocks().stream()
                .collect(Collectors.toMap(Portfolio.PortfolioStock::getSymbol, s -> s));

        // Generate actions for each symbol
        for (Map.Entry<String, Double> entry : optimizedAllocations.entrySet()) {
            String symbol = entry.getKey();
            double targetAllocation = entry.getValue();

            StockAction action = new StockAction();
            action.symbol = symbol;

            Portfolio.PortfolioStock currentStock = currentHoldings.get(symbol);
            if (currentStock != null) {
                action.currentShares = currentStock.getShares();
                double currentValue = currentStock.getCurrentPrice() * currentStock.getShares();
                double currentAllocation = currentValue / totalValue;

                double targetValue = totalValue * targetAllocation;
                action.targetShares = (int) Math.floor(targetValue / currentStock.getCurrentPrice());
                action.currentPrice = currentStock.getCurrentPrice();

                if (action.targetShares > action.currentShares) {
                    action.action = "BUY";
                    action.reason = String.format("Increase allocation from %.1f%% to %.1f%%",
                            currentAllocation * 100, targetAllocation * 100);
                } else if (action.targetShares < action.currentShares) {
                    action.action = "SELL";
                    action.reason = String.format("Decrease allocation from %.1f%% to %.1f%%",
                            currentAllocation * 100, targetAllocation * 100);
                } else {
                    action.action = "HOLD";
                    action.reason = "Current allocation is optimal";
                }
            } else {
                action.currentShares = 0;
                action.action = "BUY";

                AnalysisData data = analysisData.get(symbol);
                if (data != null) {
                    action.currentPrice = data.currentPrice;
                    action.targetShares = (int) Math.floor((totalValue * targetAllocation) / data.currentPrice);
                    action.reason = String.format("New position with %.1f%% allocation, expected return: %.1f%%",
                            targetAllocation * 100, data.predictedReturn * 100);
                }
            }

            actions.add(action);
        }

        // Add SELL actions for stocks not in optimized portfolio
        for (Portfolio.PortfolioStock stock : portfolio.getStocks()) {
            if (!optimizedAllocations.containsKey(stock.getSymbol())) {
                StockAction action = new StockAction();
                action.symbol = stock.getSymbol();
                action.action = "SELL";
                action.currentShares = stock.getShares();
                action.targetShares = 0;
                action.currentPrice = stock.getCurrentPrice();
                action.reason = "Remove from portfolio - better opportunities available";
                actions.add(action);
            }
        }

        return actions;
    }

    private Map<String, Object> calculateExpectedPerformance(
            Map<String, Double> allocations,
            Map<String, AnalysisData> analysisData) {

        Map<String, Object> performance = new HashMap<>();

        double expectedReturn = 0;
        double expectedVolatility = 0;

        // Calculate weighted average return
        for (Map.Entry<String, Double> entry : allocations.entrySet()) {
            String symbol = entry.getKey();
            double allocation = entry.getValue();
            AnalysisData data = analysisData.get(symbol);

            if (data != null) {
                expectedReturn += allocation * data.predictedReturn;
                expectedVolatility += allocation * allocation * data.volatility * data.volatility;
            }
        }

        expectedVolatility = Math.sqrt(expectedVolatility);

        // Calculate Sharpe ratio
        double riskFreeRate = 0.02; // 2% annual
        double sharpeRatio = (expectedReturn - riskFreeRate) / expectedVolatility;

        performance.put("expectedAnnualReturn", expectedReturn * 100);
        performance.put("expectedAnnualVolatility", expectedVolatility * 100);
        performance.put("sharpeRatio", sharpeRatio);
        performance.put("riskAdjustedReturn", expectedReturn - expectedVolatility);

        return performance;
    }

    private Map<String, Object> calculateImprovementMetrics(
            Portfolio portfolio,
            Map<String, Double> optimizedAllocations,
            Map<String, AnalysisData> analysisData) {

        Map<String, Object> metrics = new HashMap<>();

        // Calculate current portfolio metrics
        double currentReturn = 0;
        double currentVolatility = 0;
        double totalValue = portfolio.getTotalValue() != null ? portfolio.getTotalValue() : 0;

        if (totalValue > 0) {
            for (Portfolio.PortfolioStock stock : portfolio.getStocks()) {
                double allocation = (stock.getCurrentPrice() * stock.getShares()) / totalValue;
                AnalysisData data = analysisData.get(stock.getSymbol());

                if (data != null) {
                    currentReturn += allocation * data.predictedReturn;
                    currentVolatility += allocation * allocation * data.volatility * data.volatility;
                }
            }
            currentVolatility = Math.sqrt(currentVolatility);
        }

        // Calculate optimized portfolio metrics
        Map<String, Object> optimizedPerformance = calculateExpectedPerformance(optimizedAllocations, analysisData);
        double optimizedReturn = (Double) optimizedPerformance.get("expectedAnnualReturn") / 100;
        double optimizedVolatility = (Double) optimizedPerformance.get("expectedAnnualVolatility") / 100;

        // Calculate improvements
        double returnImprovement = (optimizedReturn - currentReturn) * 100;
        double riskReduction = (currentVolatility - optimizedVolatility) * 100;

        // Calculate overall improvement score (0-100)
        double improvementScore = 0;
        if (returnImprovement > 0) improvementScore += 50 * (returnImprovement / 10); // Max 50 points for 10% return improvement
        if (riskReduction > 0) improvementScore += 50 * (riskReduction / 10); // Max 50 points for 10% risk reduction
        improvementScore = Math.min(100, Math.max(0, improvementScore));

        metrics.put("currentExpectedReturn", currentReturn * 100);
        metrics.put("currentVolatility", currentVolatility * 100);
        metrics.put("returnImprovement", returnImprovement);
        metrics.put("riskReduction", riskReduction);
        metrics.put("overallImprovementScore", improvementScore);

        return metrics;
    }

    private Map<String, StockData> getLatestStockData(Set<String> symbols) {
        Map<String, StockData> result = new HashMap<>();
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(5);

        for (String symbol : symbols) {
            List<StockData> recentData = stockDataRepository
                    .findBySymbolAndDateBetweenOrderByDateAsc(symbol, startDate, today);

            if (!recentData.isEmpty()) {
                result.put(symbol, recentData.get(recentData.size() - 1));
            }
        }

        return result;
    }

    private double calculatePortfolioRiskScore(Portfolio portfolio) {
        // Simplified risk score calculation based on diversification and volatility
        int stockCount = portfolio.getStocks().size();
        double diversificationScore = Math.min(stockCount / 10.0, 1.0) * 50;

        // Add volatility component (simplified)
        double volatilityScore = 50; // Default medium risk

        return diversificationScore + (100 - diversificationScore) * (volatilityScore / 100);
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
        return commonStocks.getOrDefault(symbol, symbol + " Corp");
    }

    private double calculateAIConfidenceScore(Map<String, AnalysisData> analysisData) {
        if (analysisData.isEmpty()) return 0;

        // Average confidence score across all analyzed stocks
        double avgConfidence = analysisData.values().stream()
                .mapToDouble(data -> data.confidenceScore)
                .average()
                .orElse(0);

        // Factor in data quality (more data points = higher confidence)
        double dataQuality = analysisData.values().stream()
                .mapToDouble(data -> Math.min(data.historicalReturns.size() / 250.0, 1.0))
                .average()
                .orElse(0);

        return (avgConfidence * 0.7 + dataQuality * 0.3) * 100;
    }

    // Inner classes
    private static class AnalysisData {
        String symbol;
        double currentPrice;
        double predictedPrice;
        double predictedReturn;
        double volatility;
        double sharpeRatio;
        double confidenceScore;
        List<Double> historicalReturns = new ArrayList<>();
    }

    private static class StockAction {
        String symbol;
        String action;
        int currentShares;
        int targetShares;
        double currentPrice;
        String reason;
    }
}