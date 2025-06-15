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
    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_DELAY_MS = 3000;
    private static final long MAX_DELAY_MS = 15000;
    private static final Map<String, Long> lastRequestTime = new HashMap<>();
    private static final long MIN_REQUEST_INTERVAL = 2000;

    @Autowired
    public MarketDataCollectorService(StockDataRepository stockDataRepository) {
        this.stockDataRepository = stockDataRepository;
    }

    public List<StockData> fetchHistoricalData(String symbol, LocalDate from, LocalDate to) throws IOException {
        return fetchHistoricalData(symbol, from, to, "system");
    }

    public List<StockData> fetchHistoricalData(String symbol, LocalDate from, LocalDate to, String userId) throws IOException {
        List<StockData> existingData = stockDataRepository.findByUserIdAndSymbolAndDateBetweenOrderByDateAsc(userId, symbol, from, to);

        if (existingData.size() >= 100) {
            System.out.println("Using existing data for " + symbol + " - " + existingData.size() + " points");
            return existingData;
        }

        if (!existingData.isEmpty()) {
            System.out.println("Deleting " + existingData.size() + " partial data points for " + symbol);
            stockDataRepository.deleteByUserIdAndSymbolAndDateBetween(userId, symbol, from, to);
        }

        try {
            enforceRateLimit(symbol);
            List<StockData> yahooData = fetchFromYahooFinanceWithRetries(symbol, from, to, userId);
            if (!yahooData.isEmpty()) {
                stockDataRepository.saveAll(yahooData);
                return yahooData;
            }
        } catch (Exception e) {
            System.err.println("Yahoo Finance failed for " + symbol + ": " + e.getMessage());
            throw new IOException("Failed to fetch data from Yahoo Finance for " + symbol + ": " + e.getMessage());
        }

        throw new IOException("Unable to fetch data for " + symbol + " from Yahoo Finance");
    }

    private void enforceRateLimit(String symbol) {
        synchronized (lastRequestTime) {
            Long lastTime = lastRequestTime.get(symbol);
            if (lastTime != null) {
                long timeSinceLastRequest = System.currentTimeMillis() - lastTime;
                if (timeSinceLastRequest < MIN_REQUEST_INTERVAL) {
                    try {
                        Thread.sleep(MIN_REQUEST_INTERVAL - timeSinceLastRequest);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            lastRequestTime.put(symbol, System.currentTimeMillis());
        }
    }

    private List<StockData> fetchFromYahooFinanceWithRetries(String symbol, LocalDate from, LocalDate to, String userId) throws IOException {
        IOException lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 1) {
                    long delay = Math.min(INITIAL_DELAY_MS * attempt, MAX_DELAY_MS);
                    System.out.println("Retry attempt " + attempt + " for " + symbol + " after " + delay + "ms delay");
                    Thread.sleep(delay);
                }

                System.out.println("Fetching from Yahoo Finance for " + symbol + " (attempt " + attempt + ")");

                Calendar fromDate = Calendar.getInstance();
                fromDate.setTime(Date.from(from.atStartOfDay(ZoneId.systemDefault()).toInstant()));
                fromDate.add(Calendar.DAY_OF_MONTH, -5);

                Calendar toDate = Calendar.getInstance();
                toDate.setTime(Date.from(to.atStartOfDay(ZoneId.systemDefault()).toInstant()));
                toDate.add(Calendar.DAY_OF_MONTH, 1);

                yahoofinance.Stock stock = YahooFinance.get(symbol, fromDate, toDate, Interval.DAILY);

                if (stock == null) {
                    throw new IOException("Yahoo Finance returned null for symbol: " + symbol);
                }

                List<HistoricalQuote> quotes = stock.getHistory();
                if (quotes == null || quotes.isEmpty()) {
                    if (attempt < MAX_RETRIES) {
                        System.out.println("No data returned, retrying...");
                        continue;
                    }
                    throw new IOException("No historical data available for " + symbol);
                }

                List<StockData> stockDataList = quotes.stream()
                        .filter(quote -> quote.getClose() != null && quote.getDate() != null)
                        .map(quote -> {
                            LocalDate date = quote.getDate().getTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                            if (date.isBefore(from) || date.isAfter(to)) {
                                return null;
                            }

                            StockData data = new StockData(
                                    userId,
                                    symbol,
                                    date,
                                    quote.getOpen() != null ? quote.getOpen().doubleValue() : quote.getClose().doubleValue(),
                                    quote.getHigh() != null ? quote.getHigh().doubleValue() : quote.getClose().doubleValue(),
                                    quote.getLow() != null ? quote.getLow().doubleValue() : quote.getClose().doubleValue(),
                                    quote.getClose().doubleValue(),
                                    quote.getVolume() != null ? quote.getVolume().longValue() : 0
                            );
                            data.setAdjClose(quote.getAdjClose() != null ? quote.getAdjClose().doubleValue() : quote.getClose().doubleValue());
                            return data;
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                Collections.reverse(stockDataList);

                System.out.println("Successfully fetched " + stockDataList.size() + " data points from Yahoo Finance for " + symbol);
                return stockDataList;

            } catch (IOException e) {
                lastException = e;
                System.err.println("Yahoo Finance error (attempt " + attempt + "): " + e.getMessage());
                if (e.getMessage().contains("404") || e.getMessage().contains("Invalid ticker")) {
                    throw new IOException("Invalid stock symbol: " + symbol);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for retry");
            } catch (Exception e) {
                lastException = new IOException("Unexpected error: " + e.getMessage());
                System.err.println("Unexpected error (attempt " + attempt + "): " + e.getMessage());
            }
        }

        throw lastException != null ? lastException : new IOException("Failed to fetch data after " + MAX_RETRIES + " attempts");
    }

    public Map<String, List<StockData>> fetchHistoricalDataBatch(List<String> symbols, LocalDate from, LocalDate to, String userId) throws IOException {
        Map<String, List<StockData>> result = new HashMap<>();

        for (int i = 0; i < symbols.size(); i++) {
            String symbol = symbols.get(i);
            try {
                if (i > 0) {
                    Thread.sleep(2000);
                }

                List<StockData> data = fetchHistoricalData(symbol, from, to, userId);
                result.put(symbol, data);
            } catch (Exception e) {
                System.err.println("Error fetching data for " + symbol + ": " + e.getMessage());
                throw new IOException("Failed to fetch data for " + symbol + ": " + e.getMessage());
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

        double trendFactor = 1.0 + (random.nextDouble() - 0.4) * 0.3;

        while (!currentDate.isAfter(to)) {
            if (currentDate.getDayOfWeek().getValue() >= 6) {
                currentDate = currentDate.plusDays(1);
                continue;
            }

            double dailyChange = (random.nextDouble() - 0.5) * 0.06;

            double trendAdjustment = (trendFactor - 1.0) / 365.0;
            basePrice = basePrice * (1 + dailyChange + trendAdjustment);

            basePrice = Math.max(basePrice, 10.0);

            double open = basePrice * (1 + (random.nextDouble() - 0.5) * 0.02);
            double high = Math.max(open, basePrice) * (1 + random.nextDouble() * 0.02);
            double low = Math.min(open, basePrice) * (1 - random.nextDouble() * 0.02);
            double close = basePrice;

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

        stockDataRepository.deleteByUserIdAndSymbol(userId, symbol);

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
                List<StockData> latestData = fetchHistoricalData(symbol, yesterday, today, systemUserId);
                System.out.println("Updated " + symbol + " with " + latestData.size() + " new data points");
            } catch (Exception e) {
                System.err.println("Error updating data for " + symbol + ": " + e.getMessage());
            }
        }
    }
}