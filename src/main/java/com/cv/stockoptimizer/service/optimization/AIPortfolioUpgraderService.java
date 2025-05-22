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
 *
 *
 *
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
        Map<String, AnalysisData> stockAnalysisData = collectStockAnalysisData(analyzedSymbols);

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

        historyService.recordAiRecommendation(
                portfolio,
                optimizedAllocations,
                riskTolerance, // You'll need to add this parameter to the method
                "1.0"); // Version number

// Update portfolio AI recommendation fields
        portfolio.setHasAiRecommendations(true);
        portfolio.setLastAiRecommendationDate(LocalDateTime.now());

// Determine recommendation type
        if (riskTolerance < 0.33) {
            portfolio.setAiRecommendationType("RISK_OPTIMIZED");
        } else if (riskTolerance < 0.67) {
            portfolio.setAiRecommendationType("BALANCED");
        } else {
            portfolio.setAiRecommendationType("RETURN_OPTIMIZED");
        }

// Save updates
        portfolio = portfolioRepository.save(portfolio);

        // Save and return
        return portfolioRepository.save(portfolio);
    }

    /**
     * Update portfolio totals based on current stock values
     */
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

    /**
     * Class to hold analysis data for each stock
     */
    private static class AnalysisData {
        String symbol;
        double currentPrice;
        double predictedPrice;
        double predictedReturn;
        double volatility;
        double sharpeRatio;
        double confidenceScore;
        Map<String, Double> correlations = new HashMap<>();
        List<Double> historicalReturns = new ArrayList<>();
        List<TechnicalIndicator> technicalIndicators = new ArrayList<>();
        TrendAnalysis trendAnalysis;

        static class TrendAnalysis {
            String trend; // BULLISH, BEARISH, NEUTRAL
            double momentum;
            double supportLevel;
            double resistanceLevel;
            double rsiValue;
            double macdValue;
            boolean isBullishCrossover;
            boolean isBearishCrossover;
        }
    }

    /**
     * Class to represent a recommended stock action
     */
    private static class StockAction {
        String symbol;
        String action; // BUY, SELL, HOLD
        int currentShares;
        int targetShares;
        int shareDifference;
        double currentAllocation;
        double targetAllocation;
        double allocationDifference;
        String reason;
        double currentPrice;
        double estimatedImpact;
        double confidenceScore;
    }

    /**
     * Expand the stock universe to include related stocks
     */
    private Set<String> expandStockUniverse(Set<String> currentSymbols) {
        Set<String> expandedSymbols = new HashSet<>();

        // Get all available symbols in the database
        Set<String> allAvailableSymbols = stockDataRepository.findDistinctSymbols();

        // For simplicity, just add some major index components
        // In a real implementation, you would use sector analysis, correlation,
        // or ML-based recommendation algorithms to find related stocks
        List<String> potentialSymbols = Arrays.asList(
                "AAPL", "MSFT", "GOOGL", "AMZN", "META", "TSLA", "NVDA", "JPM",
                "JNJ", "V", "PG", "HD", "BAC", "MA", "DIS", "NFLX", "INTC", "VZ"
        );

        for (String symbol : potentialSymbols) {
            // Only add if not already in portfolio and we have data
            if (!currentSymbols.contains(symbol) && allAvailableSymbols.contains(symbol)) {
                expandedSymbols.add(symbol);

                // Limit to 10 additional stocks for performance reasons
                if (expandedSymbols.size() >= 10) {
                    break;
                }
            }
        }

        return expandedSymbols;
    }

    /**
     * Collect analysis data for each stock
     */
    private Map<String, AnalysisData> collectStockAnalysisData(Set<String> symbols) throws IOException {
        Map<String, AnalysisData> analysisDataMap = new HashMap<>();

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(HISTORICAL_DAYS);

        // Create analysis data for each symbol
        for (String symbol : symbols) {
            try {
                AnalysisData data = new AnalysisData();
                data.symbol = symbol;

                // Get historical price data
                List<StockData> historicalData = stockDataRepository
                        .findBySymbolAndDateBetweenOrderByDateAsc(symbol, startDate, today);

                if (historicalData.isEmpty()) {
                    continue; // Skip if no data
                }

                // Get current price
                StockData latestData = historicalData.get(historicalData.size() - 1);
                data.currentPrice = latestData.getClose();

                // Calculate historical returns
                for (int i = 1; i < historicalData.size(); i++) {
                    double previousClose = historicalData.get(i - 1).getClose();
                    double currentClose = historicalData.get(i).getClose();
                    double dailyReturn = (currentClose - previousClose) / previousClose;
                    data.historicalReturns.add(dailyReturn);
                }

                // Calculate volatility (standard deviation of returns)
                data.volatility = calculateVolatility(data.historicalReturns);

                // Get or generate price prediction
                List<StockPrediction> predictions = predictionRepository
                        .findBySymbolOrderByPredictionDateDesc(symbol);

                StockPrediction prediction;
                if (!predictions.isEmpty()) {
                    prediction = predictions.get(0);
                } else {
                    // If no prediction exists, generate one
                    List<StockPrediction> generatedPredictions = neuralNetworkService.predictFuturePrices(symbol);
                    prediction = generatedPredictions.get(0);
                }

                data.predictedPrice = prediction.getPredictedPrice();
                data.predictedReturn = prediction.getPredictedChangePercentage() / 100.0;
                data.confidenceScore = prediction.getConfidenceScore() / 100.0;

                // Calculate Sharpe ratio (using risk-free rate of 2%)
                double riskFreeRate = 0.02 / 365; // Daily risk-free rate
                double avgReturn = data.historicalReturns.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0);

                if (data.volatility > 0) {
                    data.sharpeRatio = (avgReturn - riskFreeRate) / data.volatility;
                } else {
                    data.sharpeRatio = 0;
                }

                // Get technical indicators
                data.technicalIndicators = indicatorRepository
                        .findBySymbolAndDateBetweenOrderByDateAsc(symbol, startDate, today);

                // Calculate trend analysis
                data.trendAnalysis = analyzeTrend(data.technicalIndicators);

                // Add to map
                analysisDataMap.put(symbol, data);
            } catch (Exception e) {
                System.err.println("Error collecting analysis data for " + symbol + ": " + e.getMessage());
            }
        }

        // Calculate correlations between stocks
        calculateStockCorrelations(analysisDataMap);

        return analysisDataMap;
    }

    /**
     * Calculate the volatility (standard deviation) of returns
     */
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

    /**
     * Analyze technical indicators to determine trend
     */
    private AnalysisData.TrendAnalysis analyzeTrend(List<TechnicalIndicator> indicators) {
        AnalysisData.TrendAnalysis analysis = new AnalysisData.TrendAnalysis();

        if (indicators.isEmpty()) {
            analysis.trend = "NEUTRAL";
            return analysis;
        }

        // Get the most recent indicators
        TechnicalIndicator latest = indicators.get(indicators.size() - 1);

        // Set default values
        analysis.trend = "NEUTRAL";
        analysis.momentum = 0;
        analysis.supportLevel = 0;
        analysis.resistanceLevel = 0;
        analysis.rsiValue = latest.getRsi14() != null ? latest.getRsi14() : 50;
        analysis.macdValue = latest.getMacdHistogram() != null ? latest.getMacdHistogram() : 0;

        // Determine trend based on moving averages
        if (latest.getSma20() != null && latest.getSma50() != null) {
            if (latest.getSma20() > latest.getSma50()) {
                analysis.trend = "BULLISH";
            } else if (latest.getSma20() < latest.getSma50()) {
                analysis.trend = "BEARISH";
            }
        }

        // Calculate momentum (rate of price change)
        if (indicators.size() > 10) {
            TechnicalIndicator tenDaysAgo = indicators.get(indicators.size() - 10);
            analysis.momentum = (latest.getPrice() - tenDaysAgo.getPrice()) / tenDaysAgo.getPrice();
        }

        // Identify support and resistance levels
        List<Double> supportLevels = new ArrayList<>();
        List<Double> resistanceLevels = new ArrayList<>();

        for (int i = 1; i < indicators.size() - 1; i++) {
            TechnicalIndicator prev = indicators.get(i - 1);
            TechnicalIndicator curr = indicators.get(i);
            TechnicalIndicator next = indicators.get(i + 1);

            // Support: local minimum
            if (curr.getPrice() < prev.getPrice() && curr.getPrice() < next.getPrice()) {
                supportLevels.add(curr.getPrice());
            }

            // Resistance: local maximum
            if (curr.getPrice() > prev.getPrice() && curr.getPrice() > next.getPrice()) {
                resistanceLevels.add(curr.getPrice());
            }
        }

        // Find closest support and resistance
        if (!supportLevels.isEmpty()) {
            supportLevels.sort(Comparator.reverseOrder());
            for (double support : supportLevels) {
                if (support < latest.getPrice()) {
                    analysis.supportLevel = support;
                    break;
                }
            }
        }

        if (!resistanceLevels.isEmpty()) {
            resistanceLevels.sort(Comparator.naturalOrder());
            for (double resistance : resistanceLevels) {
                if (resistance > latest.getPrice()) {
                    analysis.resistanceLevel = resistance;
                    break;
                }
            }
        }

        // Check for MACD crossovers
        if (indicators.size() > 2) {
            TechnicalIndicator previous = indicators.get(indicators.size() - 2);

            if (latest.getMacdHistogram() != null && previous.getMacdHistogram() != null) {
                analysis.isBullishCrossover = previous.getMacdHistogram() < 0 && latest.getMacdHistogram() > 0;
                analysis.isBearishCrossover = previous.getMacdHistogram() > 0 && latest.getMacdHistogram() < 0;
            }
        }

        return analysis;
    }

    /**
     * Calculate correlations between all stocks
     */
    private void calculateStockCorrelations(Map<String, AnalysisData> analysisDataMap) {
        // For each pair of stocks, calculate correlation of returns
        for (String symbol1 : analysisDataMap.keySet()) {
            AnalysisData data1 = analysisDataMap.get(symbol1);

            for (String symbol2 : analysisDataMap.keySet()) {
                if (symbol1.equals(symbol2)) {
                    data1.correlations.put(symbol2, 1.0); // Self-correlation is 1
                } else {
                    AnalysisData data2 = analysisDataMap.get(symbol2);

                    // Calculate correlation between return series
                    double correlation = calculateCorrelation(data1.historicalReturns, data2.historicalReturns);
                    data1.correlations.put(symbol2, correlation);
                }
            }
        }
    }

    /**
     * Calculate correlation between two series
     */
    private double calculateCorrelation(List<Double> series1, List<Double> series2) {
        // Ensure series are the same length
        int minLength = Math.min(series1.size(), series2.size());
        if (minLength < 10) {
            return 0; // Not enough data
        }

        List<Double> trimmed1 = series1.subList(series1.size() - minLength, series1.size());
        List<Double> trimmed2 = series2.subList(series2.size() - minLength, series2.size());

        double mean1 = trimmed1.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double mean2 = trimmed2.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        double sum = 0;
        double sumSq1 = 0;
        double sumSq2 = 0;

        for (int i = 0; i < minLength; i++) {
            double diff1 = trimmed1.get(i) - mean1;
            double diff2 = trimmed2.get(i) - mean2;

            sum += diff1 * diff2;
            sumSq1 += diff1 * diff1;
            sumSq2 += diff2 * diff2;
        }

        if (sumSq1 == 0 || sumSq2 == 0) {
            return 0;
        }

        return sum / (Math.sqrt(sumSq1) * Math.sqrt(sumSq2));
    }

    /**
     * Calculate optimal allocations using ML insights
     */
    private Map<String, Double> calculateOptimalAllocations(
            Map<String, AnalysisData> analysisData,
            Set<String> currentSymbols,
            double riskTolerance) {

        // Initialize allocations map
        Map<String, Double> allocations = new HashMap<>();

        // For AI portfolio optimization, we'll use:
        // 1. Black-Litterman model with neural network predictions
        // 2. Risk-adjusted for user's risk tolerance
        // 3. Constrained to ensure diversification

        // Step 1: Calculate expected returns (blend of historical and predicted)
        Map<String, Double> expectedReturns = new HashMap<>();
        for (Map.Entry<String, AnalysisData> entry : analysisData.entrySet()) {
            String symbol = entry.getKey();
            AnalysisData data = entry.getValue();

            // Blend historical and predicted returns with confidence weighting
            double historicalAvgReturn = data.historicalReturns.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0) * 252; // Annualized

            double predictedReturn = data.predictedReturn * (365.0 / PREDICTION_HORIZON); // Annualized
            double blendedReturn = (historicalAvgReturn * 0.3) + (predictedReturn * 0.7 * data.confidenceScore);

            // Adjust based on technical trend signals
            if (data.trendAnalysis.trend.equals("BULLISH")) {
                blendedReturn *= 1.2; // 20% boost for bullish trend
            } else if (data.trendAnalysis.trend.equals("BEARISH")) {
                blendedReturn *= 0.8; // 20% reduction for bearish trend
            }

            expectedReturns.put(symbol, blendedReturn);
        }

        // Step 2: Build covariance matrix from correlations and volatilities
        Map<String, Map<String, Double>> covarianceMatrix = new HashMap<>();
        for (String symbol1 : analysisData.keySet()) {
            AnalysisData data1 = analysisData.get(symbol1);
            Map<String, Double> covRow = new HashMap<>();

            for (String symbol2 : analysisData.keySet()) {
                AnalysisData data2 = analysisData.get(symbol2);
                double correlation = data1.correlations.getOrDefault(symbol2, 0.0);
                double covariance = correlation * data1.volatility * data2.volatility;
                covRow.put(symbol2, covariance);
            }

            covarianceMatrix.put(symbol1, covRow);
        }

        // Step 3: Optimize portfolio weights using Black-Litterman approach
        // For simplicity, we'll use a modified gradient descent method

        // Initialize weights equally
        for (String symbol : analysisData.keySet()) {
            allocations.put(symbol, 1.0 / analysisData.size());
        }

        // Optimize weights using gradient descent
        double learningRate = 0.01;
        int iterations = 1000;

        for (int i = 0; i < iterations; i++) {
            // Calculate portfolio return and risk
            double portfolioReturn = calculatePortfolioReturn(allocations, expectedReturns);
            double portfolioRisk = calculatePortfolioRisk(allocations, covarianceMatrix);

            // Define utility function based on risk tolerance
            // Higher risk tolerance = more weight on returns
            double utility = (1 - riskTolerance) * portfolioReturn - riskTolerance * portfolioRisk;

            // Calculate gradients and update weights
            Map<String, Double> gradients = new HashMap<>();
            for (String symbol : allocations.keySet()) {
                double perturbation = 0.001;
                double originalWeight = allocations.get(symbol);

                // Perturb weight up
                allocations.put(symbol, originalWeight + perturbation);
                double utilityUp = (1 - riskTolerance) * calculatePortfolioReturn(allocations, expectedReturns)
                        - riskTolerance * calculatePortfolioRisk(allocations, covarianceMatrix);

                // Perturb weight down
                allocations.put(symbol, originalWeight - perturbation);
                double utilityDown = (1 - riskTolerance) * calculatePortfolioReturn(allocations, expectedReturns)
                        - riskTolerance * calculatePortfolioRisk(allocations, covarianceMatrix);

                // Reset weight
                allocations.put(symbol, originalWeight);

                // Calculate gradient
                double gradient = (utilityUp - utilityDown) / (2 * perturbation);
                gradients.put(symbol, gradient);
            }

            // Update weights based on gradients
            for (String symbol : allocations.keySet()) {
                double newWeight = allocations.get(symbol) + learningRate * gradients.get(symbol);
                allocations.put(symbol, newWeight);
            }

            // Apply constraints
            normalizeAndConstrainWeights(allocations, currentSymbols);
        }

        // Adjust for existing positions (give slight preference to current holdings)
        for (String symbol : currentSymbols) {
            if (allocations.containsKey(symbol)) {
                double weight = allocations.get(symbol);
                allocations.put(symbol, weight * 1.05); // 5% boost for existing positions
            }
        }

        // Final normalization with constraints
        normalizeAndConstrainWeights(allocations, currentSymbols);

        return allocations;
    }

    /**
     * Calculate expected portfolio return based on weights and expected returns
     */
    private double calculatePortfolioReturn(Map<String, Double> weights, Map<String, Double> expectedReturns) {
        double portfolioReturn = 0;

        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            String symbol = entry.getKey();
            double weight = entry.getValue();

            if (expectedReturns.containsKey(symbol)) {
                portfolioReturn += weight * expectedReturns.get(symbol);
            }
        }

        return portfolioReturn;
    }

    /**
     * Calculate portfolio risk based on weights and covariance matrix
     */
    private double calculatePortfolioRisk(Map<String, Double> weights, Map<String, Map<String, Double>> covarianceMatrix) {
        double risk = 0;

        for (String symbol1 : weights.keySet()) {
            double weight1 = weights.get(symbol1);

            if (!covarianceMatrix.containsKey(symbol1)) {
                continue;
            }

            for (String symbol2 : weights.keySet()) {
                double weight2 = weights.get(symbol2);

                if (!covarianceMatrix.get(symbol1).containsKey(symbol2)) {
                    continue;
                }

                double covariance = covarianceMatrix.get(symbol1).get(symbol2);
                risk += weight1 * weight2 * covariance;
            }
        }

        return Math.sqrt(risk);
    }

    /**
     * Normalize weights and apply constraints
     */
    private void normalizeAndConstrainWeights(Map<String, Double> weights, Set<String> currentSymbols) {
        // Ensure all weights are non-negative
        for (String symbol : new HashSet<>(weights.keySet())) {
            double weight = weights.get(symbol);

            if (weight < 0) {
                weights.put(symbol, 0.0);
            }

            // Remove symbols with very small allocations, unless they're current holdings
            if (weight < MIN_SINGLE_STOCK_ALLOCATION && !currentSymbols.contains(symbol)) {
                weights.remove(symbol);
            }
        }

        // Cap maximum allocation per stock
        for (String symbol : weights.keySet()) {
            double weight = weights.get(symbol);
            if (weight > MAX_SINGLE_STOCK_ALLOCATION) {
                weights.put(symbol, MAX_SINGLE_STOCK_ALLOCATION);
            }
        }

        // Normalize to sum to 1
        double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();

        if (sum > 0) {
            for (String symbol : weights.keySet()) {
                weights.put(symbol, weights.get(symbol) / sum);
            }
        } else {
            // If all weights are zero, default to equal weights
            double equalWeight = 1.0 / weights.size();
            for (String symbol : weights.keySet()) {
                weights.put(symbol, equalWeight);
            }
        }
    }

    /**
     * Generate specific action recommendations for each stock
     */
    private List<StockAction> generateStockActions(
            Portfolio portfolio,
            Map<String, Double> optimizedAllocations,
            Map<String, AnalysisData> analysisData) {

        List<StockAction> actions = new ArrayList<>();

        // Calculate current total value
        double totalValue = portfolio.getTotalValue() != null ? portfolio.getTotalValue() : 0;
        if (totalValue <= 0) {
            // Can't calculate specific actions without knowing portfolio value
            return actions;
        }

        // Map current portfolio for quick lookup
        Map<String, Portfolio.PortfolioStock> currentStocks = portfolio.getStocks().stream()
                .collect(Collectors.toMap(Portfolio.PortfolioStock::getSymbol, s -> s));

        // Process current holdings first
        for (Portfolio.PortfolioStock currentStock : portfolio.getStocks()) {
            String symbol = currentStock.getSymbol();
            double currentPrice = currentStock.getCurrentPrice();
            int currentShares = currentStock.getShares();
            double currentValue = currentPrice * currentShares;
            double currentAllocation = currentValue / totalValue;

            // Get target allocation, if any
            double targetAllocation = optimizedAllocations.getOrDefault(symbol, 0.0);
            double targetValue = totalValue * targetAllocation;
            int targetShares = (int) Math.floor(targetValue / currentPrice);

            // Create action
            StockAction action = new StockAction();
            action.symbol = symbol;
            action.currentShares = currentShares;
            action.targetShares = targetShares;
            action.shareDifference = targetShares - currentShares;
            action.currentAllocation = currentAllocation * 100; // Convert to percentage
            action.targetAllocation = targetAllocation * 100;   // Convert to percentage
            action.allocationDifference = action.targetAllocation - action.currentAllocation;
            action.currentPrice = currentPrice;
            action.estimatedImpact = action.shareDifference * currentPrice;

            // Determine action type and reason
            if (Math.abs(action.shareDifference) < 5 || Math.abs(action.allocationDifference) < 1) {
                action.action = "HOLD";
                action.reason = "Current position is close to optimal allocation";
            } else if (action.shareDifference > 0) {
                action.action = "BUY";

                // Determine reason based on analysis data
                if (analysisData.containsKey(symbol)) {
                    AnalysisData data = analysisData.get(symbol);

                    if (data.trendAnalysis.trend.equals("BULLISH")) {
                        action.reason = "Bullish trend with positive momentum";
                    } else if (data.predictedReturn > 0.1) {
                        action.reason = "High expected return in the near term";
                    } else if (data.sharpeRatio > 1) {
                        action.reason = "Strong risk-adjusted return profile";
                    } else {
                        action.reason = "Portfolio diversification benefits";
                    }

                    action.confidenceScore = data.confidenceScore * 100;
                } else {
                    action.reason = "Increase allocation for portfolio optimization";
                    action.confidenceScore = 60;
                }
            } else {
                action.action = "SELL";

                // Determine reason based on analysis data
                if (analysisData.containsKey(symbol)) {
                    AnalysisData data = analysisData.get(symbol);

                    if (data.trendAnalysis.trend.equals("BEARISH")) {
                        action.reason = "Bearish trend with negative momentum";
                    } else if (data.predictedReturn < -0.05) {
                        action.reason = "Negative expected return in the near term";
                    } else if (data.sharpeRatio < 0) {
                        action.reason = "Poor risk-adjusted return profile";
                    } else {
                        action.reason = "Better opportunities elsewhere in portfolio";
                    }

                    action.confidenceScore = data.confidenceScore * 100;
                } else {
                    action.reason = "Decrease allocation for portfolio optimization";
                    action.confidenceScore = 60;
                }
            }

            actions.add(action);
        }

        // Process new stocks (in optimized allocations but not in current portfolio)
        for (String symbol : optimizedAllocations.keySet()) {
            if (!currentStocks.containsKey(symbol)) {
                double targetAllocation = optimizedAllocations.get(symbol);

                // Skip very small allocations
                if (targetAllocation < MIN_SINGLE_STOCK_ALLOCATION) {
                    continue;
                }

                // Get current price
                double currentPrice = 0;
                if (analysisData.containsKey(symbol)) {
                    currentPrice = analysisData.get(symbol).currentPrice;
                } else {
                    continue; // Skip if no price data
                }

                double targetValue = totalValue * targetAllocation;
                int targetShares = (int) Math.floor(targetValue / currentPrice);

                // Skip if less than 1 share
                if (targetShares < 1) {
                    continue;
                }

                // Create action
                StockAction action = new StockAction();
                action.symbol = symbol;
                action.action = "BUY";
                action.currentShares = 0;
                action.targetShares = targetShares;
                action.shareDifference = targetShares;
                action.currentAllocation = 0;
                action.targetAllocation = targetAllocation * 100; // Convert to percentage
                action.allocationDifference = action.targetAllocation;
                action.currentPrice = currentPrice;
                action.estimatedImpact = targetShares * currentPrice;

                // Determine reason based on analysis data
                if (analysisData.containsKey(symbol)) {
                    AnalysisData data = analysisData.get(symbol);

                    if (data.trendAnalysis.trend.equals("BULLISH")) {
                        action.reason = "New position: Bullish trend with positive outlook";
                    } else if (data.predictedReturn > 0.1) {
                        action.reason = "New position: High expected return potential";
                    } else if (data.sharpeRatio > 1) {
                        action.reason = "New position: Strong risk-adjusted return profile";
                    } else {
                        action.reason = "New position: Improves portfolio diversification";
                    }

                    action.confidenceScore = data.confidenceScore * 100;
                } else {
                    action.reason = "New position: Enhances portfolio balance";
                    action.confidenceScore = 60;
                }

                actions.add(action);
            }
        }

        // Sort actions by impact (descending)
        actions.sort(Comparator.comparing((StockAction a) -> Math.abs(a.estimatedImpact)).reversed());

        return actions;
    }

    /**
     * Calculate expected performance metrics for the optimized portfolio
     */
    private Map<String, Object> calculateExpectedPerformance(
            Map<String, Double> allocations,
            Map<String, AnalysisData> analysisData) {

        Map<String, Object> performance = new HashMap<>();

        // Calculate expected return (annualized)
        double expectedReturn = 0;
        for (String symbol : allocations.keySet()) {
            double weight = allocations.get(symbol);

            if (analysisData.containsKey(symbol)) {
                AnalysisData data = analysisData.get(symbol);
                double annualizedReturn = data.predictedReturn * (365.0 / PREDICTION_HORIZON);
                expectedReturn += weight * annualizedReturn;
            }
        }

        // Calculate expected risk (annualized volatility)
        Map<String, Map<String, Double>> covarianceMatrix = new HashMap<>();
        for (String symbol1 : analysisData.keySet()) {
            AnalysisData data1 = analysisData.get(symbol1);
            Map<String, Double> covRow = new HashMap<>();

            for (String symbol2 : analysisData.keySet()) {
                AnalysisData data2 = analysisData.get(symbol2);
                double correlation = data1.correlations.getOrDefault(symbol2, 0.0);
                double covariance = correlation * data1.volatility * data2.volatility;
                covRow.put(symbol2, covariance);
            }

            covarianceMatrix.put(symbol1, covRow);
        }

        double expectedRisk = calculatePortfolioRisk(allocations, covarianceMatrix) * Math.sqrt(252); // Annualized

        // Calculate Sharpe ratio (assuming 2% risk-free rate)
        double riskFreeRate = 0.02;
        double sharpeRatio = (expectedReturn - riskFreeRate) / expectedRisk;

        // Calculate expected max drawdown (estimate based on volatility)
        double expectedMaxDrawdown = expectedRisk * 2.5; // Approximation

        // Calculate diversification score
        double diversificationScore = calculateDiversificationScore(allocations, analysisData);

        // Add metrics to result
        performance.put("expectedAnnualReturn", expectedReturn * 100); // As percentage
        performance.put("expectedAnnualVolatility", expectedRisk * 100); // As percentage
        performance.put("sharpeRatio", sharpeRatio);
        performance.put("expectedMaxDrawdown", expectedMaxDrawdown * 100); // As percentage
        performance.put("diversificationScore", diversificationScore);
        performance.put("riskReturnRatio", expectedReturn / expectedRisk);
        performance.put("numberOfPositions", allocations.size());
        performance.put("averagePositionSize", allocations.isEmpty() ? 0 : 100.0 / allocations.size());

        return performance;
    }

    /**
     * Calculate improvement metrics versus current portfolio
     */
    private Map<String, Object> calculateImprovementMetrics(
            Portfolio portfolio,
            Map<String, Double> optimizedAllocations,
            Map<String, AnalysisData> analysisData) {

        Map<String, Object> metrics = new HashMap<>();

        // Calculate current portfolio metrics
        Map<String, Double> currentAllocations = new HashMap<>();
        double totalValue = portfolio.getTotalValue() != null ? portfolio.getTotalValue() : 0;

        if (totalValue > 0) {
            for (Portfolio.PortfolioStock stock : portfolio.getStocks()) {
                double stockValue = stock.getCurrentPrice() * stock.getShares();
                currentAllocations.put(stock.getSymbol(), stockValue / totalValue);
            }
        } else {
            // Equal weights if no value available
            for (Portfolio.PortfolioStock stock : portfolio.getStocks()) {
                currentAllocations.put(stock.getSymbol(), 1.0 / portfolio.getStocks().size());
            }
        }

        // Calculate expected returns and risks for both portfolios
        Map<String, Object> currentPerformance = calculateExpectedPerformance(currentAllocations, analysisData);
        Map<String, Object> optimizedPerformance = calculateExpectedPerformance(optimizedAllocations, analysisData);

        // Calculate improvements
        double returnImprovement = (double) optimizedPerformance.get("expectedAnnualReturn") -
                (double) currentPerformance.get("expectedAnnualReturn");

        double riskReduction = (double) currentPerformance.get("expectedAnnualVolatility") -
                (double) optimizedPerformance.get("expectedAnnualVolatility");

        double sharpeImprovement = (double) optimizedPerformance.get("sharpeRatio") -
                (double) currentPerformance.get("sharpeRatio");

        double diversificationImprovement = (double) optimizedPerformance.get("diversificationScore") -
                (double) currentPerformance.get("diversificationScore");

        // Calculate turnover (how much of the portfolio would change)
        double turnoverPercentage = calculateTurnoverPercentage(currentAllocations, optimizedAllocations);

        // Calculate risk-adjusted return improvement
        double currentRiskReturn = (double) currentPerformance.get("riskReturnRatio");
        double optimizedRiskReturn = (double) optimizedPerformance.get("riskReturnRatio");
        double riskReturnImprovement = optimizedRiskReturn - currentRiskReturn;

        // Add metrics to result
        metrics.put("returnImprovement", returnImprovement);
        metrics.put("riskReduction", riskReduction);
        metrics.put("sharpeRatioImprovement", sharpeImprovement);
        metrics.put("diversificationImprovement", diversificationImprovement);
        metrics.put("turnoverPercentage", turnoverPercentage);
        metrics.put("riskReturnImprovement", riskReturnImprovement);

        // Calculate overall improvement score (weighted average of improvements)
        double overallImprovementScore = (returnImprovement * 0.4) +
                (riskReduction * 0.2) +
                (sharpeImprovement * 0.3) +
                (diversificationImprovement * 0.1);

        // Normalize to 0-100 scale
        overallImprovementScore = Math.min(100, Math.max(0, overallImprovementScore * 20));
        metrics.put("overallImprovementScore", overallImprovementScore);

        return metrics;
    }

    /**
     * Calculate turnover percentage between current and optimized allocations
     */
    private double calculateTurnoverPercentage(
            Map<String, Double> currentAllocations,
            Map<String, Double> optimizedAllocations) {

        double totalTurnover = 0;

        // Calculate for existing positions
        for (String symbol : currentAllocations.keySet()) {
            double currentWeight = currentAllocations.get(symbol);
            double optimizedWeight = optimizedAllocations.getOrDefault(symbol, 0.0);

            totalTurnover += Math.abs(optimizedWeight - currentWeight);
        }

        // Add new positions
        for (String symbol : optimizedAllocations.keySet()) {
            if (!currentAllocations.containsKey(symbol)) {
                totalTurnover += optimizedAllocations.get(symbol);
            }
        }

        // Turnover is the sum of absolute changes divided by 2
        // (since each sale is matched with a purchase)
        return totalTurnover * 50; // Convert to percentage
    }

    /**
     * Calculate diversification score for a portfolio
     */
    private double calculateDiversificationScore(
            Map<String, Double> allocations,
            Map<String, AnalysisData> analysisData) {

        if (allocations.isEmpty()) {
            return 0;
        }

        // Calculate Herfindahl-Hirschman Index (HHI) for concentration
        double hhi = allocations.values().stream()
                .mapToDouble(w -> w * w)
                .sum();

        // Calculate Effective N (effective number of stocks)
        double effectiveN = 1 / hhi;

        // Calculate average correlation between stocks
        double totalCorrelation = 0;
        int correlationCount = 0;

        for (String symbol1 : allocations.keySet()) {
            if (!analysisData.containsKey(symbol1)) {
                continue;
            }

            AnalysisData data1 = analysisData.get(symbol1);

            for (String symbol2 : allocations.keySet()) {
                if (!analysisData.containsKey(symbol2) || symbol1.equals(symbol2)) {
                    continue;
                }

                totalCorrelation += Math.abs(data1.correlations.getOrDefault(symbol2, 0.0));
                correlationCount++;
            }
        }

        double avgCorrelation = correlationCount > 0 ? totalCorrelation / correlationCount : 0.5;

        // Calculate diversification score (0-100)
        // Higher score = better diversification
        double weightDiversification = Math.min(1, effectiveN / 10) * 50; // Max 50 points
        double correlationDiversification = (1 - avgCorrelation) * 50;    // Max 50 points

        return weightDiversification + correlationDiversification;
    }

    /**
     * Get latest stock data for a set of symbols
     */
    private Map<String, StockData> getLatestStockData(Set<String> symbols) {
        Map<String, StockData> result = new HashMap<>();
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(5); // Look back 5 days to handle weekends/holidays

        for (String symbol : symbols) {
            List<StockData> recentData = stockDataRepository
                    .findBySymbolAndDateBetweenOrderByDateAsc(symbol, startDate, today);

            if (!recentData.isEmpty()) {
                result.put(symbol, recentData.get(recentData.size() - 1));
            }
        }

        return result;
    }

    /**
     * Calculate risk score for a portfolio (0-100 scale)
     */
    private double calculatePortfolioRiskScore(Portfolio portfolio) {
        if (portfolio.getStocks() == null || portfolio.getStocks().isEmpty()) {
            return 50; // Default midpoint
        }

        // Simple risk score based on volatility
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(365);

        Map<String, List<Double>> returns = new HashMap<>();

        // Get historical returns
        for (Portfolio.PortfolioStock stock : portfolio.getStocks()) {
            String symbol = stock.getSymbol();

            List<StockData> historicalData = stockDataRepository
                    .findBySymbolAndDateBetweenOrderByDateAsc(symbol, startDate, today);

            if (historicalData.size() < 30) {
                continue;
            }

            List<Double> stockReturns = new ArrayList<>();
            for (int i = 1; i < historicalData.size(); i++) {
                double previousClose = historicalData.get(i - 1).getClose();
                double currentClose = historicalData.get(i).getClose();
                double dailyReturn = (currentClose - previousClose) / previousClose;
                stockReturns.add(dailyReturn);
            }

            returns.put(symbol, stockReturns);
        }

        if (returns.isEmpty()) {
            return 50; // Default if no historical data
        }

        // Calculate portfolio volatility
        Map<String, Double> weights = new HashMap<>();
        double totalValue = portfolio.getTotalValue() != null ? portfolio.getTotalValue() : 0;

        if (totalValue <= 0) {
            // Equal weights if no value
            for (Portfolio.PortfolioStock stock : portfolio.getStocks()) {
                weights.put(stock.getSymbol(), 1.0 / portfolio.getStocks().size());
            }
        } else {
            for (Portfolio.PortfolioStock stock : portfolio.getStocks()) {
                double stockValue = stock.getCurrentPrice() * stock.getShares();
                weights.put(stock.getSymbol(), stockValue / totalValue);
            }
        }

        // Calculate weighted average volatility
        double weightedVolatility = 0;
        for (String symbol : weights.keySet()) {
            if (!returns.containsKey(symbol)) {
                continue;
            }

            double weight = weights.get(symbol);
            List<Double> stockReturns = returns.get(symbol);
            double volatility = calculateVolatility(stockReturns);

            weightedVolatility += weight * volatility;
        }

        // Convert to 0-100 scale (assuming max volatility of 0.03 daily)
        double maxVolatility = 0.03;
        double riskScore = (weightedVolatility / maxVolatility) * 100;

        // Cap to 0-100 range
        return Math.min(100, Math.max(0, riskScore));
    }

    /**
     * Get company name for a stock symbol
     */
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
        commonStocks.put("V", "Visa Inc.");
        commonStocks.put("JNJ", "Johnson & Johnson");
        commonStocks.put("WMT", "Walmart Inc.");
        commonStocks.put("PG", "Procter & Gamble Company");
        commonStocks.put("MA", "Mastercard Incorporated");
        commonStocks.put("HD", "The Home Depot, Inc.");
        commonStocks.put("BAC", "Bank of America Corporation");
        commonStocks.put("DIS", "The Walt Disney Company");
        commonStocks.put("NFLX", "Netflix, Inc.");
        commonStocks.put("INTC", "Intel Corporation");
        commonStocks.put("VZ", "Verizon Communications Inc.");

        return commonStocks.getOrDefault(symbol, symbol + " Corp");
    }

    /**
     * Calculate AI confidence score for the upgrade recommendations
     */
    private double calculateAIConfidenceScore(Map<String, AnalysisData> analysisData) {
        if (analysisData.isEmpty()) {
            return 60; // Default moderate confidence
        }

        // Average of prediction confidence scores
        double avgConfidence = analysisData.values().stream()
                .mapToDouble(data -> data.confidenceScore * 100)
                .average()
                .orElse(60);

        // Adjust based on data quality
        int dataPointsAverage = (int) analysisData.values().stream()
                .mapToDouble(data -> data.historicalReturns.size())
                .average()
                .orElse(0);

        // More data points = higher confidence
        double dataQualityFactor = Math.min(1.0, dataPointsAverage / 200.0);

        double adjustedConfidence = avgConfidence * (0.7 + (0.3 * dataQualityFactor));

        // Ensure result is between 0-100
        return Math.min(100, Math.max(0, adjustedConfidence));
    }
}