package com.cv.stockoptimizer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StockoptimizerApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockoptimizerApplication.class, args);
    }
}