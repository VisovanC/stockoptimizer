package com.cv.stockoptimizer.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Component
public class MongoDBIndexInitializer {

    private final MongoTemplate mongoTemplate;

    @Autowired
    public MongoDBIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void initIndexes() {
        try {
            System.out.println("Initializing MongoDB indexes...");

            mongoTemplate.indexOps("stock_data").ensureIndex(
                    new Index().on("symbol", Sort.Direction.ASC)
                            .on("date", Sort.Direction.ASC)
                            .unique());

            mongoTemplate.indexOps("technical_indicators").ensureIndex(
                    new Index().on("symbol", Sort.Direction.ASC)
                            .on("date", Sort.Direction.ASC)
                            .unique());

            mongoTemplate.indexOps("stock_predictions").ensureIndex(
                    new Index().on("symbol", Sort.Direction.ASC)
                            .on("predictionDate", Sort.Direction.DESC));

            mongoTemplate.indexOps("users").ensureIndex(
                    new Index().on("username", Sort.Direction.ASC)
                            .unique());
            mongoTemplate.indexOps("users").ensureIndex(
                    new Index().on("email", Sort.Direction.ASC)
                            .unique());

            mongoTemplate.indexOps("portfolios").ensureIndex(
                    new Index().on("userId", Sort.Direction.ASC));

            System.out.println("Indexes initialized successfully.");
        } catch (Exception e) {
            System.err.println("Error initializing MongoDB indexes: " + e.getMessage());
            e.printStackTrace();
        }
    }
}