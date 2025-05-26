package com.cv.stockoptimizer.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class HomeController {

    @GetMapping("/")
    public Map<String, Object> home() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Welcome to Stock Portfolio Optimizer API");
        response.put("version", "1.0.0");
        response.put("status", "running");
        response.put("endpoints", Map.of(
                "health", "/api/health/mongodb",
                "ml", "/api/ml/status",
                "auth", "/api/auth/signin",
                "portfolios", "/api/portfolios",
                "stocks", "/api/stocks"
        ));
        return response;
    }

    @GetMapping("/hello")
    public Map<String, Object> hello() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Hello from Stock Optimizer!");
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
}