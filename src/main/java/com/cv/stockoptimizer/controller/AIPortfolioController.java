package com.cv.stockoptimizer.controller;

import com.cv.stockoptimizer.model.entity.Portfolio;
import com.cv.stockoptimizer.service.metrics.AIMetricsService;
import com.cv.stockoptimizer.service.optimization.AIPortfolioUpgraderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for AI-powered portfolio upgrading
 */
@RestController
@RequestMapping("/api/ai/portfolio")
@Tag(name = "AI Portfolio", description = "AI portfolio optimization operations")
public class AIPortfolioController {

    private final AIPortfolioUpgraderService aiPortfolioUpgraderService;
    private final AIMetricsService aiMetricsService;

    @Autowired
    public AIPortfolioController(
            AIPortfolioUpgraderService aiPortfolioUpgraderService,
            AIMetricsService aiMetricsService) {
        this.aiPortfolioUpgraderService = aiPortfolioUpgraderService;
        this.aiMetricsService = aiMetricsService;
    }

    /**
     * Generate AI-powered upgrade recommendations for a portfolio
     */
    @Operation(
            summary = "Generate AI portfolio upgrade recommendations",
            description = "Analyzes a portfolio and generates AI-powered optimization recommendations"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recommendations generated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid portfolio ID or parameters"),
            @ApiResponse(responseCode = "403", description = "Unauthorized access to portfolio"),
            @ApiResponse(responseCode = "404", description = "Portfolio not found"),
            @ApiResponse(responseCode = "500", description = "Error generating recommendations")
    })
    @GetMapping("/{portfolioId}/upgrade-recommendations")
    public ResponseEntity<?> getUpgradeRecommendations(
            @Parameter(description = "Portfolio ID", required = true)
            @PathVariable String portfolioId,

            @Parameter(description = "Risk tolerance (0.0-1.0 where 0.0 is conservative and 1.0 is aggressive)")
            @RequestParam(defaultValue = "0.5") double riskTolerance,

            @Parameter(description = "Whether to expand universe and consider new stocks")
            @RequestParam(defaultValue = "false") boolean expandUniverse) {

        String currentUserId = getCurrentUserId();

        try {
            Map<String, Object> recommendations = aiPortfolioUpgraderService.generatePortfolioUpgrade(
                    portfolioId, riskTolerance, expandUniverse);

            return ResponseEntity.ok(recommendations);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Apply AI-recommended upgrades to a portfolio
     */
    @Operation(
            summary = "Apply AI portfolio upgrade",
            description = "Applies AI-recommended allocations to a portfolio"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Upgrade applied successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid portfolio ID or allocations"),
            @ApiResponse(responseCode = "403", description = "Unauthorized access to portfolio"),
            @ApiResponse(responseCode = "404", description = "Portfolio not found"),
            @ApiResponse(responseCode = "500", description = "Error applying upgrade")
    })
    @PostMapping("/{portfolioId}/apply-upgrade")
    public ResponseEntity<?> applyUpgrade(
            @Parameter(description = "Portfolio ID", required = true)
            @PathVariable String portfolioId,

            @Parameter(description = "Optimized allocations (map of symbol to allocation percentage)",
                    required = true)
            @RequestBody Map<String, Double> optimizedAllocations) {

        String currentUserId = getCurrentUserId();

        try {
            Portfolio upgradedPortfolio = aiPortfolioUpgraderService.applyPortfolioUpgrade(
                    portfolioId, optimizedAllocations);

            // Record the application for performance tracking
            aiMetricsService.recordRecommendationApplication(portfolioId, optimizedAllocations);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "AI portfolio upgrade applied successfully");
            response.put("portfolio", upgradedPortfolio);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get the current user ID from the security context
     */
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }
}