package com.cv.stockoptimizer.service.scheduler;

import com.cv.stockoptimizer.model.entity.Portfolio;
import com.cv.stockoptimizer.repository.PortfolioRepository;
import com.cv.stockoptimizer.service.optimization.AIPortfolioUpgraderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class AIRecommendationScheduler {

    private static final Logger logger = LoggerFactory.getLogger(AIRecommendationScheduler.class);

    private final PortfolioRepository portfolioRepository;
    private final AIPortfolioUpgraderService aiPortfolioUpgraderService;

    @Autowired
    public AIRecommendationScheduler(
            PortfolioRepository portfolioRepository,
            AIPortfolioUpgraderService aiPortfolioUpgraderService) {
        this.portfolioRepository = portfolioRepository;
        this.aiPortfolioUpgraderService = aiPortfolioUpgraderService;
    }

    /**
     * Daily update of AI recommendations for all portfolios
     * Runs at 1:00 AM every day
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void updateAllPortfolioRecommendations() {
        logger.info("Starting scheduled portfolio recommendation updates");

        List<Portfolio> allPortfolios = portfolioRepository.findAll();
        int count = 0;

        for (Portfolio portfolio : allPortfolios) {
            try {
                // Use default settings
                aiPortfolioUpgraderService.generatePortfolioUpgrade(
                        portfolio.getId(), 0.5, true);
                count++;

                // Prevent overwhelming the system
                if (count % 10 == 0) {
                    Thread.sleep(5000); // Sleep for 5 seconds every 10 portfolios
                }
            } catch (Exception e) {
                logger.error("Error updating recommendations for portfolio {}: {}",
                        portfolio.getId(), e.getMessage());
            }
        }

        logger.info("Completed scheduled portfolio recommendation updates. Processed {} portfolios", count);
    }

    /**
     * Weekly optimization of top-performing portfolios
     * Runs at 2:00 AM every Sunday
     */
    @Scheduled(cron = "0 0 2 * * 0")
    public void optimizeTopPerformingPortfolios() {
        logger.info("Starting weekly optimization of top portfolios");

        // Find portfolios that haven't been optimized in the last week
        LocalDateTime oneWeekAgo = LocalDateTime.now().minusWeeks(1);
        List<Portfolio> candidates = portfolioRepository.findByLastOptimizedAtBeforeOrLastOptimizedAtIsNull(oneWeekAgo);

        // Sort by total value (descending)
        candidates.sort((p1, p2) -> {
            Double v1 = p1.getTotalValue() != null ? p1.getTotalValue() : 0.0;
            Double v2 = p2.getTotalValue() != null ? p2.getTotalValue() : 0.0;
            return v2.compareTo(v1);
        });

        // Process top 20 portfolios
        int limit = Math.min(20, candidates.size());
        for (int i = 0; i < limit; i++) {
            Portfolio portfolio = candidates.get(i);
            try {
                // Generate recommendations
                portfolio.setOptimizationStatus("OPTIMIZING");
                portfolioRepository.save(portfolio);

                // Use default risk tolerance
                aiPortfolioUpgraderService.generatePortfolioUpgrade(
                        portfolio.getId(), 0.5, true);

                // Mark as optimized
                portfolio.setOptimizationStatus("OPTIMIZED");
                portfolio.setLastOptimizedAt(LocalDateTime.now());
                portfolioRepository.save(portfolio);

                logger.info("Optimized portfolio {}", portfolio.getId());

                // Prevent overwhelming the system
                Thread.sleep(10000); // Sleep for 10 seconds between portfolios
            } catch (Exception e) {
                logger.error("Error optimizing portfolio {}: {}",
                        portfolio.getId(), e.getMessage());

                // Mark as failed
                portfolio.setOptimizationStatus("OPTIMIZATION_FAILED");
                portfolioRepository.save(portfolio);
            }
        }

        logger.info("Completed weekly portfolio optimization. Processed {} portfolios", limit);
    }
}