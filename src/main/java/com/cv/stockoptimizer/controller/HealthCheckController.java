package com.cv.stockoptimizer.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthCheckController {

    private final MongoTemplate mongoTemplate;

    @Autowired
    public HealthCheckController(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @GetMapping("/mongodb")
    public ResponseEntity<Map<String, Object>> checkMongoDBConnection() {
        Map<String, Object> response = new HashMap<>();

        try {
            // Try to perform a simple operation
            String dbName = mongoTemplate.getDb().getName();

            response.put("status", "UP");
            response.put("database", dbName);
            response.put("message", "MongoDB connection successful");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "DOWN");
            response.put("error", e.getMessage());

            return ResponseEntity.status(503).body(response);
        }
    }
}