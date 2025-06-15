package com.cv.stockoptimizer.controller;

import com.cv.stockoptimizer.service.ml.PortfolioMLService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RestController
@RequestMapping("/api/ml/portfolio")
@Tag(name = "Portfolio ML", description = "Machine Learning operations for portfolios")
public class PortfolioMLController {

    private final PortfolioMLService portfolioMLService;

    @Autowired
    public PortfolioMLController(PortfolioMLService portfolioMLService) {
        this.portfolioMLService = portfolioMLService;
    }

    @Operation(
            summary = "Get ML training status",
            description = "Get the status of ML models for stocks in a portfolio"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Portfolio not found"),
            @ApiResponse(responseCode = "403", description = "Unauthorized access to portfolio")
    })
    @GetMapping("/{portfolioId}/status")
    public ResponseEntity<?> getMLStatus(
            @Parameter(description = "Portfolio ID", required = true)
            @PathVariable String portfolioId) {

        String currentUserId = getCurrentUserId();

        try {
            Map<String, Object> status = portfolioMLService.getPortfolioMLStatus(portfolioId);
            return ResponseEntity.ok(status);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @Operation(
            summary = "Train ML models for portfolio",
            description = "Start training neural network models for all stocks in a portfolio"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Training started successfully"),
            @ApiResponse(responseCode = "404", description = "Portfolio not found"),
            @ApiResponse(responseCode = "403", description = "Unauthorized access to portfolio"),
            @ApiResponse(responseCode = "500", description = "Error starting training")
    })
    @PostMapping("/{portfolioId}/train")
    public ResponseEntity<?> trainPortfolioModels(
            @Parameter(description = "Portfolio ID", required = true)
            @PathVariable String portfolioId,

            @Parameter(description = "Use sample data for faster training (for testing)")
            @RequestParam(defaultValue = "false") boolean useSampleData) {

        String currentUserId = getCurrentUserId();

        try {
            Map<String, Object> result = portfolioMLService.trainModelsForPortfolio(portfolioId, useSampleData);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @Operation(
            summary = "Get ML-based recommendations",
            description = "Generate portfolio recommendations using trained neural network models"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recommendations generated successfully"),
            @ApiResponse(responseCode = "400", description = "ML models not trained yet"),
            @ApiResponse(responseCode = "404", description = "Portfolio not found"),
            @ApiResponse(responseCode = "403", description = "Unauthorized access to portfolio"),
            @ApiResponse(responseCode = "500", description = "Error generating recommendations")
    })
    @GetMapping("/{portfolioId}/recommendations")
    public ResponseEntity<?> getPersonalizedRecommendations(
            @Parameter(description = "Portfolio ID", required = true)
            @PathVariable String portfolioId,

            @Parameter(description = "Risk tolerance (0.0-1.0 where 0.0 is conservative and 1.0 is aggressive)")
            @RequestParam(defaultValue = "0.5") double riskTolerance,

            @Parameter(description = "Whether to expand universe and consider new stocks")
            @RequestParam(defaultValue = "false") boolean expandUniverse) {

        String currentUserId = getCurrentUserId();

        try {
            Map<String, Object> mlStatus = portfolioMLService.getPortfolioMLStatus(portfolioId);
            Long modelsReady = (Long) mlStatus.get("modelsReady");
            Integer totalStocks = (Integer) mlStatus.get("totalStocks");

            if (modelsReady < totalStocks) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", String.format("ML models not ready. %d of %d models trained.", modelsReady, totalStocks));
                response.put("mlStatus", mlStatus);
                return ResponseEntity.badRequest().body(response);
            }

            Map<String, Object> recommendations = portfolioMLService.generatePersonalizedRecommendations(
                    portfolioId, riskTolerance, expandUniverse);

            return ResponseEntity.ok(recommendations);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }
}