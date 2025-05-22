package com.cv.stockoptimizer.model.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Entity to store portfolio change history for audit and performance tracking
 */
@Document(collection = "portfolio_history")
public class PortfolioHistory extends AuditableEntity {

    @Id
    private String id;

    private String portfolioId;
    private String userId;
    private String changeType; // "CREATION", "UPDATE", "AI_RECOMMENDATION", "REBALANCE"
    private LocalDateTime changeDate;
    private Map<String, Double> previousAllocations = new HashMap<>();
    private Map<String, Double> newAllocations = new HashMap<>();
    private Double previousValue;
    private Double newValue;
    private String aiModelVersion;
    private Double riskTolerance;
    private String changeReason;
    private String changeSource; // "USER", "AI", "SCHEDULED"

    public PortfolioHistory() {
        this.changeDate = LocalDateTime.now();
    }

    // Getters and setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPortfolioId() {
        return portfolioId;
    }

    public void setPortfolioId(String portfolioId) {
        this.portfolioId = portfolioId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public LocalDateTime getChangeDate() {
        return changeDate;
    }

    public void setChangeDate(LocalDateTime changeDate) {
        this.changeDate = changeDate;
    }

    public Map<String, Double> getPreviousAllocations() {
        return previousAllocations;
    }

    public void setPreviousAllocations(Map<String, Double> previousAllocations) {
        this.previousAllocations = previousAllocations;
    }

    public Map<String, Double> getNewAllocations() {
        return newAllocations;
    }

    public void setNewAllocations(Map<String, Double> newAllocations) {
        this.newAllocations = newAllocations;
    }

    public Double getPreviousValue() {
        return previousValue;
    }

    public void setPreviousValue(Double previousValue) {
        this.previousValue = previousValue;
    }

    public Double getNewValue() {
        return newValue;
    }

    public void setNewValue(Double newValue) {
        this.newValue = newValue;
    }

    public String getAiModelVersion() {
        return aiModelVersion;
    }

    public void setAiModelVersion(String aiModelVersion) {
        this.aiModelVersion = aiModelVersion;
    }

    public Double getRiskTolerance() {
        return riskTolerance;
    }

    public void setRiskTolerance(Double riskTolerance) {
        this.riskTolerance = riskTolerance;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }

    public String getChangeSource() {
        return changeSource;
    }

    public void setChangeSource(String changeSource) {
        this.changeSource = changeSource;
    }
}