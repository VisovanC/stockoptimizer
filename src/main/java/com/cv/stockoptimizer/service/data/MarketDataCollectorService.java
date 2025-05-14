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

    @Autowired
    public MarketDataCollectorService(StockDataRepository stockDataRepository) {
        this.stockDataRepository = stockDataRepository;
    }
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
    }
    public Map<String, List<StockData>> fetchHistoricalDataBatch(List<String> symbols, LocalDate from, LocalDate to) throws IOException {
        Map<String, List<StockData>> result = new HashMap<>();

        for (String symbol : symbols) {
            try {
                List<StockData> data = fetchHistoricalData(symbol, from, to);
                result.put(symbol, data);
            } catch (IOException e) {
                System.err.println("Error fetching data for " + symbol + ": " + e.getMessage());
            }
        }

        return result;
    }

    public void collectInitialData(int years) throws IOException {
        List<String> symbols = Arrays.asList(
                "^GSPC", "^DJI", "^IXIC", "^RUT",
                "AAPL", "MSFT", "GOOGL", "AMZN", "META", "TSLA", "NVDA",
                "JPM", "BAC", "WFC", "C", "GS",
                "GE", "BA", "CAT", "MMM", "HON",
                "PG", "KO", "PEP", "WMT", "MCD"
        );

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusYears(years);

        Map<String, List<StockData>> historicalData = fetchHistoricalDataBatch(symbols, startDate, endDate);
        for (Map.Entry<String, List<StockData>> entry : historicalData.entrySet()) {
            stockDataRepository.saveAll(entry.getValue());
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
                stockDataRepository.saveAll(latestData);
            } catch (IOException e) {
                System.err.println("Error updating data for " + symbol + ": " + e.getMessage());
            }
        }
    }
}