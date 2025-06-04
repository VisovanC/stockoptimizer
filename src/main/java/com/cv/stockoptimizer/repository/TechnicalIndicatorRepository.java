package com.cv.stockoptimizer.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.cv.stockoptimizer.model.entity.TechnicalIndicator;

@Repository
public interface TechnicalIndicatorRepository extends MongoRepository<TechnicalIndicator, String> {
    List<TechnicalIndicator> findByUserIdAndSymbolOrderByDateDesc(String userId, String symbol);
    List<TechnicalIndicator> findByUserIdAndSymbolAndDateBetweenOrderByDateAsc(String userId, String symbol, LocalDate from, LocalDate to);

    // Add this method for backward compatibility (without userId)
    List<TechnicalIndicator> findBySymbolAndDateBetweenOrderByDateAsc(String symbol, LocalDate from, LocalDate to);

    void deleteByUserIdAndSymbol(String userId, String symbol);

    void deleteByUserIdAndSymbolAndDateBetween(
            String userId, String symbol, LocalDate from, LocalDate to);

    Optional<TechnicalIndicator> findByUserIdAndSymbolAndDate(
            String userId, String symbol, LocalDate date);
}