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

/**
 * Service for collecting stock market data from Yahoo Finance API
 */
@Service
public class MarketDataCollectorService {

    private final StockDataRepository stockDataRepository;

    @Autowired
    public MarketDataCollectorService(StockDataRepository stockDataRepository) {
        this.stockDataRepository = stockDataRepository;
    }

    /**
     * Fetches historical data for a single stock from Yahoo Finance
     *
     * @param symbol Stock ticker symbol
     * @param from Start date
     * @param to End date
     * @return List of stock data points
     */
    public List<StockData> fetchHistoricalData(String symbol, LocalDate from, LocalDate to) throws IOException {
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
            return Collections.emptyList();
        }

        return quotes.stream()
                .filter(quote -> quote.getClose() != null) // Filter out quotes with null values
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
    }

    /**
     * Fetches historical data for multiple stocks
     *
     * @param symbols List of stock ticker symbols
     * @param from Start date
     * @param to End date
     * @return Map of symbol to list of stock data points
     */
    public Map<String, List<StockData>> fetchHistoricalDataBatch(List<String> symbols, LocalDate from, LocalDate to) throws IOException {
        Map<String, List<StockData>> result = new HashMap<>();

        for (String symbol : symbols) {
            try {
                List<StockData> data = fetchHistoricalData(symbol, from, to);
                result.put(symbol, data);
            } catch (IOException e) {
                // Log the error but continue with other symbols
                System.err.println("Error fetching data for " + symbol + ": " + e.getMessage());
            }
        }

        return result;
    }

    /**
     * Collects initial historical data for a predefined set of stocks
     *
     * @param years Number of years of historical data to collect
     */
    public void collectInitialData(int years) throws IOException {
        // List of symbols representing major stocks and indices
        List<String> symbols = Arrays.asList(
                // Major US indices
                "^GSPC", "^DJI", "^IXIC", "^RUT",
                // Major tech stocks
                "AAPL", "MSFT", "GOOGL", "AMZN", "META", "TSLA", "NVDA",
                // Financial stocks
                "JPM", "BAC", "WFC", "C", "GS",
                // Industrial stocks
                "GE", "BA", "CAT", "MMM", "HON",
                // Consumer stocks
                "PG", "KO", "PEP", "WMT", "MCD"
        );

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusYears(years);

        Map<String, List<StockData>> historicalData = fetchHistoricalDataBatch(symbols, startDate, endDate);

        // Save all data to MongoDB
        for (Map.Entry<String, List<StockData>> entry : historicalData.entrySet()) {
            stockDataRepository.saveAll(entry.getValue());
        }
    }

    /**
     * Scheduled job to update daily stock data
     * Runs at the end of each trading day (6PM Eastern Time)
     */
    @Scheduled(cron = "0 0 18 * * MON-FRI", zone = "America/New_York")
    public void updateDailyData() {
        // Get all unique symbols from the repository
        Set<String> symbols = stockDataRepository.findDistinctSymbols();

        if (symbols.isEmpty()) {
            // If no data exists yet, collect some initial data
            try {
                collectInitialData(2); // 2 years of historical data
            } catch (IOException e) {
                System.err.println("Failed to collect initial data: " + e.getMessage());
            }
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        // Update each symbol with the latest data
        for (String symbol : symbols) {
            try {
                List<StockData> latestData = fetchHistoricalData(symbol, yesterday, today);
                stockDataRepository.saveAll(latestData);
            } catch (IOException e) {
                System.err.println("Error updating data for " + symbol + ": " + e.getMessage());
            }
        }
    }
}