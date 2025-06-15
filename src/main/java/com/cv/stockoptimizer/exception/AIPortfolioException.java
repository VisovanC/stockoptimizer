package com.cv.stockoptimizer.exception;

public class AIPortfolioException extends RuntimeException {

    public AIPortfolioException(String message) {
        super(message);
    }

    public AIPortfolioException(String message, Throwable cause) {
        super(message, cause);
    }


    public static class InsufficientDataException extends AIPortfolioException {
        public InsufficientDataException(String symbol) {
            super("Insufficient historical data for stock: " + symbol);
        }
    }

    public static class OptimizationFailedException extends AIPortfolioException {
        public OptimizationFailedException(String portfolioId, String reason) {
            super("Portfolio optimization failed for ID: " + portfolioId + ". Reason: " + reason);
        }
    }

    public static class InvalidAllocationException extends AIPortfolioException {
        public InvalidAllocationException(String message) {
            super(message);
        }
    }

    public static class PredictionFailedException extends AIPortfolioException {
        public PredictionFailedException(String symbol, Throwable cause) {
            super("Failed to generate prediction for stock: " + symbol, cause);
        }
    }
}