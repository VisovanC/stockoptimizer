package com.cv.stockoptimizer.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * Configuration for OpenAPI documentation
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI stockOptimizerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Stock Optimizer API")
                        .description("API for stock portfolio optimization with AI capabilities")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Stock Optimizer Team")
                                .email("support@stockoptimizer.com")
                                .url("https://www.stockoptimizer.com"))
                        .license(new License()
                                .name("Private License")
                                .url("https://www.stockoptimizer.com/license")))
                .components(new Components()
                        .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"))
                .tags(Arrays.asList(
                        new Tag().name("Authentication").description("Authentication operations"),
                        new Tag().name("Portfolios").description("Portfolio management operations"),
                        new Tag().name("Stocks").description("Stock data operations"),
                        new Tag().name("AI Portfolio").description("AI portfolio optimization"),
                        new Tag().name("AI Metrics").description("AI recommendation performance tracking"),
                        new Tag().name("Portfolio History").description("Portfolio change history"),
                        new Tag().name("Health").description("Health check endpoints")
                ));
    }
}