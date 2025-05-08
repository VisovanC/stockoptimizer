package com.cv.stockoptimizer.repository;

import com.cv.stockoptimizer.model.entity.StockPrediction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface StockPredictionRepository extends MongoRepository<StockPrediction, String> {

    List<StockPrediction> findBySymbolOrderByPredictionDateDesc(String symbol);

    List<StockPrediction> findByTargetDateAndVerifiedFalse(LocalDate targetDate);

    List<StockPrediction> findBySymbolAndPredictionDateBetween(String symbol, LocalDate from, LocalDate to);
}