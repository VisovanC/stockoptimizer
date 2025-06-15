package com.cv.stockoptimizer.controller;

import com.cv.stockoptimizer.model.dto.request.PortfolioRequest;
import com.cv.stockoptimizer.model.dto.request.StockRequest;
import com.cv.stockoptimizer.model.entity.Portfolio;
import com.cv.stockoptimizer.model.entity.StockData;
import com.cv.stockoptimizer.repository.PortfolioRepository;
import com.cv.stockoptimizer.repository.StockDataRepository;
import com.cv.stockoptimizer.service.data.MarketDataCollectorService;
import com.cv.stockoptimizer.service.data.TechnicalIndicatorService;
import com.cv.stockoptimizer.service.optimization.PortfolioOptimizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioOptimizationService optimizationService;
    private final MarketDataCollectorService marketDataCollectorService;
    private final TechnicalIndicatorService technicalIndicatorService;
    private final StockDataRepository stockDataRepository;

    @Autowired
    public PortfolioController(
            PortfolioRepository portfolioRepository,
            PortfolioOptimizationService optimizationService,
            MarketDataCollectorService marketDataCollectorService,
            TechnicalIndicatorService technicalIndicatorService,
            StockDataRepository stockDataRepository) {
        this.portfolioRepository = portfolioRepository;
        this.optimizationService = optimizationService;
        this.marketDataCollectorService = marketDataCollectorService;
        this.technicalIndicatorService = technicalIndicatorService;
        this.stockDataRepository = stockDataRepository;
    }

    @GetMapping
    public ResponseEntity<List<Portfolio>> getAllPortfolios() {
        String currentUserId = getCurrentUserId();
        List<Portfolio> portfolios = portfolioRepository.findByUserId(currentUserId);
        return ResponseEntity.ok(portfolios);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Portfolio> getPortfolioById(@PathVariable String id) {
        String currentUserId = getCurrentUserId();

        Portfolio portfolio = portfolioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));

        if (!portfolio.getUserId().equals(currentUserId)) {
            return ResponseEntity.status(403).body(null);
        }

        Portfolio updatedPortfolio = optimizationService.getPortfolioWithCurrentValues(id);

        return ResponseEntity.ok(updatedPortfolio);
    }

    @PostMapping
    public ResponseEntity<Portfolio> createPortfolio(@Valid @RequestBody PortfolioRequest portfolioRequest) {
        String currentUserId = getCurrentUserId();

        Portfolio portfolio = new Portfolio();
        portfolio.setUserId(currentUserId);
        portfolio.setName(portfolioRequest.getName());
        portfolio.setDescription(portfolioRequest.getDescription());
        portfolio.setCreatedAt(LocalDateTime.now());
        portfolio.setUpdatedAt(LocalDateTime.now());
        portfolio.setOptimizationStatus("NOT_OPTIMIZED");

        List<Portfolio.PortfolioStock> stocks = new ArrayList<>();

        if (portfolioRequest.getStocks() != null) {
            Set<String> uniqueSymbols = new HashSet<>();
            for (StockRequest stockRequest : portfolioRequest.getStocks()) {
                uniqueSymbols.add(stockRequest.getSymbol().toUpperCase());
            }

            ensureStockDataExists(uniqueSymbols, currentUserId);

            for (StockRequest stockRequest : portfolioRequest.getStocks()) {
                Portfolio.PortfolioStock stock = new Portfolio.PortfolioStock();
                stock.setSymbol(stockRequest.getSymbol().toUpperCase());
                stock.setCompanyName(stockRequest.getCompanyName());
                stock.setShares(stockRequest.getShares());
                stock.setEntryPrice(stockRequest.getEntryPrice());
                stock.setEntryDate(stockRequest.getEntryDate() != null ?
                        stockRequest.getEntryDate() : LocalDateTime.now());
                stocks.add(stock);
            }
        }

        portfolio.setStocks(stocks);

        Portfolio savedPortfolio = portfolioRepository.save(portfolio);

        return ResponseEntity.ok(savedPortfolio);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Portfolio> updatePortfolio(
            @PathVariable String id,
            @Valid @RequestBody PortfolioRequest portfolioRequest) {

        String currentUserId = getCurrentUserId();

        Portfolio portfolio = portfolioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));
        if (!portfolio.getUserId().equals(currentUserId)) {
            return ResponseEntity.status(403).body(null);
        }
        portfolio.setName(portfolioRequest.getName());
        portfolio.setDescription(portfolioRequest.getDescription());
        portfolio.setUpdatedAt(LocalDateTime.now());
        if (portfolioRequest.getStocks() != null) {
            List<Portfolio.PortfolioStock> stocks = new ArrayList<>();

            Set<String> uniqueSymbols = new HashSet<>();
            for (StockRequest stockRequest : portfolioRequest.getStocks()) {
                uniqueSymbols.add(stockRequest.getSymbol().toUpperCase());
            }

            ensureStockDataExists(uniqueSymbols, currentUserId);

            for (StockRequest stockRequest : portfolioRequest.getStocks()) {
                Portfolio.PortfolioStock stock = new Portfolio.PortfolioStock();
                stock.setSymbol(stockRequest.getSymbol().toUpperCase());
                stock.setCompanyName(stockRequest.getCompanyName());
                stock.setShares(stockRequest.getShares());
                stock.setEntryPrice(stockRequest.getEntryPrice());
                stock.setEntryDate(stockRequest.getEntryDate() != null ?
                        stockRequest.getEntryDate() : LocalDateTime.now());
                stocks.add(stock);
            }

            portfolio.setStocks(stocks);
        }

        portfolio.setOptimizationStatus("NOT_OPTIMIZED");

        Portfolio updatedPortfolio = portfolioRepository.save(portfolio);

        return ResponseEntity.ok(updatedPortfolio);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePortfolio(@PathVariable String id) {
        String currentUserId = getCurrentUserId();

        Portfolio portfolio = portfolioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));

        if (!portfolio.getUserId().equals(currentUserId)) {
            return ResponseEntity.status(403).body(null);
        }

        portfolioRepository.deleteById(id);

        return ResponseEntity.ok(Map.of("message", "Portfolio deleted successfully"));
    }

    @PostMapping("/{id}/optimize")
    public ResponseEntity<?> optimizePortfolio(
            @PathVariable String id,
            @RequestParam(defaultValue = "0.5") double riskTolerance) {

        String currentUserId = getCurrentUserId();

        Portfolio portfolio = portfolioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));

        if (!portfolio.getUserId().equals(currentUserId)) {
            return ResponseEntity.status(403).body(null);
        }

        optimizationService.optimizePortfolio(id, riskTolerance);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Portfolio optimization started");
        response.put("portfolioId", id);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/optimize/status")
    public ResponseEntity<?> getOptimizationStatus(@PathVariable String id) {
        String currentUserId = getCurrentUserId();

        Portfolio portfolio = portfolioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));

        if (!portfolio.getUserId().equals(currentUserId)) {
            return ResponseEntity.status(403).body(null);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", portfolio.getOptimizationStatus());
        response.put("lastOptimizedAt", portfolio.getLastOptimizedAt());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/suggestions")
    public ResponseEntity<?> getPortfolioSuggestions(@PathVariable String id) {
        String currentUserId = getCurrentUserId();

        Portfolio portfolio = portfolioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));

        if (!portfolio.getUserId().equals(currentUserId)) {
            return ResponseEntity.status(403).body(null);
        }

        Map<String, Object> suggestions = optimizationService.getOptimizationSuggestions(id);

        return ResponseEntity.ok(suggestions);
    }

    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }

    private void ensureStockDataExists(Set<String> symbols, String userId) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusYears(2);

        for (String symbol : symbols) {
            List<StockData> existingData = stockDataRepository
                    .findByUserIdAndSymbolAndDateBetweenOrderByDateAsc(userId, symbol, startDate, endDate);

            if (existingData.size() < 100) {
                System.out.println("Insufficient data for " + symbol + ", fetching/generating data...");
                CompletableFuture.runAsync(() -> {
                    try {
                        List<StockData> historicalData = marketDataCollectorService
                                .fetchHistoricalData(symbol, startDate, endDate, userId);

                        if (historicalData.isEmpty() || historicalData.size() < 100) {
                            System.out.println("Yahoo Finance failed or insufficient data for " + symbol +
                                    ", generating sample data...");
                            historicalData = marketDataCollectorService
                                    .generateSampleData(symbol, startDate, endDate, userId);
                        }

                        stockDataRepository.saveAll(historicalData);
                        System.out.println("Saved " + historicalData.size() + " data points for " + symbol);

                        technicalIndicatorService.calculateAllIndicators(symbol, startDate, endDate, userId);
                        System.out.println("Calculated technical indicators for " + symbol);

                    } catch (Exception e) {
                        System.err.println("Error ensuring data for " + symbol + ": " + e.getMessage());
                        try {
                            List<StockData> sampleData = marketDataCollectorService
                                    .generateSampleData(symbol, startDate, endDate, userId);
                            stockDataRepository.saveAll(sampleData);
                            technicalIndicatorService.calculateAllIndicators(symbol, startDate, endDate, userId);
                            System.out.println("Generated sample data for " + symbol + " after error");
                        } catch (Exception ex) {
                            System.err.println("Failed to generate sample data for " + symbol + ": " + ex.getMessage());
                        }
                    }
                });
            }
        }
    }
}