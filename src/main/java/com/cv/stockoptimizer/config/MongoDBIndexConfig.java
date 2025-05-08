package com.cv.stockoptimizer.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

@Configuration
public class MongoDBIndexConfig {

    private final MongoTemplate mongoTemplate;

    @Autowired
    public MongoDBIndexConfig(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    // Change from void to String (or any other return type)
    @Bean
    public String initIndexes() {
        // Create index for StockData
        mongoTemplate.indexOps("stock_data").ensureIndex(
                new Index().on("symbol", Sort.Direction.ASC)
                        .on("date", Sort.Direction.ASC)
                        .unique());

        // Create index for TechnicalIndicator
        mongoTemplate.indexOps("technical_indicators").ensureIndex(
                new Index().on("symbol", Sort.Direction.ASC)
                        .on("date", Sort.Direction.ASC)
                        .unique());

        // Create index for StockPrediction
        mongoTemplate.indexOps("stock_predictions").ensureIndex(
                new Index().on("symbol", Sort.Direction.ASC)
                        .on("predictionDate", Sort.Direction.DESC));

        // Create index for User
        mongoTemplate.indexOps("users").ensureIndex(
                new Index().on("username", Sort.Direction.ASC)
                        .unique());
        mongoTemplate.indexOps("users").ensureIndex(
                new Index().on("email", Sort.Direction.ASC)
                        .unique());

        // Create index for Portfolio
        mongoTemplate.indexOps("portfolios").ensureIndex(
                new Index().on("userId", Sort.Direction.ASC));

        // Return a value (can be any descriptive string)
        return "MongoDB indexes initialized";
    }
}