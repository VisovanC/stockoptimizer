package com.cv.stockoptimizer.controller;

import com.cv.stockoptimizer.model.dto.request.PortfolioRequest;
import com.cv.stockoptimizer.model.dto.request.StockRequest;
import com.cv.stockoptimizer.model.entity.Portfolio;
import com.cv.stockoptimizer.repository.PortfolioRepository;
import com.cv.stockoptimizer.service.optimization.PortfolioOptimizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioOptimizationService optimizationService;

    @Autowired
    public PortfolioController(PortfolioRepository portfolioRepository, PortfolioOptimizationService optimizationService) {
        this.portfolioRepository = portfolioRepository;
        this.optimizationService = optimizationService;
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
            for (StockRequest stockRequest : portfolioRequest.getStocks()) {
                Portfolio.PortfolioStock stock = new Portfolio.PortfolioStock();
                stock.setSymbol(stockRequest.getSymbol());
                stock.setCompanyName(stockRequest.getCompanyName());
                stock.setShares(stockRequest.getShares());
                stock.setEntryPrice(stockRequest.getEntryPrice());
                stock.setEntryDate(LocalDateTime.now());
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

            for (StockRequest stockRequest : portfolioRequest.getStocks()) {
                Portfolio.PortfolioStock stock = new Portfolio.PortfolioStock();
                stock.setSymbol(stockRequest.getSymbol());
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
}