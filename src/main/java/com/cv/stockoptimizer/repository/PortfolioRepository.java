package com.cv.stockoptimizer.repository;

import com.cv.stockoptimizer.model.entity.Portfolio;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PortfolioRepository extends MongoRepository<Portfolio, String> {
    List<Portfolio> findByUserId(String userId);
    List<Portfolio> findByLastOptimizedAtBeforeOrLastOptimizedAtIsNull(LocalDateTime date);
    void deleteByUserIdAndId(String userId, String id);
}