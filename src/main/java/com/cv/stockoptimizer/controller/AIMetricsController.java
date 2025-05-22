package com.cv.stockoptimizer.controller;

import com.cv.stockoptimizer.service.metrics.AIMetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for AI recommendation performance metrics
 */
@RestController
@RequestMapping("/api/ai/metrics")
public class AIMetricsController {

    private final AIMetricsService aiMetricsService;

    @Autowired
    public AIMetricsController(AIMetricsService aiMetricsService) {
        this.aiMetricsService = aiMetricsService;
    }

    /**
     * Get performance metrics for a specific portfolio's AI recommendations
     */
    @GetMapping("/portfolio/{portfolioId}")
    public ResponseEntity<?> getPortfolioPerformance(@PathVariable String portfolioId) {
        String currentUserId = getCurrentUserId();

        Map<String, Object> metrics = aiMetricsService.getRecommendationPerformance(portfolioId);

        return ResponseEntity.ok(metrics);
    }

    /**
     * Get aggregate performance statistics for all AI recommendations
     */
    @GetMapping("/aggregate")
    public ResponseEntity<?> getAggregateStats() {
        // Only allow admin users to access aggregate stats
        // Simplified check for example purposes
        String currentUserId = getCurrentUserId();

        Map<String, Object> stats = aiMetricsService.getAggregatePerformanceStats();

        return ResponseEntity.ok(stats);
    }

    /**
     * Record the application of AI recommendations to a portfolio
     * This is called internally by the AIPortfolioController
     */
    @PostMapping("/record/{portfolioId}")
    public ResponseEntity<?> recordRecommendationApplication(
            @PathVariable String portfolioId,
            @RequestBody Map<String, Double> allocations) {

        String currentUserId = getCurrentUserId();

        aiMetricsService.recordRecommendationApplication(portfolioId, allocations);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Recommendation application recorded for performance tracking");

        return ResponseEntity.ok(response);
    }

    /**
     * Get the current user ID from the security context
     */
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }
}