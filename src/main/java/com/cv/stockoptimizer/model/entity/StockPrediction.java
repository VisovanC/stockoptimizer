package com.cv.stockoptimizer.model.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
@Document(collection = "stock_predictions")
public class StockPrediction {
    @Id
    private String id;

    private String symbol;
    private LocalDate predictionDate;
    private LocalDate targetDate;
    private double currentPrice;
    private double predictedPrice;
    private double predictedChangePercentage;
    private double confidenceScore;
    private Double actualPrice;
    private Double actualChangePercentage;
    private boolean verified;
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public LocalDate getPredictionDate() {
        return predictionDate;
    }

    public void setPredictionDate(LocalDate predictionDate) {
        this.predictionDate = predictionDate;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(double currentPrice) {
        this.currentPrice = currentPrice;
    }

    public double getPredictedPrice() {
        return predictedPrice;
    }

    public void setPredictedPrice(double predictedPrice) {
        this.predictedPrice = predictedPrice;
    }

    public double getPredictedChangePercentage() {
        return predictedChangePercentage;
    }

    public void setPredictedChangePercentage(double predictedChangePercentage) {
        this.predictedChangePercentage = predictedChangePercentage;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public Double getActualPrice() {
        return actualPrice;
    }

    public void setActualPrice(Double actualPrice) {
        this.actualPrice = actualPrice;
    }

    public Double getActualChangePercentage() {
        return actualChangePercentage;
    }

    public void setActualChangePercentage(Double actualChangePercentage) {
        this.actualChangePercentage = actualChangePercentage;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }
}
