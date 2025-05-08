package com.cv.stockoptimizer.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

import com.cv.stockoptimizer.model.entity.TechnicalIndicator;

@Repository
public interface TechnicalIndicatorRepository extends MongoRepository<TechnicalIndicator, String> {

    List<TechnicalIndicator> findBySymbolOrderByDateDesc(String symbol);

    List<TechnicalIndicator> findBySymbolAndDateBetweenOrderByDateAsc(String symbol, LocalDate from, LocalDate to);
}