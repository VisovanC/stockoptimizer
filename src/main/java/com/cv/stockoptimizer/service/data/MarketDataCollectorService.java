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
    private static final long INITIAL_DELAY_MS = 2000;
    private static final long MAX_DELAY_MS = 10000;
    private static final boolean PREFER_REAL_DATA = true; // Changed to prefer real data

    @Autowired
    public MarketDataCollectorService(StockDataRepository stockDataRepository) {
        this.stockDataRepository = stockDataRepository;
    }

    public List<StockData> fetchHistoricalData(String symbol, LocalDate from, LocalDate to) throws IOException {
        return fetchHistoricalData(symbol, from, to, "system");
    }

    public List<StockData> fetchHistoricalData(String symbol, LocalDate from, LocalDate to, String userId) throws IOException {
        // First check if user already has sufficient data
        List<StockData> existingData = stockDataRepository.findByUserIdAndSymbolAndDateBetweenOrderByDateAsc(userId, symbol, from, to);

        // If we have at least 100 data points, return them
        if (existingData.size() >= 100) {
            System.out.println("Using existing data for " + symbol + " - " + existingData.size() + " points");
            return existingData;
        }

        // Delete any partial data to avoid duplicates
        if (!existingData.isEmpty()) {
            System.out.println("Deleting " + existingData.size() + " partial data points for " + symbol);
            stockDataRepository.deleteByUserIdAndSymbolAndDateBetween(userId, symbol, from, to);
        }

        // Try Yahoo Finance first if we prefer real data
        if (PREFER_REAL_DATA) {
            try {
                List<StockData> yahooData = fetchFromYahooFinance(symbol, from, to, userId);
                if (!yahooData.isEmpty()) {
                    // Save the data
                    stockDataRepository.saveAll(yahooData);
                    return yahooData;
                }
            } catch (Exception e) {
                System.err.println("Yahoo Finance failed for " + symbol + ": " + e.getMessage());
                // Fall back to sample data
            }
        }

        // Use sample data as fallback
        System.out.println("Using sample data for " + symbol);
        List<StockData> sampleData = generateSampleData(symbol, from, to, userId);
        stockDataRepository.saveAll(sampleData);
        return sampleData;
    }

    private List<StockData> fetchFromYahooFinance(String symbol, LocalDate from, LocalDate to, String userId) throws IOException, InterruptedException {
        IOException lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 1) {
                    long delay = Math.min(INITIAL_DELAY_MS * (long) Math.pow(2, attempt - 1), MAX_DELAY_MS);
                    System.out.println("Waiting " + delay + "ms before retry attempt " + attempt);
                    Thread.sleep(delay);
                }

                System.out.println("Fetching from Yahoo Finance for " + symbol + " (attempt " + attempt + ")");

                Calendar fromDate = Calendar.getInstance();
                fromDate.setTime(java.sql.Date.valueOf(from));

                Calendar toDate = Calendar.getInstance();
                toDate.setTime(java.sql.Date.valueOf(to));

                yahoofinance.Stock stock = YahooFinance.get(symbol, fromDate, toDate, Interval.DAILY);

                if (stock == null || stock.getHistory() == null || stock.getHistory().isEmpty()) {
                    throw new IOException("No data returned from Yahoo Finance for " + symbol);
                }

                List<HistoricalQuote> quotes = stock.getHistory();
                List<StockData> stockDataList = quotes.stream()
                        .filter(quote -> quote.getClose() != null)
                        .map(quote -> {
                            StockData data = new StockData(
                                    userId,
                                    symbol,
                                    quote.getDate().getTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
                                    quote.getOpen() != null ? quote.getOpen().doubleValue() : 0,
                                    quote.getHigh() != null ? quote.getHigh().doubleValue() : 0,
                                    quote.getLow() != null ? quote.getLow().doubleValue() : 0,
                                    quote.getClose().doubleValue(),
                                    quote.getVolume() != null ? quote.getVolume().longValue() : 0
                            );
                            data.setAdjClose(quote.getAdjClose() != null ? quote.getAdjClose().doubleValue() : quote.getClose().doubleValue());
                            return data;
                        })
                        .collect(Collectors.toList());

                System.out.println("Successfully fetched " + stockDataList.size() + " data points from Yahoo Finance for " + symbol);
                return stockDataList;

            } catch (IOException | InterruptedException e) {
                lastException = new IOException(e.getMessage());
                System.err.println("Yahoo Finance error (attempt " + attempt + "): " + e.getMessage());
            }
        }

        throw lastException != null ? lastException : new IOException("Failed to fetch data from Yahoo Finance");
    }

    public Map<String, List<StockData>> fetchHistoricalDataBatch(List<String> symbols, LocalDate from, LocalDate to, String userId) throws IOException {
        Map<String, List<StockData>> result = new HashMap<>();

        for (String symbol : symbols) {
            try {
                List<StockData> data = fetchHistoricalData(symbol, from, to, userId);
                result.put(symbol, data);

                // Add delay between symbols to avoid rate limiting
                Thread.sleep(1000);
            } catch (Exception e) {
                System.err.println("Error fetching data for " + symbol + ": " + e.getMessage());
                // Use sample data on error
                List<StockData> sampleData = generateSampleData(symbol, from, to, userId);
                stockDataRepository.saveAll(sampleData);
                result.put(symbol, sampleData);
            }
        }

        return result;
    }

    public List<StockData> generateSampleData(String symbol, LocalDate from, LocalDate to) {
        return generateSampleData(symbol, from, to, "system");
    }

    public List<StockData> generateSampleData(String symbol, LocalDate from, LocalDate to, String userId) {
        System.out.println("Generating comprehensive sample data for " + symbol + " from " + from + " to " + to);

        List<StockData> sampleData = new ArrayList<>();
        LocalDate currentDate = from;

        // Base prices for common stocks (updated to be more realistic)
        Map<String, Double> basePrices = new HashMap<>();
        basePrices.put("AAPL", 180.0);
        basePrices.put("MSFT", 380.0);
        basePrices.put("GOOGL", 140.0);
        basePrices.put("AMZN", 170.0);
        basePrices.put("TSLA", 250.0);
        basePrices.put("META", 500.0);
        basePrices.put("NVDA", 850.0);
        basePrices.put("JPM", 180.0);
        basePrices.put("JNJ", 155.0);
        basePrices.put("V", 280.0);
        basePrices.put("WMT", 180.0);
        basePrices.put("PG", 165.0);
        basePrices.put("UNH", 530.0);
        basePrices.put("HD", 370.0);
        basePrices.put("MA", 460.0);

        double basePrice = basePrices.getOrDefault(symbol.toUpperCase(), 100.0);
        Random random = new Random(symbol.hashCode() + userId.hashCode());

        // Add realistic market trends
        double trendFactor = 1.0 + (random.nextDouble() - 0.4) * 0.3; // -10% to +20% overall trend

        while (!currentDate.isAfter(to)) {
            // Skip weekends
            if (currentDate.getDayOfWeek().getValue() >= 6) {
                currentDate = currentDate.plusDays(1);
                continue;
            }

            // Daily volatility between -3% and +3%
            double dailyChange = (random.nextDouble() - 0.5) * 0.06;

            // Apply trend
            double trendAdjustment = (trendFactor - 1.0) / 365.0; // Daily trend contribution
            basePrice = basePrice * (1 + dailyChange + trendAdjustment);

            // Ensure price doesn't go negative
            basePrice = Math.max(basePrice, 10.0);

            // Generate OHLC data
            double open = basePrice * (1 + (random.nextDouble() - 0.5) * 0.02);
            double high = Math.max(open, basePrice) * (1 + random.nextDouble() * 0.02);
            double low = Math.min(open, basePrice) * (1 - random.nextDouble() * 0.02);
            double close = basePrice;

            // Volume with some randomness
            long baseVolume = 10000000L;
            long volume = baseVolume + (long)(random.nextGaussian() * 2000000);
            volume = Math.max(volume, 100000L);

            StockData data = new StockData(userId, symbol, currentDate, open, high, low, close, volume);
            data.setAdjClose(close);
            sampleData.add(data);

            currentDate = currentDate.plusDays(1);
        }

        System.out.println("Generated " + sampleData.size() + " sample data points for " + symbol);
        return sampleData;
    }

    public void saveStockDataForUser(List<StockData> stockDataList, String userId, String symbol) {
        if (stockDataList.isEmpty()) return;

        // Delete existing data for this user and symbol to avoid duplicates
        stockDataRepository.deleteByUserIdAndSymbol(userId, symbol);

        // Save new data
        stockDataRepository.saveAll(stockDataList);
        System.out.println("Saved " + stockDataList.size() + " stock data records for user " + userId);
    }

    public void collectInitialData(int years) throws IOException {
        collectInitialData(years, "system");
    }

    public void collectInitialData(int years, String userId) throws IOException {
        List<String> symbols = Arrays.asList("AAPL", "MSFT", "GOOGL", "AMZN", "TSLA");

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusYears(years);

        for (String symbol : symbols) {
            try {
                List<StockData> data = fetchHistoricalData(symbol, startDate, endDate, userId);
                System.out.println("Collected " + data.size() + " records for " + symbol);
            } catch (Exception e) {
                System.err.println("Failed to collect data for " + symbol + ": " + e.getMessage());
            }
        }
    }

    @Scheduled(cron = "0 0 18 * * MON-FRI", zone = "America/New_York")
    public void updateDailyData() {
        Set<String> symbols = stockDataRepository.findDistinctSymbols();
        String systemUserId = "system";

        if (symbols.isEmpty()) {
            try {
                collectInitialData(2, systemUserId);
            } catch (IOException e) {
                System.err.println("Failed to collect initial data: " + e.getMessage());
            }
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        for (String symbol : symbols) {
            try {
                // For daily updates, try to get real data first
                List<StockData> latestData = fetchHistoricalData(symbol, yesterday, today, systemUserId);
                System.out.println("Updated " + symbol + " with " + latestData.size() + " new data points");
            } catch (Exception e) {
                System.err.println("Error updating data for " + symbol + ": " + e.getMessage());
            }
        }
    }
}