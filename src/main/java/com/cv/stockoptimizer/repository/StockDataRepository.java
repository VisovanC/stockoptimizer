package com.cv.stockoptimizer.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Repository
public interface StockDataRepository extends MongoRepository<com.cv.stockoptimizer.model.entity.StockData, String> {

    List<com.cv.stockoptimizer.model.entity.StockData> findBySymbolOrderByDateDesc(String symbol);

    List<com.cv.stockoptimizer.model.entity.StockData> findBySymbolAndDateBetweenOrderByDateAsc(String symbol, LocalDate from, LocalDate to);

    @Query(value = "{}", fields = "{ 'symbol' : 1 }")
    Set<String> findDistinctSymbols();
}
