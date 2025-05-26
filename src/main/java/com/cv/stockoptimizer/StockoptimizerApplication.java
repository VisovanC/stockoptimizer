package com.cv.stockoptimizer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class StockoptimizerApplication {

    public static void main(String[] args) {
        System.out.println("Starting Stock Optimizer Application...");
        SpringApplication.run(StockoptimizerApplication.class, args);
        System.out.println("Stock Optimizer Application started successfully!");
    }
}