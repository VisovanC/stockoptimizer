package com.cv.stockoptimizer.service.history;

import com.cv.stockoptimizer.model.entity.Portfolio;
import com.cv.stockoptimizer.model.entity.PortfolioHistory;
import com.cv.stockoptimizer.repository.PortfolioHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PortfolioHistoryService {

    private final PortfolioHistoryRepository historyRepository;

    @Autowired
    public PortfolioHistoryService(PortfolioHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }
    public PortfolioHistory recordAiRecommendation(
            Portfolio portfolio,
            Map<String, Double> newAllocations,
            double riskTolerance,
            String aiVersion) {

        PortfolioHistory history = new PortfolioHistory();
        history.setPortfolioId(portfolio.getId());
        history.setUserId(portfolio.getUserId());
        history.setChangeType("AI_RECOMMENDATION");
        history.setChangeDate(LocalDateTime.now());
        history.setChangeSource("AI");
        history.setAiModelVersion(aiVersion);
        history.setRiskTolerance(riskTolerance);

        Map<String, Double> previousAllocations = new HashMap<>();
        double totalValue = portfolio.getTotalValue() != null ? portfolio.getTotalValue() : 0;

        if (totalValue > 0) {
            for (Portfolio.PortfolioStock stock : portfolio.getStocks()) {
                double value = stock.getCurrentPrice() * stock.getShares();
                double weight = value / totalValue;
                previousAllocations.put(stock.getSymbol(), weight);
            }
        }

        history.setPreviousAllocations(previousAllocations);
        history.setNewAllocations(newAllocations);
        history.setPreviousValue(totalValue);

        if (riskTolerance < 0.33) {
            history.setChangeReason("Conservative risk-optimized AI portfolio");
        } else if (riskTolerance < 0.67) {
            history.setChangeReason("Balanced AI portfolio optimization");
        } else {
            history.setChangeReason("Aggressive return-optimized AI portfolio");
        }

        return historyRepository.save(history);
    }

    public PortfolioHistory recordUserUpdate(
            Portfolio portfolio,
            Map<String, Double> newAllocations) {

        PortfolioHistory history = new PortfolioHistory();
        history.setPortfolioId(portfolio.getId());
        history.setUserId(portfolio.getUserId());
        history.setChangeType("UPDATE");
        history.setChangeDate(LocalDateTime.now());
        history.setChangeSource("USER");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUser = authentication != null ? authentication.getName() : "unknown";

        Map<String, Double> previousAllocations = new HashMap<>();
        double totalValue = portfolio.getTotalValue() != null ? portfolio.getTotalValue() : 0;

        if (totalValue > 0) {
            for (Portfolio.PortfolioStock stock : portfolio.getStocks()) {
                double value = stock.getCurrentPrice() * stock.getShares();
                double weight = value / totalValue;
                previousAllocations.put(stock.getSymbol(), weight);
            }
        }

        history.setPreviousAllocations(previousAllocations);
        history.setNewAllocations(newAllocations);
        history.setPreviousValue(totalValue);
        history.setChangeReason("Manual update by user: " + currentUser);

        return historyRepository.save(history);
    }

    public PortfolioHistory recordScheduledRebalance(
            Portfolio portfolio,
            Map<String, Double> newAllocations) {

        PortfolioHistory history = new PortfolioHistory();
        history.setPortfolioId(portfolio.getId());
        history.setUserId(portfolio.getUserId());
        history.setChangeType("REBALANCE");
        history.setChangeDate(LocalDateTime.now());
        history.setChangeSource("SCHEDULED");

        Map<String, Double> previousAllocations = new HashMap<>();
        double totalValue = portfolio.getTotalValue() != null ? portfolio.getTotalValue() : 0;

        if (totalValue > 0) {
            for (Portfolio.PortfolioStock stock : portfolio.getStocks()) {
                double value = stock.getCurrentPrice() * stock.getShares();
                double weight = value / totalValue;
                previousAllocations.put(stock.getSymbol(), weight);
            }
        }

        history.setPreviousAllocations(previousAllocations);
        history.setNewAllocations(newAllocations);
        history.setPreviousValue(totalValue);
        history.setChangeReason("Scheduled portfolio rebalance");

        return historyRepository.save(history);
    }

    public List<PortfolioHistory> getPortfolioHistory(String portfolioId) {
        return historyRepository.findByPortfolioIdOrderByChangeDateDesc(portfolioId);
    }

    public List<PortfolioHistory> getAiRecommendationHistory(String portfolioId) {
        return historyRepository.findByPortfolioIdAndChangeTypeOrderByChangeDateDesc(
                portfolioId, "AI_RECOMMENDATION");
    }
    public List<PortfolioHistory> getRecentChanges(int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        return historyRepository.findByChangeDateBetweenOrderByChangeDateDesc(
                startDate, LocalDateTime.now());
    }
}