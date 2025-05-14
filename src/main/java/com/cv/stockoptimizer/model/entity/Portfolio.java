package com.cv.stockoptimizer.model.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "portfolios")
public class Portfolio {
    @Id
    private String id;
    private String userId;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<PortfolioStock> stocks = new ArrayList<>();
    private Double totalValue;
    private Double totalReturn;
    private Double totalReturnPercentage;
    private Double riskScore;
    private String optimizationStatus; // "NOT_OPTIMIZED", "OPTIMIZING", "OPTIMIZED"
    private LocalDateTime lastOptimizedAt;

    public Portfolio() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Portfolio(String userId, String name, String description) {
        this.userId = userId;
        this.name = name;
        this.description = description;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.optimizationStatus = "NOT_OPTIMIZED";
    }

    public static class PortfolioStock {
        private String symbol;
        private String companyName;
        private int shares;
        private double entryPrice;
        private LocalDateTime entryDate;
        private double currentPrice;
        private double weight;
        private double returnValue;
        private double returnPercentage;

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public String getCompanyName() {
            return companyName;
        }

        public void setCompanyName(String companyName) {
            this.companyName = companyName;
        }

        public int getShares() {
            return shares;
        }

        public void setShares(int shares) {
            this.shares = shares;
        }

        public double getEntryPrice() {
            return entryPrice;
        }

        public void setEntryPrice(double entryPrice) {
            this.entryPrice = entryPrice;
        }

        public LocalDateTime getEntryDate() {
            return entryDate;
        }

        public void setEntryDate(LocalDateTime entryDate) {
            this.entryDate = entryDate;
        }

        public double getCurrentPrice() {
            return currentPrice;
        }

        public void setCurrentPrice(double currentPrice) {
            this.currentPrice = currentPrice;
        }

        public double getWeight() {
            return weight;
        }

        public void setWeight(double weight) {
            this.weight = weight;
        }

        public double getReturnValue() {
            return returnValue;
        }

        public void setReturnValue(double returnValue) {
            this.returnValue = returnValue;
        }

        public double getReturnPercentage() {
            return returnPercentage;
        }

        public void setReturnPercentage(double returnPercentage) {
            this.returnPercentage = returnPercentage;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<PortfolioStock> getStocks() {
        return stocks;
    }

    public void setStocks(List<PortfolioStock> stocks) {
        this.stocks = stocks;
    }

    public Double getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(Double totalValue) {
        this.totalValue = totalValue;
    }

    public Double getTotalReturn() {
        return totalReturn;
    }

    public void setTotalReturn(Double totalReturn) {
        this.totalReturn = totalReturn;
    }

    public Double getTotalReturnPercentage() {
        return totalReturnPercentage;
    }

    public void setTotalReturnPercentage(Double totalReturnPercentage) {
        this.totalReturnPercentage = totalReturnPercentage;
    }

    public Double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Double riskScore) {
        this.riskScore = riskScore;
    }

    public String getOptimizationStatus() {
        return optimizationStatus;
    }

    public void setOptimizationStatus(String optimizationStatus) {
        this.optimizationStatus = optimizationStatus;
    }

    public LocalDateTime getLastOptimizedAt() {
        return lastOptimizedAt;
    }

    public void setLastOptimizedAt(LocalDateTime lastOptimizedAt) {
        this.lastOptimizedAt = lastOptimizedAt;
    }
}