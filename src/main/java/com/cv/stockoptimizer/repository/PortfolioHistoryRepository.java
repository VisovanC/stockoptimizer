package com.cv.stockoptimizer.repository;

import com.cv.stockoptimizer.model.entity.PortfolioHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PortfolioHistoryRepository extends MongoRepository<PortfolioHistory, String> {

    List<PortfolioHistory> findByPortfolioIdOrderByChangeDateDesc(String portfolioId);

    List<PortfolioHistory> findByPortfolioIdAndChangeTypeOrderByChangeDateDesc(
            String portfolioId, String changeType);

    List<PortfolioHistory> findByChangeDateBetweenOrderByChangeDateDesc(
            LocalDateTime startDate, LocalDateTime endDate);

    List<PortfolioHistory> findByUserIdAndChangeDateBetweenOrderByChangeDateDesc(
            String userId, LocalDateTime startDate, LocalDateTime endDate);

    List<PortfolioHistory> findByChangeSource(String changeSource);
}