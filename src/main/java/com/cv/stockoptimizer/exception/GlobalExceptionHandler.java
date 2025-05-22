package com.cv.stockoptimizer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AIPortfolioException.class)
    public ResponseEntity<Map<String, Object>> handleAIPortfolioException(AIPortfolioException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", ex.getMessage());

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        // Determine appropriate status code based on exception type
        if (ex instanceof AIPortfolioException.InsufficientDataException) {
            status = HttpStatus.BAD_REQUEST;
            response.put("errorType", "INSUFFICIENT_DATA");
        } else if (ex instanceof AIPortfolioException.InvalidAllocationException) {
            status = HttpStatus.BAD_REQUEST;
            response.put("errorType", "INVALID_ALLOCATION");
        } else if (ex instanceof AIPortfolioException.OptimizationFailedException) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            response.put("errorType", "OPTIMIZATION_FAILED");
        } else if (ex instanceof AIPortfolioException.PredictionFailedException) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            response.put("errorType", "PREDICTION_FAILED");
        }

        return new ResponseEntity<>(response, status);
    }
}