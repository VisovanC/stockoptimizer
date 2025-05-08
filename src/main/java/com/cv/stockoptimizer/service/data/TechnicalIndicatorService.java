package com.cv.stockoptimizer.service.data;

import com.cv.stockoptimizer.model.entity.StockData;
import com.cv.stockoptimizer.model.entity.TechnicalIndicator;
import com.cv.stockoptimizer.repository.StockDataRepository;
import com.cv.stockoptimizer.repository.TechnicalIndicatorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for calculating technical indicators from stock data
 */
@Service
public class TechnicalIndicatorService {

    private final StockDataRepository stockDataRepository;
    private final TechnicalIndicatorRepository technicalIndicatorRepository;

    @Autowired
    public TechnicalIndicatorService(StockDataRepository stockDataRepository,
                                     TechnicalIndicatorRepository technicalIndicatorRepository) {
        this.stockDataRepository = stockDataRepository;
        this.technicalIndicatorRepository = technicalIndicatorRepository;
    }

    /**
     * Calculate moving average for a given period
     *
     * @param data List of closing prices
     * @param period Moving average period (e.g., 20 for 20-day MA)
     * @return List of moving averages (first (period-1) values will be NaN)
     */
    private List<Double> calculateMovingAverage(List<Double> data, int period) {
        List<Double> result = new ArrayList<>(Collections.nCopies(data.size(), Double.NaN));

        for (int i = period - 1; i < data.size(); i++) {
            double sum = 0;
            for (int j = 0; j < period; j++) {
                sum += data.get(i - j);
            }
            result.set(i, sum / period);
        }

        return result;
    }

    /**
     * Calculate Relative Strength Index (RSI)
     *
     * @param data List of closing prices
     * @param period RSI period (typically 14)
     * @return List of RSI values
     */
    private List<Double> calculateRSI(List<Double> data, int period) {
        List<Double> result = new ArrayList<>(Collections.nCopies(data.size(), Double.NaN));

        // Calculate price changes
        List<Double> changes = new ArrayList<>();
        changes.add(0.0); // No change for first data point
        for (int i = 1; i < data.size(); i++) {
            changes.add(data.get(i) - data.get(i - 1));
        }

        // Calculate average gains and losses
        for (int i = period; i < data.size(); i++) {
            double sumGain = 0;
            double sumLoss = 0;

            for (int j = i - period + 1; j <= i; j++) {
                double change = changes.get(j);
                if (change > 0) {
                    sumGain += change;
                } else {
                    sumLoss += Math.abs(change);
                }
            }

            double avgGain = sumGain / period;
            double avgLoss = sumLoss / period;

            if (avgLoss == 0) {
                result.set(i, 100.0);
            } else {
                double rs = avgGain / avgLoss;
                result.set(i, 100 - (100 / (1 + rs)));
            }
        }

        return result;
    }

    /**
     * Calculate Moving Average Convergence Divergence (MACD)
     *
     * @param data List of closing prices
     * @return Map with MACD line, signal line, and histogram values
     */
    private Map<String, List<Double>> calculateMACD(List<Double> data) {
        Map<String, List<Double>> result = new HashMap<>();

        // Calculate 12-day EMA
        List<Double> ema12 = calculateEMA(data, 12);

        // Calculate 26-day EMA
        List<Double> ema26 = calculateEMA(data, 26);

        // Calculate MACD line (12-day EMA - 26-day EMA)
        List<Double> macdLine = new ArrayList<>(Collections.nCopies(data.size(), Double.NaN));
        for (int i = 0; i < data.size(); i++) {
            if (!Double.isNaN(ema12.get(i)) && !Double.isNaN(ema26.get(i))) {
                macdLine.set(i, ema12.get(i) - ema26.get(i));
            }
        }

        // Calculate 9-day EMA of MACD line (signal line)
        List<Double> signalLine = calculateEMA(macdLine, 9);

        // Calculate MACD histogram (MACD line - signal line)
        List<Double> histogram = new ArrayList<>(Collections.nCopies(data.size(), Double.NaN));
        for (int i = 0; i < data.size(); i++) {
            if (!Double.isNaN(macdLine.get(i)) && !Double.isNaN(signalLine.get(i))) {
                histogram.set(i, macdLine.get(i) - signalLine.get(i));
            }
        }

        result.put("macdLine", macdLine);
        result.put("signalLine", signalLine);
        result.put("histogram", histogram);

        return result;
    }

    /**
     * Calculate Exponential Moving Average (EMA)
     *
     * @param data List of values
     * @param period EMA period
     * @return List of EMA values
     */
    private List<Double> calculateEMA(List<Double> data, int period) {
        List<Double> result = new ArrayList<>(Collections.nCopies(data.size(), Double.NaN));

        // Calculate simple moving average for the first point
        double sum = 0;
        int count = 0;
        for (int i = 0; i < period && i < data.size(); i++) {
            if (!Double.isNaN(data.get(i))) {
                sum += data.get(i);
                count++;
            }
        }

        if (count > 0 && period <= data.size()) {
            result.set(period - 1, sum / count);
        }

        // Calculate multiplier: (2 / (period + 1))
        double multiplier = 2.0 / (period + 1);

        // Calculate EMA for the rest of the points
        for (int i = period; i < data.size(); i++) {
            if (!Double.isNaN(data.get(i)) && !Double.isNaN(result.get(i - 1))) {
                result.set(i, (data.get(i) - result.get(i - 1)) * multiplier + result.get(i - 1));
            }
        }

        return result;
    }

    /**
     * Calculate Bollinger Bands
     *
     * @param data List of closing prices
     * @param period Moving average period (typically 20)
     * @param stdDevMultiplier Standard deviation multiplier (typically 2)
     * @return Map with upper band, middle band, and lower band values
     */
    private Map<String, List<Double>> calculateBollingerBands(List<Double> data, int period, double stdDevMultiplier) {
        Map<String, List<Double>> result = new HashMap<>();

        // Calculate middle band (simple moving average)
        List<Double> middleBand = calculateMovingAverage(data, period);

        // Calculate standard deviation
        List<Double> stdDev = new ArrayList<>(Collections.nCopies(data.size(), Double.NaN));
        for (int i = period - 1; i < data.size(); i++) {
            double sum = 0;
            for (int j = 0; j < period; j++) {
                double diff = data.get(i - j) - middleBand.get(i);
                sum += diff * diff;
            }
            stdDev.set(i, Math.sqrt(sum / period));
        }

        // Calculate upper and lower bands
        List<Double> upperBand = new ArrayList<>(Collections.nCopies(data.size(), Double.NaN));
        List<Double> lowerBand = new ArrayList<>(Collections.nCopies(data.size(), Double.NaN));

        for (int i = 0; i < data.size(); i++) {
            if (!Double.isNaN(middleBand.get(i)) && !Double.isNaN(stdDev.get(i))) {
                upperBand.set(i, middleBand.get(i) + stdDevMultiplier * stdDev.get(i));
                lowerBand.set(i, middleBand.get(i) - stdDevMultiplier * stdDev.get(i));
            }
        }

        result.put("upperBand", upperBand);
        result.put("middleBand", middleBand);
        result.put("lowerBand", lowerBand);

        return result;
    }

    /**
     * Calculate all technical indicators for a given stock
     *
     * @param symbol Stock symbol
     * @param from Start date
     * @param to End date
     * @return List of technical indicator objects
     */
    public List<TechnicalIndicator> calculateAllIndicators(String symbol, LocalDate from, LocalDate to) {
        // Fetch stock data for the given period
        List<StockData> stockDataList = stockDataRepository.findBySymbolAndDateBetweenOrderByDateAsc(symbol, from, to);

        if (stockDataList.isEmpty()) {
            return Collections.emptyList();
        }

        // Extract closing prices
        List<Double> closingPrices = stockDataList.stream()
                .map(StockData::getClose)
                .collect(Collectors.toList());

        // Calculate indicators
        List<Double> sma20 = calculateMovingAverage(closingPrices, 20);
        List<Double> sma50 = calculateMovingAverage(closingPrices, 50);
        List<Double> sma200 = calculateMovingAverage(closingPrices, 200);
        List<Double> rsi14 = calculateRSI(closingPrices, 14);
        Map<String, List<Double>> macd = calculateMACD(closingPrices);
        Map<String, List<Double>> bollingerBands = calculateBollingerBands(closingPrices, 20, 2.0);

        // Create technical indicator objects
        List<TechnicalIndicator> indicators = new ArrayList<>();

        for (int i = 0; i < stockDataList.size(); i++) {
            StockData stockData = stockDataList.get(i);

            TechnicalIndicator indicator = new TechnicalIndicator();
            indicator.setSymbol(symbol);
            indicator.setDate(stockData.getDate());
            indicator.setPrice(stockData.getClose());

            // Set moving averages
            indicator.setSma20(i < sma20.size() ? sma20.get(i) : null);
            indicator.setSma50(i < sma50.size() ? sma50.get(i) : null);
            indicator.setSma200(i < sma200.size() ? sma200.get(i) : null);

            // Set RSI
            indicator.setRsi14(i < rsi14.size() ? rsi14.get(i) : null);

            // Set MACD
            indicator.setMacdLine(i < macd.get("macdLine").size() ? macd.get("macdLine").get(i) : null);
            indicator.setMacdSignal(i < macd.get("signalLine").size() ? macd.get("signalLine").get(i) : null);
            indicator.setMacdHistogram(i < macd.get("histogram").size() ? macd.get("histogram").get(i) : null);

            // Set Bollinger Bands
            indicator.setBollingerUpper(i < bollingerBands.get("upperBand").size() ? bollingerBands.get("upperBand").get(i) : null);
            indicator.setBollingerMiddle(i < bollingerBands.get("middleBand").size() ? bollingerBands.get("middleBand").get(i) : null);
            indicator.setBollingerLower(i < bollingerBands.get("lowerBand").size() ? bollingerBands.get("lowerBand").get(i) : null);

            indicators.add(indicator);
        }

        // Save and return the indicators
        return technicalIndicatorRepository.saveAll(indicators);
    }

    /**
     * Calculate and store technical indicators for all stocks in the database
     */
    public void calculateAndStoreAllIndicators() {
        // Get all unique symbols from the repository
        Set<String> symbols = stockDataRepository.findDistinctSymbols();

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusYears(2); // 2 years of data

        for (String symbol : symbols) {
            try {
                calculateAllIndicators(symbol, startDate, today);
            } catch (Exception e) {
                System.err.println("Error calculating indicators for " + symbol + ": " + e.getMessage());
            }
        }
    }
}