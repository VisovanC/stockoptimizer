package com.cv.stockoptimizer.repository;

import com.cv.stockoptimizer.model.entity.StockData;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Repository
public interface StockDataRepository extends MongoRepository<StockData, String> {
    List<StockData> findByUserIdAndSymbolOrderByDateDesc(String userId, String symbol);
    List<StockData> findByUserIdAndSymbolAndDateBetweenOrderByDateAsc(String userId, String symbol, LocalDate from, LocalDate to);

    List<StockData> findBySymbolAndDateBetweenOrderByDateAsc(String symbol, LocalDate from, LocalDate to);

    @Query(value = "{'userId': ?0}", fields = "{ 'symbol' : 1 }")
    Set<String> findDistinctSymbolsByUserId(String userId);

    @Query(value = "{}", fields = "{ 'symbol' : 1 }")
    Set<String> findDistinctSymbols();

    void deleteByUserIdAndSymbol(String userId, String symbol);

    boolean existsByUserIdAndSymbolAndDateBetween(
            String userId, String symbol, LocalDate from, LocalDate to);

    void deleteByUserIdAndSymbolAndDateBetween(
            String userId, String symbol, LocalDate from, LocalDate to);
}