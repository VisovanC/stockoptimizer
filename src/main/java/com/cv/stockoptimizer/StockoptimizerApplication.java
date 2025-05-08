package com.cv.stockoptimizer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@SpringBootApplication
public class StockoptimizerApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockoptimizerApplication.class, args);
    }

}



