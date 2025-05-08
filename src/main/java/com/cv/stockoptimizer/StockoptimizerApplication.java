package com.cv.stockoptimizer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class StockoptimizerApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockoptimizerApplication.class, args);
    }

    @Component
    public class MongoDBConnectionChecker implements CommandLineRunner {

        private final MongoTemplate mongoTemplate;

        @Autowired
        public MongoDBConnectionChecker(MongoTemplate mongoTemplate) {
            this.mongoTemplate = mongoTemplate;
        }

        @Override
        public void run(String... args) {
            try {
                System.out.println("Checking MongoDB connection with Spring's MongoTemplate...");
                String dbName = mongoTemplate.getDb().getName();
                System.out.println("✅ Successfully connected to MongoDB: " + dbName);
                System.out.println("Collections in database:");
                for (String collection : mongoTemplate.getCollectionNames()) {
                    System.out.println(" - " + collection);
                }
            } catch (Exception e) {
                System.err.println("❌ Failed to connect to MongoDB with Spring: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}