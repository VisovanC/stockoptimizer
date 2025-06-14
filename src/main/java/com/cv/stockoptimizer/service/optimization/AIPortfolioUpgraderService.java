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
            Set<String> expandedSymbols = expandStockUniverse(currentSymbols, portfolio.getUserId());
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

        Map<String, StockData> latestStockData = getLatestStockData(allSymbols, portfolio.getUserId());

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

    private Set<String> expandStockUniverse(Set<String> currentSymbols, String userId) {
        Set<String> expandedSymbols = new HashSet<>();

        // Get all available symbols for this user
        Set<String> userSymbols = stockDataRepository.findDistinctSymbolsByUserId(userId);

        // Common high-performing stocks to consider
        List<String> potentialSymbols = Arrays.asList(
                "AAPL", "MSFT", "GOOGL", "AMZN", "META", "TSLA", "NVDA", "JPM",
                "JNJ", "V", "PG", "HD", "BAC", "MA", "DIS", "NFLX", "INTC", "VZ",
                "WMT", "UNH", "CVX", "KO", "PEP", "ABBV", "MRK", "TMO", "CSCO",
                "ADBE", "CRM", "AMD", "ORCL", "IBM", "GS", "MS", "BLK", "SCHW"
        );

        // Add symbols that exist in user's data but not in current portfolio
        for (String symbol : userSymbols) {
            if (!currentSymbols.contains(symbol)) {
                expandedSymbols.add(symbol);
                if (expandedSymbols.size() >= config.getMaxExpansionStocks()) {
                    break;
                }
            }
        }

        // If still room, add from potential symbols
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

                // Get historical data for this user
                List<StockData> historicalData = stockDataRepository
                        .findByUserIdAndSymbolAndDateBetweenOrderByDateAsc(userId, symbol, startDate, today);

                if (historicalData.isEmpty()) {
                    continue;
                }

                StockData latestData = historicalData.get(historicalData.size() - 1);
                data.currentPrice = latestData.getClose();

                // Calculate historical returns and metrics
                for (int i = 1; i < historicalData.size(); i++) {
                    double previousClose = historicalData.get(i - 1).getClose();
                    double currentClose = historicalData.get(i).getClose();
                    double dailyReturn = (currentClose - previousClose) / previousClose;
                    data.historicalReturns.add(dailyReturn);
                }

                data.volatility = calculateVolatility(data.historicalReturns);

                // Calculate average return
                double avgReturn = data.historicalReturns.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0);

                // Get or generate prediction
                List<StockPrediction> predictions = predictionRepository
                        .findBySymbolOrderByPredictionDateDesc(symbol);

                StockPrediction prediction;
                if (!predictions.isEmpty() && predictions.get(0).getUserId().equals(userId)) {
                    prediction = predictions.get(0);
                } else {
                    // Generate new prediction for this user
                    List<StockPrediction> generatedPredictions = neuralNetworkService.predictFuturePrices(symbol, userId);
                    prediction = generatedPredictions.get(0);
                }

                data.predictedPrice = prediction.getPredictedPrice();
                data.predictedReturn = prediction.getPredictedChangePercentage() / 100.0;
                data.confidenceScore = prediction.getConfidenceScore() / 100.0;

                // Calculate momentum score (recent performance)
                if (historicalData.size() >= 20) {
                    double recentPrice = historicalData.get(historicalData.size() - 20).getClose();
                    data.momentumScore = (data.currentPrice - recentPrice) / recentPrice;
                } else {
                    data.momentumScore = avgReturn * 20; // Approximate
                }

                // Calculate Sharpe ratio
                double riskFreeRate = 0.02 / 365; // Daily risk-free rate
                if (data.volatility > 0) {
                    data.sharpeRatio = (avgReturn - riskFreeRate) / data.volatility;
                } else {
                    data.sharpeRatio = 0;
                }

                // Technical analysis score (simplified)
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
            // Get recent technical indicators
            List<TechnicalIndicator> indicators = indicatorRepository
                    .findByUserIdAndSymbolAndDateBetweenOrderByDateAsc(
                            userId, symbol, date.minusDays(30), date);

            if (indicators.isEmpty()) {
                return 0.5; // Neutral score
            }

            TechnicalIndicator latest = indicators.get(indicators.size() - 1);
            double score = 0.5; // Start neutral

            // RSI analysis
            if (latest.getRsi14() != null) {
                if (latest.getRsi14() < 30) {
                    score += 0.2; // Oversold - bullish
                } else if (latest.getRsi14() > 70) {
                    score -= 0.2; // Overbought - bearish
                }
            }

            // Moving average analysis
            if (latest.getSma20() != null && latest.getSma50() != null) {
                if (latest.getPrice() > latest.getSma20() && latest.getSma20() > latest.getSma50()) {
                    score += 0.15; // Bullish trend
                } else if (latest.getPrice() < latest.getSma20() && latest.getSma20() < latest.getSma50()) {
                    score -= 0.15; // Bearish trend
                }
            }

            // MACD analysis
            if (latest.getMacdHistogram() != null) {
                if (latest.getMacdHistogram() > 0) {
                    score += 0.1; // Bullish momentum
                } else {
                    score -= 0.1; // Bearish momentum
                }
            }

            // Bollinger Bands analysis
            if (latest.getBollingerUpper() != null && latest.getBollingerLower() != null) {
                double bbRange = latest.getBollingerUpper() - latest.getBollingerLower();
                double bbPosition = (latest.getPrice() - latest.getBollingerLower()) / bbRange;

                if (bbPosition < 0.2) {
                    score += 0.15; // Near lower band - oversold
                } else if (bbPosition > 0.8) {
                    score -= 0.15; // Near upper band - overbought
                }
            }

            return Math.max(0, Math.min(1, score)); // Clamp to [0, 1]
        } catch (Exception e) {
            return 0.5; // Default neutral score on error
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

        // Score each stock based on multiple factors
        Map<String, Double> stockScores = new HashMap<>();

        for (Map.Entry<String, AnalysisData> entry : analysisData.entrySet()) {
            String symbol = entry.getKey();
            AnalysisData data = entry.getValue();

            // Multi-factor scoring model
            double score = 0;

            // 1. Expected return component (weighted by confidence)
            double returnScore = data.predictedReturn * data.confidenceScore;
            score += returnScore * 0.3;

            // 2. Risk-adjusted return (Sharpe ratio)
            score += Math.tanh(data.sharpeRatio * 0.5) * 0.2; // tanh to limit extreme values

            // 3. Momentum factor
            score += Math.tanh(data.momentumScore * 2) * 0.15;

            // 4. Technical analysis score
            score += (data.technicalScore - 0.5) * 0.15; // Center around 0

            // 5. Volatility penalty (adjusted by risk tolerance)
            double volatilityPenalty = data.volatility * (1 - riskTolerance) * 0.2;
            score -= volatilityPenalty;

            // 6. Confidence bonus
            score += (data.confidenceScore - 0.5) * 0.1;

            // Give bonus to existing stocks (lower transaction costs)
            if (currentSymbols.contains(symbol)) {
                score *= 1.1;
            }

            // Ensure non-negative scores
            stockScores.put(symbol, Math.max(0, score));
        }

        // Sort stocks by score
        List<Map.Entry<String, Double>> sortedStocks = stockScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toList());

        // Allocate based on scores with risk tolerance consideration
        double totalScore = stockScores.values().stream().mapToDouble(Double::doubleValue).sum();

        if (totalScore > 0) {
            // Number of stocks to include based on risk tolerance
            int targetStocks = (int) (5 + (1 - riskTolerance) * 10); // 5-15 stocks
            targetStocks = Math.min(targetStocks, sortedStocks.size());

            // Allocate to top stocks
            double allocatedWeight = 0;
            for (int i = 0; i < targetStocks; i++) {
                Map.Entry<String, Double> entry = sortedStocks.get(i);
                String symbol = entry.getKey();
                double score = entry.getValue();

                // Calculate allocation
                double baseAllocation = score / totalScore;

                // Apply concentration limits
                double allocation = Math.min(baseAllocation * 1.5, config.getMaxStockAllocation());

                if (allocation >= config.getMinStockAllocation()) {
                    allocations.put(symbol, allocation);
                    allocatedWeight += allocation;
                }
            }

            // Normalize to sum to 1
            if (allocatedWeight > 0 && Math.abs(allocatedWeight - 1.0) > 0.01) {
                for (String symbol : allocations.keySet()) {
                    allocations.put(symbol, allocations.get(symbol) / allocatedWeight);
                }
            }
        }

        // Ensure we have at least some allocation
        if (allocations.isEmpty() && !analysisData.isEmpty()) {
            // Fall back to equal weight on current holdings
            double equalWeight = 1.0 / currentSymbols.size();
            for (String symbol : currentSymbols) {
                if (analysisData.containsKey(symbol)) {
                    allocations.put(symbol, equalWeight);
                }
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
                double currentAllocation = totalValue > 0 ? currentValue / totalValue : 0;

                double targetValue = totalValue * targetAllocation;
                action.targetShares = (int) Math.floor(targetValue / currentStock.getCurrentPrice());
                action.currentPrice = currentStock.getCurrentPrice();

                if (action.targetShares > action.currentShares * 1.1) { // 10% threshold
                    action.action = "BUY";
                    AnalysisData data = analysisData.get(symbol);
                    action.reason = String.format("Increase allocation from %.1f%% to %.1f%%. Expected return: %.1f%%, Technical score: %.2f",
                            currentAllocation * 100, targetAllocation * 100,
                            data != null ? data.predictedReturn * 100 : 0,
                            data != null ? data.technicalScore : 0);
                } else if (action.targetShares < action.currentShares * 0.9) { // 10% threshold
                    action.action = "SELL";
                    action.reason = String.format("Decrease allocation from %.1f%% to %.1f%%. Rebalancing for better opportunities",
                            currentAllocation * 100, targetAllocation * 100);
                } else {
                    action.action = "HOLD";
                    action.reason = "Current allocation is close to optimal";
                }
            } else {
                action.currentShares = 0;
                action.action = "BUY";

                AnalysisData data = analysisData.get(symbol);
                if (data != null) {
                    action.currentPrice = data.currentPrice;
                    action.targetShares = (int) Math.floor((totalValue * targetAllocation) / data.currentPrice);
                    action.reason = String.format("New position with %.1f%% allocation. Expected return: %.1f%%, Confidence: %.1f%%, Technical score: %.2f",
                            targetAllocation * 100, data.predictedReturn * 100,
                            data.confidenceScore * 100, data.technicalScore);
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

                AnalysisData data = analysisData.get(stock.getSymbol());
                if (data != null && data.predictedReturn < -0.05) {
                    action.reason = String.format("Remove from portfolio - negative expected return: %.1f%%",
                            data.predictedReturn * 100);
                } else if (data != null && data.technicalScore < 0.3) {
                    action.reason = "Remove from portfolio - poor technical indicators";
                } else {
                    action.reason = "Remove from portfolio - better opportunities available elsewhere";
                }
                actions.add(action);
            }
        }

        // Sort actions by importance (BUY/SELL before HOLD)
        actions.sort((a, b) -> {
            int actionPriority = getActionPriority(a.action) - getActionPriority(b.action);
            if (actionPriority != 0) return actionPriority;
            return Double.compare(b.targetShares, a.targetShares); // Larger positions first
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

        // Calculate weighted average metrics
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

        // Annualize the returns
        double annualReturn = expectedReturn * 252; // Trading days in a year
        double annualVolatility = expectedVolatility * Math.sqrt(252);

        // Calculate portfolio Sharpe ratio
        double riskFreeRate = 0.02; // 2% annual
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

        // Calculate current portfolio metrics
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

        // Calculate optimized portfolio metrics
        Map<String, Object> optimizedPerformance = calculateExpectedPerformance(optimizedAllocations, analysisData);
        double optimizedReturn = (Double) optimizedPerformance.get("expectedAnnualReturn") / 100;
        double optimizedVolatility = (Double) optimizedPerformance.get("expectedAnnualVolatility") / 100;
        double optimizedSharpe = (Double) optimizedPerformance.get("sharpeRatio");

        // Calculate improvements
        double returnImprovement = (optimizedReturn - currentReturn * 252) * 100;
        double riskReduction = (currentVolatility * Math.sqrt(252) - optimizedVolatility) * 100;
        double sharpeImprovement = optimizedSharpe - currentSharpe;

        // Calculate overall improvement score (0-100)
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
        // Risk score based on diversification, volatility, and concentration
        int stockCount = portfolio.getStocks().size();
        double diversificationScore = Math.min(stockCount / 10.0, 1.0) * 30;

        // Concentration risk
        double maxWeight = portfolio.getStocks().stream()
                .mapToDouble(Portfolio.PortfolioStock::getWeight)
                .max()
                .orElse(0);
        double concentrationRisk = maxWeight > 25 ? (maxWeight - 25) * 2 : 0;

        // Base volatility score
        double volatilityScore = 50; // Default medium risk

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

        // Factor in technical analysis reliability
        double technicalReliability = analysisData.values().stream()
                .mapToDouble(data -> Math.abs(data.technicalScore - 0.5) * 2) // Distance from neutral
                .average()
                .orElse(0);

        return (avgConfidence * 0.5 + dataQuality * 0.3 + technicalReliability * 0.2) * 100;
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
        double momentumScore;
        double technicalScore;
        List<Double> historicalReturns = new ArrayList<>();
    }

    private static class StockAction {
        String symbol;
        String action;
        int currentShares;
        int targetShares;
        double currentPrice;
        String reason;

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("symbol", symbol);
            map.put("action", action);
            map.put("currentShares", currentShares);
            map.put("targetShares", targetShares);
            map.put("currentPrice", currentPrice);
            map.put("reason", reason);
            return map;
        }
    }
}