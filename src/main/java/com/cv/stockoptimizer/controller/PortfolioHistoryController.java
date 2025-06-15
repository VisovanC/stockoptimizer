package com.cv.stockoptimizer.controller;

import com.cv.stockoptimizer.model.entity.PortfolioHistory;
import com.cv.stockoptimizer.service.history.PortfolioHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/portfolio-history")
public class PortfolioHistoryController {

    private final PortfolioHistoryService historyService;

    @Autowired
    public PortfolioHistoryController(PortfolioHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/portfolio/{portfolioId}")
    public ResponseEntity<List<PortfolioHistory>> getPortfolioHistory(@PathVariable String portfolioId) {
        String currentUserId = getCurrentUserId();

        List<PortfolioHistory> history = historyService.getPortfolioHistory(portfolioId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/portfolio/{portfolioId}/ai-recommendations")
    public ResponseEntity<List<PortfolioHistory>> getAiRecommendationHistory(@PathVariable String portfolioId) {
        String currentUserId = getCurrentUserId();

        List<PortfolioHistory> history = historyService.getAiRecommendationHistory(portfolioId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/recent")
    public ResponseEntity<?> getRecentChanges(@RequestParam(defaultValue = "7") int days) {
        String currentUserId = getCurrentUserId();
        List<PortfolioHistory> history = historyService.getRecentChanges(days);

        Map<String, Object> response = new HashMap<>();
        response.put("days", days);
        response.put("count", history.size());
        response.put("history", history);

        return ResponseEntity.ok(response);
    }

    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }
}