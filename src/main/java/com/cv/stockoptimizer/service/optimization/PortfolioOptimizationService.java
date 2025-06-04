package com.cv.stockoptimizer.service.optimization;

import com.cv.stockoptimizer.model.entity.Portfolio;
import com.cv.stockoptimizer.model.entity.StockData;
import com.cv.stockoptimizer.model.entity.StockPrediction;
import com.cv.stockoptimizer.repository.PortfolioRepository;
import com.cv.stockoptimizer.repository.StockDataRepository;
import com.cv.stockoptimizer.repository.StockPredictionRepository;
import com.cv.stockoptimizer.service.ml.NeuralNetworkService;
import org.encog.ml.data.MLData;
import org.encog.ml.data.basic.BasicMLData;
import org.encog.neural.networks.BasicNetwork;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for optimizing stock portfolios using neural networks and modern portfolio theory
 */
@Service
public class PortfolioOptimizationService {

    private final PortfolioRepository portfolioRepository;
    private final StockDataRepository stockDataRepository;
    private final StockPredictionRepository predictionRepository;
    private final NeuralNetworkService neuralNetworkService;

    @Autowired
    public PortfolioOptimizationService(
            PortfolioRepository portfolioRepository,
            StockDataRepository stockDataRepository,
            StockPredictionRepository predictionRepository,
            NeuralNetworkService neuralNetworkService) {
        this.portfolioRepository = portfolioRepository;
        this.stockDataRepository = stockDataRepository;
        this.predictionRepository = predictionRepository;
        this.neuralNetworkService = neuralNetworkService;
    }

    /**
     * Get portfolio details with current market values
     */
    public Portfolio getPortfolioWithCurrentValues(String portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));

        // Update current prices and calculate values
        updatePortfolioCurrentValues(portfolio);

        return portfolio;
    }

    /**
     * Update portfolio with current stock prices and calculated values
     */
    private void updatePortfolioCurrentValues(Portfolio portfolio) {
        if (portfolio.getStocks() == null || portfolio.getStocks().isEmpty()) {
            return;
        }

        double totalValue = 0;
        double totalCost = 0;

        // Get current date
        LocalDate today = LocalDate.now();

        for (Portfolio.PortfolioStock stock : portfolio.getStocks()) {
            // Get latest stock data from database
            List<StockData> recentData = stockDataRepository
                    .findBySymbolAndDateBetweenOrderByDateAsc(
                            stock.getSymbol(),
                            today.minusDays(5), // Look back a few days in case market was closed
                            today);

            if (!recentData.isEmpty()) {
                // Get the most recent data
                StockData latestData = recentData.get(recentData.size() - 1);
                double currentPrice = latestData.getClose();
                stock.setCurrentPrice(currentPrice);

                // Calculate values
                double stockValue = currentPrice * stock.getShares();
                double stockCost = stock.getEntryPrice() * stock.getShares();
                double returnValue = stockValue - stockCost;
                double returnPercentage = (returnValue / stockCost) * 100;

                stock.setReturnValue(returnValue);
                stock.setReturnPercentage(returnPercentage);

                totalValue += stockValue;
                totalCost += stockCost;
            }
        }

        // Calculate portfolio totals
        portfolio.setTotalValue(totalValue);
        portfolio.setTotalReturn(totalValue - totalCost);
        portfolio.setTotalReturnPercentage((portfolio.getTotalReturn() / totalCost) * 100);

        // Update stock weights based on current values
        for (Portfolio.PortfolioStock stock : portfolio.getStocks()) {
            double stockValue = stock.getCurrentPrice() * stock.getShares();
            stock.setWeight((stockValue / totalValue) * 100);
        }

        // Update timestamp
        portfolio.setUpdatedAt(LocalDateTime.now());

        // Save updated portfolio
        portfolioRepository.save(portfolio);
    }

    /**
     * Start portfolio optimization process
     */
    @Async
    public void optimizePortfolio(String portfolioId, double riskTolerance) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));

        try {
            // Set status to OPTIMIZING
            portfolio.setOptimizationStatus("OPTIMIZING");
            portfolioRepository.save(portfolio);

            // Get historical data for all stocks in portfolio
            Map<String, List<StockData>> historicalDataMap = new HashMap<>();
            Set<String> symbols = portfolio.getStocks().stream()
                    .map(Portfolio.PortfolioStock::getSymbol)
                    .collect(Collectors.toSet());

            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusYears(2); // 2 years of historical data

            for (String symbol : symbols) {
                List<StockData> historicalData = stockDataRepository
                        .findBySymbolAndDateBetweenOrderByDateAsc(symbol, startDate, endDate);
                historicalDataMap.put(symbol, historicalData);
            }

            // Get predictions for all stocks
            Map<String, StockPrediction> predictionMap = new HashMap<>();
            String userId = portfolio.getUserId(); // Get userId from portfolio

            for (String symbol : symbols) {
                List<StockPrediction> predictions = predictionRepository
                        .findBySymbolOrderByPredictionDateDesc(symbol);
                if (!predictions.isEmpty()) {
                    predictionMap.put(symbol, predictions.get(0));
                } else {
                    // Generate new prediction if none exists
                    List<StockPrediction> newPredictions = neuralNetworkService.predictFuturePrices(symbol, userId);
                    if (!newPredictions.isEmpty()) {
                        predictionMap.put(symbol, newPredictions.get(0));
                    }
                }
            }

            // Calculate expected returns, volatilities, and correlation matrix
            Map<String, Double> expectedReturns = calculateExpectedReturns(historicalDataMap, predictionMap);
            Map<String, Double> volatilities = calculateVolatilities(historicalDataMap);
            Map<String, Map<String, Double>> correlationMatrix = calculateCorrelationMatrix(historicalDataMap);

            // Optimize portfolio weights using Markowitz model
            Map<String, Double> optimalWeights = findOptimalWeights(
                    symbols, expectedReturns, volatilities, correlationMatrix, riskTolerance);

            // Update portfolio with new optimal weights
            updatePortfolioWeights(portfolio, optimalWeights);

            // Set status to OPTIMIZED and save
            portfolio.setOptimizationStatus("OPTIMIZED");
            portfolio.setLastOptimizedAt(LocalDateTime.now());
            portfolioRepository.save(portfolio);

        } catch (Exception e) {
            // Log error and set status back to NOT_OPTIMIZED
            System.err.println("Error optimizing portfolio: " + e.getMessage());
            e.printStackTrace();

            portfolio.setOptimizationStatus("OPTIMIZATION_FAILED");
            portfolioRepository.save(portfolio);
        }
    }

    /**
     * Calculate expected returns for each stock using historical data and predictions
     */
    private Map<String, Double> calculateExpectedReturns(
            Map<String, List<StockData>> historicalDataMap,
            Map<String, StockPrediction> predictionMap) {

        Map<String, Double> expectedReturns = new HashMap<>();

        for (Map.Entry<String, List<StockData>> entry : historicalDataMap.entrySet()) {
            String symbol = entry.getKey();
            List<StockData> historicalData = entry.getValue();

            if (historicalData.size() < 30) {
                continue; // Skip if not enough data
            }

            // Calculate historical average daily return
            double historicalReturn = calculateAverageReturn(historicalData);

            // Get predicted return if available
            double predictedReturn = 0;
            if (predictionMap.containsKey(symbol)) {
                StockPrediction prediction = predictionMap.get(symbol);
                predictedReturn = prediction.getPredictedChangePercentage() / 100.0;
            }

            // Blend historical and predicted returns (70% weight on predictions)
            double blendedReturn = (0.3 * historicalReturn) + (0.7 * predictedReturn);
            expectedReturns.put(symbol, blendedReturn);
        }

        return expectedReturns;
    }

    /**
     * Calculate average daily return from historical data
     */
    private double calculateAverageReturn(List<StockData> data) {
        double sum = 0;
        int count = 0;

        for (int i = 1; i < data.size(); i++) {
            double previousClose = data.get(i - 1).getClose();
            double currentClose = data.get(i).getClose();
            double dailyReturn = (currentClose - previousClose) / previousClose;
            sum += dailyReturn;
            count++;
        }

        return count > 0 ? sum / count : 0;
    }

    /**
     * Calculate volatilities (standard deviations) for each stock
     */
    private Map<String, Double> calculateVolatilities(Map<String, List<StockData>> historicalDataMap) {
        Map<String, Double> volatilities = new HashMap<>();

        for (Map.Entry<String, List<StockData>> entry : historicalDataMap.entrySet()) {
            String symbol = entry.getKey();
            List<StockData> historicalData = entry.getValue();

            if (historicalData.size() < 30) {
                continue; // Skip if not enough data
            }

            List<Double> returns = new ArrayList<>();
            for (int i = 1; i < historicalData.size(); i++) {
                double previousClose = historicalData.get(i - 1).getClose();
                double currentClose = historicalData.get(i).getClose();
                double dailyReturn = (currentClose - previousClose) / previousClose;
                returns.add(dailyReturn);
            }

            double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double variance = returns.stream()
                    .mapToDouble(r -> Math.pow(r - mean, 2))
                    .average().orElse(0);
            double stdDev = Math.sqrt(variance);

            volatilities.put(symbol, stdDev);
        }

        return volatilities;
    }

    /**
     * Calculate correlation matrix between stocks
     */
    private Map<String, Map<String, Double>> calculateCorrelationMatrix(
            Map<String, List<StockData>> historicalDataMap) {

        Map<String, Map<String, Double>> correlationMatrix = new HashMap<>();
        Map<String, List<Double>> returnSeries = new HashMap<>();

        // Extract return series for each stock
        for (Map.Entry<String, List<StockData>> entry : historicalDataMap.entrySet()) {
            String symbol = entry.getKey();
            List<StockData> historicalData = entry.getValue();

            if (historicalData.size() < 30) {
                continue;
            }

            List<Double> returns = new ArrayList<>();
            for (int i = 1; i < historicalData.size(); i++) {
                double previousClose = historicalData.get(i - 1).getClose();
                double currentClose = historicalData.get(i).getClose();
                double dailyReturn = (currentClose - previousClose) / previousClose;
                returns.add(dailyReturn);
            }

            returnSeries.put(symbol, returns);
        }

        // Calculate correlations between all pairs of stocks
        for (String symbol1 : returnSeries.keySet()) {
            Map<String, Double> correlations = new HashMap<>();
            List<Double> returns1 = returnSeries.get(symbol1);

            for (String symbol2 : returnSeries.keySet()) {
                List<Double> returns2 = returnSeries.get(symbol2);

                // Self-correlation is always 1
                if (symbol1.equals(symbol2)) {
                    correlations.put(symbol2, 1.0);
                    continue;
                }

                // Make sure both return series have the same length
                int minSize = Math.min(returns1.size(), returns2.size());
                if (minSize < 30) {
                    correlations.put(symbol2, 0.0);
                    continue;
                }

                List<Double> trimmed1 = returns1.subList(0, minSize);
                List<Double> trimmed2 = returns2.subList(0, minSize);

                double correlation = calculateCorrelation(trimmed1, trimmed2);
                correlations.put(symbol2, correlation);
            }

            correlationMatrix.put(symbol1, correlations);
        }

        return correlationMatrix;
    }

    /**
     * Calculate correlation coefficient between two series
     */
    private double calculateCorrelation(List<Double> series1, List<Double> series2) {
        double mean1 = series1.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double mean2 = series2.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        double sum = 0;
        double sum1Sq = 0;
        double sum2Sq = 0;

        for (int i = 0; i < series1.size(); i++) {
            double diff1 = series1.get(i) - mean1;
            double diff2 = series2.get(i) - mean2;
            sum += diff1 * diff2;
            sum1Sq += diff1 * diff1;
            sum2Sq += diff2 * diff2;
        }

        if (sum1Sq == 0 || sum2Sq == 0) {
            return 0;
        }

        return sum / (Math.sqrt(sum1Sq) * Math.sqrt(sum2Sq));
    }

    /**
     * Find optimal portfolio weights using Markowitz model with neural network insights
     */
    private Map<String, Double> findOptimalWeights(
            Set<String> symbols,
            Map<String, Double> expectedReturns,
            Map<String, Double> volatilities,
            Map<String, Map<String, Double>> correlationMatrix,
            double riskTolerance) {

        // Simple implementation of Mean-Variance Optimization
        // In a real application, this would use more sophisticated algorithms

        // Prepare symbols list
        List<String> symbolsList = new ArrayList<>(symbols);
        int n = symbolsList.size();

        // Initialize with equal weights
        Map<String, Double> weights = new HashMap<>();
        for (String symbol : symbolsList) {
            weights.put(symbol, 1.0 / n);
        }

        // Refine weights using a simple gradient descent approach
        double learningRate = 0.01;
        int iterations = 1000;

        for (int iter = 0; iter < iterations; iter++) {
            // Calculate portfolio return and risk
            double portfolioReturn = calculatePortfolioReturn(weights, expectedReturns);
            double portfolioRisk = calculatePortfolioRisk(weights, volatilities, correlationMatrix);

            // Calculate utility (return - risk_tolerance * risk)
            double utility = portfolioReturn - (riskTolerance * portfolioRisk);

            // Calculate gradients for each weight
            Map<String, Double> gradients = new HashMap<>();
            for (String symbol : symbolsList) {
                // Perturb weight slightly
                Map<String, Double> perturbedWeights = new HashMap<>(weights);
                double perturbation = 0.001;
                perturbedWeights.put(symbol, weights.get(symbol) + perturbation);

                // Normalize perturbed weights
                normalizeWeights(perturbedWeights);

                // Calculate perturbed utility
                double perturbedReturn = calculatePortfolioReturn(perturbedWeights, expectedReturns);
                double perturbedRisk = calculatePortfolioRisk(perturbedWeights, volatilities, correlationMatrix);
                double perturbedUtility = perturbedReturn - (riskTolerance * perturbedRisk);

                // Calculate gradient
                double gradient = (perturbedUtility - utility) / perturbation;
                gradients.put(symbol, gradient);
            }

            // Update weights
            for (String symbol : symbolsList) {
                weights.put(symbol, weights.get(symbol) + learningRate * gradients.get(symbol));
            }

            // Ensure weights are positive and sum to 1
            normalizeWeights(weights);
        }

        return weights;
    }

    /**
     * Normalize weights to ensure they are positive and sum to 1
     */
    private void normalizeWeights(Map<String, Double> weights) {
        // Ensure weights are positive
        for (String symbol : weights.keySet()) {
            weights.put(symbol, Math.max(0, weights.get(symbol)));
        }

        // Normalize to sum to 1
        double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (sum > 0) {
            for (String symbol : weights.keySet()) {
                weights.put(symbol, weights.get(symbol) / sum);
            }
        } else {
            // If all weights are negative or zero, default to equal weights
            double equalWeight = 1.0 / weights.size();
            for (String symbol : weights.keySet()) {
                weights.put(symbol, equalWeight);
            }
        }
    }

    /**
     * Calculate expected portfolio return based on weights and expected returns
     */
    private double calculatePortfolioReturn(Map<String, Double> weights, Map<String, Double> expectedReturns) {
        double portfolioReturn = 0;

        for (String symbol : weights.keySet()) {
            if (expectedReturns.containsKey(symbol)) {
                portfolioReturn += weights.get(symbol) * expectedReturns.get(symbol);
            }
        }

        return portfolioReturn;
    }

    /**
     * Calculate portfolio risk (standard deviation) based on weights, volatilities, and correlations
     */
    private double calculatePortfolioRisk(
            Map<String, Double> weights,
            Map<String, Double> volatilities,
            Map<String, Map<String, Double>> correlationMatrix) {

        double portfolioVariance = 0;

        for (String symbol1 : weights.keySet()) {
            if (!volatilities.containsKey(symbol1) || !correlationMatrix.containsKey(symbol1)) {
                continue;
            }

            double weight1 = weights.get(symbol1);
            double vol1 = volatilities.get(symbol1);

            for (String symbol2 : weights.keySet()) {
                if (!volatilities.containsKey(symbol2) || !correlationMatrix.get(symbol1).containsKey(symbol2)) {
                    continue;
                }

                double weight2 = weights.get(symbol2);
                double vol2 = volatilities.get(symbol2);
                double correlation = correlationMatrix.get(symbol1).get(symbol2);

                portfolioVariance += weight1 * weight2 * vol1 * vol2 * correlation;
            }
        }

        return Math.sqrt(portfolioVariance);
    }

    /**
     * Update portfolio with new optimal weights
     */
    private void updatePortfolioWeights(Portfolio portfolio, Map<String, Double> optimalWeights) {
        double totalValue = portfolio.getTotalValue() != null ? portfolio.getTotalValue() : 0;

        List<Portfolio.PortfolioStock> updatedStocks = new ArrayList<>();

        for (Portfolio.PortfolioStock stock : portfolio.getStocks()) {
            String symbol = stock.getSymbol();

            if (optimalWeights.containsKey(symbol)) {
                // Calculate optimal value for this stock
                double optimalWeight = optimalWeights.get(symbol);
                double optimalValue = totalValue * optimalWeight;

                // Calculate current value
                double currentValue = stock.getCurrentPrice() * stock.getShares();

                // Calculate optimal shares (rounded to whole shares)
                int optimalShares = (int) Math.round(optimalValue / stock.getCurrentPrice());

                // Update stock with optimal allocation
                Portfolio.PortfolioStock updatedStock = new Portfolio.PortfolioStock();
                updatedStock.setSymbol(symbol);
                updatedStock.setCompanyName(stock.getCompanyName());
                updatedStock.setCurrentPrice(stock.getCurrentPrice());
                updatedStock.setShares(optimalShares);
                updatedStock.setEntryPrice(stock.getEntryPrice());
                updatedStock.setEntryDate(stock.getEntryDate());
                updatedStock.setWeight(optimalWeight * 100); // Convert to percentage

                // Calculate returns
                double newValue = optimalShares * stock.getCurrentPrice();
                double cost = optimalShares * stock.getEntryPrice();
                updatedStock.setReturnValue(newValue - cost);
                updatedStock.setReturnPercentage(cost > 0 ? ((newValue - cost) / cost) * 100 : 0);

                updatedStocks.add(updatedStock);
            }
        }

        portfolio.setStocks(updatedStocks);

        // Calculate risk score based on the portfolio standard deviation
        double riskScore = calculatePortfolioRiskScore(portfolio);
        portfolio.setRiskScore(riskScore);
    }

    /**
     * Calculate portfolio risk score (0-100) where 0 is lowest risk and 100 is highest
     */
    private double calculatePortfolioRiskScore(Portfolio portfolio) {
        // Get historical data for portfolio stocks
        Map<String, List<StockData>> historicalDataMap = new HashMap<>();
        Set<String> symbols = portfolio.getStocks().stream()
                .map(Portfolio.PortfolioStock::getSymbol)
                .collect(Collectors.toSet());

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusYears(1);

        for (String symbol : symbols) {
            List<StockData> historicalData = stockDataRepository
                    .findBySymbolAndDateBetweenOrderByDateAsc(symbol, startDate, endDate);
            historicalDataMap.put(symbol, historicalData);
        }

        // Calculate volatilities and correlation matrix
        Map<String, Double> volatilities = calculateVolatilities(historicalDataMap);
        Map<String, Map<String, Double>> correlationMatrix = calculateCorrelationMatrix(historicalDataMap);

        // Calculate weights from shares and prices
        Map<String, Double> weights = new HashMap<>();
        double totalValue = portfolio.getTotalValue() != null ? portfolio.getTotalValue() : 0;

        if (totalValue <= 0) {
            return 50; // Default risk score if no value
        }

        for (Portfolio.PortfolioStock stock : portfolio.getStocks()) {
            double stockValue = stock.getCurrentPrice() * stock.getShares();
            weights.put(stock.getSymbol(), stockValue / totalValue);
        }

        // Calculate portfolio standard deviation
        double portfolioRisk = calculatePortfolioRisk(weights, volatilities, correlationMatrix);

        // Map to a 0-100 scale (assuming max risk is 0.03 daily std dev)
        double maxRisk = 0.03;
        double riskScore = (portfolioRisk / maxRisk) * 100;

        // Cap to 0-100 range
        return Math.min(100, Math.max(0, riskScore));
    }

    /**
     * Get optimization suggestions for a portfolio
     */
    public Map<String, Object> getOptimizationSuggestions(String portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));

        Map<String, Object> suggestions = new HashMap<>();
        List<Map<String, Object>> stockSuggestions = new ArrayList<>();

        // Get current stock allocations
        Map<String, Portfolio.PortfolioStock> currentStocks = portfolio.getStocks().stream()
                .collect(Collectors.toMap(Portfolio.PortfolioStock::getSymbol, s -> s));

        // Get predictions for portfolio stocks
        Map<String, StockPrediction> predictions = new HashMap<>();
        String userId = portfolio.getUserId();

        for (Portfolio.PortfolioStock stock : portfolio.getStocks()) {
            List<StockPrediction> stockPredictions = predictionRepository
                    .findBySymbolOrderByPredictionDateDesc(stock.getSymbol());
            if (!stockPredictions.isEmpty()) {
                predictions.put(stock.getSymbol(), stockPredictions.get(0));
            } else {
                // Generate prediction if none exists
                try {
                    List<StockPrediction> newPredictions = neuralNetworkService.predictFuturePrices(stock.getSymbol(), userId);
                    if (!newPredictions.isEmpty()) {
                        predictions.put(stock.getSymbol(), newPredictions.get(0));
                    }
                } catch (Exception e) {
                    System.err.println("Error generating prediction for " + stock.getSymbol() + ": " + e.getMessage());
                }
            }
        }

        // Create suggestions for each stock
        for (Portfolio.PortfolioStock stock : portfolio.getStocks()) {
            Map<String, Object> suggestion = new HashMap<>();
            suggestion.put("symbol", stock.getSymbol());
            suggestion.put("companyName", stock.getCompanyName());
            suggestion.put("currentShares", stock.getShares());
            suggestion.put("currentWeight", stock.getWeight());

            // Get prediction if available
            if (predictions.containsKey(stock.getSymbol())) {
                StockPrediction prediction = predictions.get(stock.getSymbol());
                suggestion.put("predictedChangePercent", prediction.getPredictedChangePercentage());
                suggestion.put("confidenceScore", prediction.getConfidenceScore());

                // Suggest action based on prediction
                String action;
                int suggestionStrength;

                if (prediction.getPredictedChangePercentage() > 5) {
                    action = "BUY";
                    suggestionStrength = (int) (prediction.getConfidenceScore() / 20); // 0-5 scale
                } else if (prediction.getPredictedChangePercentage() < -3) {
                    action = "SELL";
                    suggestionStrength = (int) (prediction.getConfidenceScore() / 20);
                } else {
                    action = "HOLD";
                    suggestionStrength = 3;
                }

                suggestion.put("suggestedAction", action);
                suggestion.put("suggestionStrength", suggestionStrength);
            } else {
                suggestion.put("suggestedAction", "HOLD");
                suggestion.put("suggestionStrength", 3);
            }

            stockSuggestions.add(suggestion);
        }

        // Add overall portfolio suggestions
        suggestions.put("stocks", stockSuggestions);
        suggestions.put("diversificationScore", calculateDiversificationScore(portfolio));
        suggestions.put("riskScore", portfolio.getRiskScore());
        suggestions.put("rebalancingNeeded", isRebalancingNeeded(portfolio));

        return suggestions;
    }

    /**
     * Calculate diversification score (0-100) where 0 is not diversified and 100 is perfectly diversified
     */
    private double calculateDiversificationScore(Portfolio portfolio) {
        if (portfolio.getStocks() == null || portfolio.getStocks().isEmpty()) {
            return 0;
        }

        // Count number of stocks
        int stockCount = portfolio.getStocks().size();

        // Calculate weight concentration (Herfindahl-Hirschman Index)
        double sumSquaredWeights = portfolio.getStocks().stream()
                .mapToDouble(s -> Math.pow(s.getWeight() / 100, 2))
                .sum();

        // Calculate sector diversity (not implemented in this example)
        // Would require additional data about stock sectors

        // Calculate final score based on number of stocks and weight concentration
        double countScore = Math.min(stockCount / 20.0, 1.0) * 50; // Max score for 20+ stocks
        double concentrationScore = (1 - sumSquaredWeights) * 50;   // Lower concentration is better

        return countScore + concentrationScore;
    }

    /**
     * Check if portfolio rebalancing is needed
     */
    private boolean isRebalancingNeeded(Portfolio portfolio) {
        if (portfolio.getStocks() == null || portfolio.getStocks().isEmpty()) {
            return false;
        }

        // Check if any stock weight deviates more than 5% from target
        for (Portfolio.PortfolioStock stock : portfolio.getStocks()) {
            // In a real application, you would compare to target weights
            // For simplicity, we'll compare to equal weight
            double equalWeight = 100.0 / portfolio.getStocks().size();
            if (Math.abs(stock.getWeight() - equalWeight) > 5) {
                return true;
            }
        }

        return false;
    }
}