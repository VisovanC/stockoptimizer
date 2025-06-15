package com.cv.stockoptimizer.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
public class StartupInitializer {

    @Bean
    CommandLineRunner init() {
        return args -> {
            System.out.println("=== Stock Portfolio Optimizer Starting ===");

            File modelsDir = new File("./models");
            if (!modelsDir.exists()) {
                boolean created = modelsDir.mkdirs();
                System.out.println("Models directory created: " + created);
            } else {
                System.out.println("Models directory exists: " + modelsDir.getAbsolutePath());
            }

            File[] modelFiles = modelsDir.listFiles((dir, name) -> name.endsWith(".eg"));
            if (modelFiles != null && modelFiles.length > 0) {
                System.out.println("Found " + modelFiles.length + " existing models:");
                for (File file : modelFiles) {
                    System.out.println("  - " + file.getName());
                }
            } else {
                System.out.println("No existing models found");
            }

            System.out.println("=== Startup Complete ===");
            System.out.println("Access the application at:");
            System.out.println("  - API: http://localhost:8080/");
            System.out.println("  - ML Control Panel: Open mlcontrol.html in your browser");
            System.out.println("  - Test Page: Open test.html in your browser");
        };
    }
}