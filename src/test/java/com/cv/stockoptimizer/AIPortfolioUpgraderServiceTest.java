package com.cv.stockoptimizer;

import com.cv.stockoptimizer.config.AIPortfolioConfig;
import com.cv.stockoptimizer.model.entity.Portfolio;
import com.cv.stockoptimizer.model.entity.StockData;
import com.cv.stockoptimizer.model.entity.StockPrediction;
import com.cv.stockoptimizer.model.entity.TechnicalIndicator;
import com.cv.stockoptimizer.repository.MLModelRepository;
import com.cv.stockoptimizer.repository.PortfolioRepository;
import com.cv.stockoptimizer.repository.StockDataRepository;
import com.cv.stockoptimizer.repository.StockPredictionRepository;
import com.cv.stockoptimizer.repository.TechnicalIndicatorRepository;
import com.cv.stockoptimizer.service.history.PortfolioHistoryService;
import com.cv.stockoptimizer.service.ml.NeuralNetworkService;
import com.cv.stockoptimizer.service.optimization.AIPortfolioUpgraderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AIPortfolioUpgraderServiceTest {

    private AIPortfolioUpgraderService aiPortfolioUpgraderService;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private StockDataRepository stockDataRepository;

    @Mock
    private StockPredictionRepository predictionRepository;

    @Mock
    private TechnicalIndicatorRepository indicatorRepository;

    @Mock
    private MLModelRepository mlModelRepository;

    @Mock
    private NeuralNetworkService neuralNetworkService;

    @Mock
    private AIPortfolioConfig config;

    @Mock
    private PortfolioHistoryService historyService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Configure the mocked config
        when(config.getHistoricalDays()).thenReturn(365);
        when(config.getPredictionHorizon()).thenReturn(90);
        when(config.getMaxStockAllocation()).thenReturn(0.25);
        when(config.getMinStockAllocation()).thenReturn(0.02);
        when(config.getOptimizationIterations()).thenReturn(1000);
        when(config.getMaxExpansionStocks()).thenReturn(10);

        aiPortfolioUpgraderService = new AIPortfolioUpgraderService(
                portfolioRepository,
                stockDataRepository,
                predictionRepository,
                indicatorRepository,
                mlModelRepository,
                neuralNetworkService,
                config,
                historyService
        );
    }

    @Test
    void generatePortfolioUpgrade_shouldReturnRecommendations() throws Exception {
        // Arrange
        String portfolioId = "test-portfolio-id";
        double riskTolerance = 0.5;
        boolean expandUniverse = false;

        Portfolio portfolio = createTestPortfolio();
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        // Mock stock data
        List<StockData> stockData = createTestStockData("AAPL");
        when(stockDataRepository.findBySymbolAndDateBetweenOrderByDateAsc(
                eq("AAPL"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(stockData);

        // Mock stock predictions
        List<StockPrediction> predictions = createTestPredictions("AAPL");
        when(predictionRepository.findBySymbolOrderByPredictionDateDesc("AAPL"))
                .thenReturn(predictions);

        // Mock technical indicators
        List<TechnicalIndicator> indicators = createTestIndicators("AAPL");
        when(indicatorRepository.findBySymbolAndDateBetweenOrderByDateAsc(
                eq("AAPL"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(indicators);

        // Mock available symbols
        Set<String> availableSymbols = new HashSet<>(Arrays.asList("AAPL", "MSFT", "GOOGL"));
        when(stockDataRepository.findDistinctSymbols()).thenReturn(availableSymbols);

        // Act
        Map<String, Object> result = aiPortfolioUpgraderService.generatePortfolioUpgrade(
                portfolioId, riskTolerance, expandUniverse);

        // Assert
        assertNotNull(result);
        assertEquals(portfolioId, result.get("portfolioId"));
        assertEquals(riskTolerance, result.get("riskTolerance"));
        assertEquals(expandUniverse, result.get("universeExpanded"));

        @SuppressWarnings("unchecked")
        Map<String, Double> recommendedAllocations = (Map<String, Double>) result.get("recommendedAllocations");
        assertNotNull(recommendedAllocations);
        assertTrue(recommendedAllocations.containsKey("AAPL"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actions = (List<Map<String, Object>>) result.get("recommendedActions");
        assertNotNull(actions);
        assertFalse(actions.isEmpty());

        verify(portfolioRepository).findById(portfolioId);
        verify(stockDataRepository).findBySymbolAndDateBetweenOrderByDateAsc(
                eq("AAPL"), any(LocalDate.class), any(LocalDate.class));
        verify(predictionRepository).findBySymbolOrderByPredictionDateDesc("AAPL");
    }

    @Test
    void applyPortfolioUpgrade_shouldUpdatePortfolio() throws Exception {
        // Arrange
        String portfolioId = "test-portfolio-id";
        Map<String, Double> optimizedAllocations = new HashMap<>();
        optimizedAllocations.put("AAPL", 0.6);
        optimizedAllocations.put("MSFT", 0.4);

        Portfolio portfolio = createTestPortfolio();
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(portfolioRepository.save(any(Portfolio.class))).thenReturn(portfolio);

        // Mock latest stock data
        StockData appleData = new StockData();
        appleData.setSymbol("AAPL");
        appleData.setClose(150.0);

        StockData msftData = new StockData();
        msftData.setSymbol("MSFT");
        msftData.setClose(300.0);

        when(stockDataRepository.findBySymbolAndDateBetweenOrderByDateAsc(
                eq("AAPL"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.singletonList(appleData));

        when(stockDataRepository.findBySymbolAndDateBetweenOrderByDateAsc(
                eq("MSFT"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.singletonList(msftData));

        // Mock history service
        when(historyService.recordAiRecommendation(
                any(Portfolio.class), anyMap(), anyDouble(), anyString()))
                .thenReturn(null);

        // Act
        Portfolio result = aiPortfolioUpgraderService.applyPortfolioUpgrade(portfolioId, optimizedAllocations);

        // Assert
        assertNotNull(result);
        assertEquals(portfolioId, result.getId());
        assertTrue(result.getHasAiRecommendations());
        assertNotNull(result.getLastAiRecommendationDate());

        verify(portfolioRepository).findById(portfolioId);
        verify(portfolioRepository).save(any(Portfolio.class));
        verify(historyService).recordAiRecommendation(
                any(Portfolio.class), anyMap(), anyDouble(), anyString());
    }

    // Helper methods to create test objects

    private Portfolio createTestPortfolio() {
        Portfolio portfolio = new Portfolio();
        portfolio.setId("test-portfolio-id");
        portfolio.setUserId("test-user-id");
        portfolio.setName("Test Portfolio");
        portfolio.setDescription("Portfolio for testing");
        portfolio.setCreatedAt(LocalDateTime.now().minusDays(30));
        portfolio.setUpdatedAt(LocalDateTime.now());
        portfolio.setTotalValue(10000.0);

        List<Portfolio.PortfolioStock> stocks = new ArrayList<>();

        Portfolio.PortfolioStock stock = new Portfolio.PortfolioStock();
        stock.setSymbol("AAPL");
        stock.setCompanyName("Apple Inc.");
        stock.setShares(10);
        stock.setEntryPrice(130.0);
        stock.setCurrentPrice(150.0);
        stock.setWeight(100.0);
        stock.setEntryDate(LocalDateTime.now().minusDays(30));
        stocks.add(stock);

        portfolio.setStocks(stocks);

        return portfolio;
    }

    private List<StockData> createTestStockData(String symbol) {
        List<StockData> dataList = new ArrayList<>();

        LocalDate today = LocalDate.now();

        // Create 100 days of data
        for (int i = 100; i >= 0; i--) {
            StockData data = new StockData();
            data.setSymbol(symbol);
            data.setDate(today.minusDays(i));
            data.setOpen(145.0 + i * 0.1);
            data.setHigh(150.0 + i * 0.1);
            data.setLow(140.0 + i * 0.1);
            data.setClose(148.0 + i * 0.1);
            data.setVolume(1000000L + i * 1000);

            dataList.add(data);
        }

        return dataList;
    }

    private List<StockPrediction> createTestPredictions(String symbol) {
        List<StockPrediction> predictions = new ArrayList<>();

        StockPrediction prediction = new StockPrediction();
        prediction.setSymbol(symbol);
        prediction.setPredictionDate(LocalDate.now());
        prediction.setTargetDate(LocalDate.now().plusDays(30));
        prediction.setCurrentPrice(150.0);
        prediction.setPredictedPrice(160.0);
        prediction.setPredictedChangePercentage(6.67);
        prediction.setConfidenceScore(75.0);

        predictions.add(prediction);

        return predictions;
    }

    private List<TechnicalIndicator> createTestIndicators(String symbol) {
        List<TechnicalIndicator> indicators = new ArrayList<>();

        LocalDate today = LocalDate.now();

        // Create 100 days of indicators
        for (int i = 100; i >= 0; i--) {
            TechnicalIndicator indicator = new TechnicalIndicator();
            indicator.setSymbol(symbol);
            indicator.setDate(today.minusDays(i));
            indicator.setPrice(148.0 + i * 0.1);
            indicator.setSma20(147.0 + i * 0.05);
            indicator.setSma50(145.0 + i * 0.03);
            indicator.setSma200(140.0 + i * 0.02);
            indicator.setRsi14(50.0 + i * 0.2);
            indicator.setMacdLine(0.5 + i * 0.01);
            indicator.setMacdSignal(0.4 + i * 0.01);
            indicator.setMacdHistogram(0.1 + i * 0.005);
            indicator.setBollingerUpper(155.0 + i * 0.1);
            indicator.setBollingerMiddle(148.0 + i * 0.1);
            indicator.setBollingerLower(141.0 + i * 0.1);

            indicators.add(indicator);
        }

        return indicators;
    }
}