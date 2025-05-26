package com.cv.stockoptimizer.service.data;

import com.cv.stockoptimizer.model.entity.StockData;
import com.cv.stockoptimizer.repository.StockDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import yahoofinance.YahooFinance;
import yahoofinance.histquotes.HistoricalQuote;
import yahoofinance.histquotes.Interval;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MarketDataCollectorService {

    private final StockDataRepository stockDataRepository;
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_DELAY_MS = 2000; // 2 seconds initial delay
    private static final long MAX_DELAY_MS = 30000; // 30 seconds max delay

    @Autowired
    public MarketDataCollectorService(StockDataRepository stockDataRepository) {
        this.stockDataRepository = stockDataRepository;
    }

    public List<StockData> fetchHistoricalData(String symbol, LocalDate from, LocalDate to) throws IOException {
        IOException lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                // Add delay to avoid rate limiting
                if (attempt > 1) {
                    long delay = Math.min(INITIAL_DELAY_MS * (long) Math.pow(2, attempt - 1), MAX_DELAY_MS);
                    System.out.println("Waiting " + delay + "ms before retry attempt " + attempt + " for " + symbol);
                    Thread.sleep(delay);
                } else {
                    // Even on first attempt, add a small delay
                    Thread.sleep(1000);
                }

                System.out.println("Fetching data for " + symbol + " (attempt " + attempt + "/" + MAX_RETRIES + ")");

                Calendar fromDate = Calendar.getInstance();
                fromDate.setTime(java.sql.Date.valueOf(from));

                Calendar toDate = Calendar.getInstance();
                toDate.setTime(java.sql.Date.valueOf(to));

                yahoofinance.Stock stock = YahooFinance.get(symbol, fromDate, toDate, Interval.DAILY);

                if (stock == null) {
                    throw new IOException("Failed to fetch data for symbol: " + symbol);
                }

                List<HistoricalQuote> quotes = stock.getHistory();

                if (quotes == null || quotes.isEmpty()) {
                    System.out.println("No historical data returned for " + symbol);

                    // Check if we have existing data in the database
                    List<StockData> existingData = stockDataRepository
                            .findBySymbolAndDateBetweenOrderByDateAsc(symbol, from, to);

                    if (!existingData.isEmpty()) {
                        System.out.println("Using " + existingData.size() + " existing data points from database for " + symbol);
                        return existingData;
                    }

                    return Collections.emptyList();
                }

                List<StockData> stockDataList = quotes.stream()
                        .filter(quote -> quote.getClose() != null)
                        .map(quote -> new StockData(
                                symbol,
                                quote.getDate().getTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
                                quote.getOpen() != null ? quote.getOpen().doubleValue() : 0,
                                quote.getHigh() != null ? quote.getHigh().doubleValue() : 0,
                                quote.getLow() != null ? quote.getLow().doubleValue() : 0,
                                quote.getClose().doubleValue(),
                                quote.getVolume() != null ? quote.getVolume().longValue() : 0,
                                quote.getAdjClose() != null ? quote.getAdjClose().doubleValue() : 0
                        ))
                        .collect(Collectors.toList());

                System.out.println("Successfully fetched " + stockDataList.size() + " data points for " + symbol);
                return stockDataList;

            } catch (IOException e) {
                lastException = e;

                if (e.getMessage() != null && e.getMessage().contains("429")) {
                    System.err.println("Rate limited by Yahoo Finance for " + symbol + ". Attempt " + attempt + "/" + MAX_RETRIES);
                } else {
                    System.err.println("Error fetching data for " + symbol + " (attempt " + attempt + "): " + e.getMessage());
                }

                // Check if we have existing data in the database as fallback
                if (attempt == MAX_RETRIES) {
                    List<StockData> existingData = stockDataRepository
                            .findBySymbolAndDateBetweenOrderByDateAsc(symbol, from, to);

                    if (!existingData.isEmpty()) {
                        System.out.println("Using " + existingData.size() + " existing data points from database for " + symbol);
                        return existingData;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting to retry", e);
            }
        }

        // If all retries failed, throw the last exception
        throw new IOException("Failed to fetch data for " + symbol + " after " + MAX_RETRIES + " attempts", lastException);
    }

    public Map<String, List<StockData>> fetchHistoricalDataBatch(List<String> symbols, LocalDate from, LocalDate to) throws IOException {
        Map<String, List<StockData>> result = new HashMap<>();

        for (int i = 0; i < symbols.size(); i++) {
            String symbol = symbols.get(i);
            try {
                // Add delay between different symbols to avoid rate limiting
                if (i > 0) {
                    Thread.sleep(3000); // 3 seconds between different symbols
                }

                List<StockData> data = fetchHistoricalData(symbol, from, to);
                result.put(symbol, data);
            } catch (IOException e) {
                System.err.println("Error fetching data for " + symbol + ": " + e.getMessage());
                // Continue with other symbols
                result.put(symbol, new ArrayList<>());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Batch collection interrupted");
                break;
            }
        }

        return result;
    }

    /**
     * Fallback method to generate sample data for testing when Yahoo Finance is unavailable
     */
    public List<StockData> generateSampleData(String symbol, LocalDate from, LocalDate to) {
        System.out.println("Generating sample data for " + symbol + " (Yahoo Finance unavailable)");

        List<StockData> sampleData = new ArrayList<>();
        LocalDate currentDate = from;

        // Starting prices for different symbols
        Map<String, Double> basePrices = new HashMap<>();
        basePrices.put("AAPL", 150.0);
        basePrices.put("MSFT", 300.0);
        basePrices.put("GOOGL", 2500.0);
        basePrices.put("AMZN", 3000.0);
        basePrices.put("TSLA", 800.0);

        double basePrice = basePrices.getOrDefault(symbol, 100.0);
        Random random = new Random(symbol.hashCode()); // Consistent random data for same symbol

        while (!currentDate.isAfter(to)) {
            // Skip weekends
            if (currentDate.getDayOfWeek().getValue() >= 6) {
                currentDate = currentDate.plusDays(1);
                continue;
            }

            // Generate realistic price movement
            double dailyChange = (random.nextDouble() - 0.5) * 0.02; // +/- 2% daily change
            basePrice = basePrice * (1 + dailyChange);

            double open = basePrice * (1 + (random.nextDouble() - 0.5) * 0.01);
            double high = Math.max(open, basePrice) * (1 + random.nextDouble() * 0.01);
            double low = Math.min(open, basePrice) * (1 - random.nextDouble() * 0.01);
            double close = basePrice;
            long volume = 1000000 + random.nextInt(5000000);

            StockData data = new StockData(symbol, currentDate, open, high, low, close, volume, close);
            sampleData.add(data);

            currentDate = currentDate.plusDays(1);
        }

        System.out.println("Generated " + sampleData.size() + " sample data points for " + symbol);
        return sampleData;
    }

    public void collectInitialData(int years) throws IOException {
        List<String> symbols = Arrays.asList(
                "AAPL", "MSFT", "GOOGL", "AMZN", "TSLA"
        );

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusYears(years);

        for (String symbol : symbols) {
            try {
                List<StockData> data = fetchHistoricalData(symbol, startDate, endDate);

                // If Yahoo Finance fails, use sample data
                if (data.isEmpty()) {
                    System.out.println("Yahoo Finance unavailable, using sample data for " + symbol);
                    data = generateSampleData(symbol, startDate, endDate);
                }

                if (!data.isEmpty()) {
                    stockDataRepository.saveAll(data);
                    System.out.println("Saved " + data.size() + " records for " + symbol);
                }
            } catch (Exception e) {
                System.err.println("Failed to collect data for " + symbol + ", using sample data");
                List<StockData> sampleData = generateSampleData(symbol, startDate, endDate);
                stockDataRepository.saveAll(sampleData);
            }
        }
    }

    @Scheduled(cron = "0 0 18 * * MON-FRI", zone = "America/New_York")
    public void updateDailyData() {
        Set<String> symbols = stockDataRepository.findDistinctSymbols();

        if (symbols.isEmpty()) {
            try {
                collectInitialData(2);
            } catch (IOException e) {
                System.err.println("Failed to collect initial data: " + e.getMessage());
            }
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        for (String symbol : symbols) {
            try {
                List<StockData> latestData = fetchHistoricalData(symbol, yesterday, today);
                if (!latestData.isEmpty()) {
                    stockDataRepository.saveAll(latestData);
                }
            } catch (IOException e) {
                System.err.println("Error updating data for " + symbol + ": " + e.getMessage());
            }
        }
    }
}