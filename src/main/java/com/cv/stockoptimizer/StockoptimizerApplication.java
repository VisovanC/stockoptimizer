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



}

