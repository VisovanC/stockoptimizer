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


    //Calculate moving average for a given period

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

    // Calculate Relative Strength Index (RSI)
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

    // Calculate Moving Average Convergence Divergence (MACD)

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

    // Calculate Exponential Moving Average (EMA)

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

    // Calculate Bollinger Bands

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

    // Calculate all technical indicators for a given stock

    public List<TechnicalIndicator> calculateAllIndicators(String symbol, LocalDate from, LocalDate to, String userId) {
        // Fetch stock data for the given user and period
        List<StockData> stockDataList = stockDataRepository
                .findByUserIdAndSymbolAndDateBetweenOrderByDateAsc(userId, symbol, from, to);

        if (stockDataList.isEmpty()) {
            return Collections.emptyList();
        }

        // Delete existing indicators for this user, symbol and date range to avoid duplicates
        technicalIndicatorRepository.deleteByUserIdAndSymbolAndDateBetween(userId, symbol, from, to);

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

        // Create technical indicator objects with userId
        List<TechnicalIndicator> indicators = new ArrayList<>();

        for (int i = 0; i < stockDataList.size(); i++) {
            StockData stockData = stockDataList.get(i);

            TechnicalIndicator indicator = new TechnicalIndicator();
            indicator.setUserId(userId); // Set userId
            indicator.setSymbol(symbol);
            indicator.setDate(stockData.getDate());
            indicator.setPrice(stockData.getClose());

            // Set moving averages
            indicator.setSma20(i < sma20.size() && !Double.isNaN(sma20.get(i)) ? sma20.get(i) : null);
            indicator.setSma50(i < sma50.size() && !Double.isNaN(sma50.get(i)) ? sma50.get(i) : null);
            indicator.setSma200(i < sma200.size() && !Double.isNaN(sma200.get(i)) ? sma200.get(i) : null);

            // Set RSI
            indicator.setRsi14(i < rsi14.size() && !Double.isNaN(rsi14.get(i)) ? rsi14.get(i) : null);

            // Set MACD
            List<Double> macdLine = macd.get("macdLine");
            List<Double> signalLine = macd.get("signalLine");
            List<Double> histogram = macd.get("histogram");

            indicator.setMacdLine(i < macdLine.size() && !Double.isNaN(macdLine.get(i)) ? macdLine.get(i) : null);
            indicator.setMacdSignal(i < signalLine.size() && !Double.isNaN(signalLine.get(i)) ? signalLine.get(i) : null);
            indicator.setMacdHistogram(i < histogram.size() && !Double.isNaN(histogram.get(i)) ? histogram.get(i) : null);

            // Set Bollinger Bands
            List<Double> upperBand = bollingerBands.get("upperBand");
            List<Double> middleBand = bollingerBands.get("middleBand");
            List<Double> lowerBand = bollingerBands.get("lowerBand");

            indicator.setBollingerUpper(i < upperBand.size() && !Double.isNaN(upperBand.get(i)) ? upperBand.get(i) : null);
            indicator.setBollingerMiddle(i < middleBand.size() && !Double.isNaN(middleBand.get(i)) ? middleBand.get(i) : null);
            indicator.setBollingerLower(i < lowerBand.size() && !Double.isNaN(lowerBand.get(i)) ? lowerBand.get(i) : null);

            indicators.add(indicator);
        }

        // Save all indicators at once
        List<TechnicalIndicator> savedIndicators = technicalIndicatorRepository.saveAll(indicators);
        System.out.println("Saved " + savedIndicators.size() + " technical indicators for user " + userId + " and symbol " + symbol);

        return savedIndicators;
    }

    // Calculate and store technical indicators for all stocks in the database

    public void saveIndicatorsForUser(List<TechnicalIndicator> indicators, String userId, String symbol) {
        if (indicators.isEmpty()) return;

        // Get date range
        LocalDate minDate = indicators.stream()
                .map(TechnicalIndicator::getDate)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());

        LocalDate maxDate = indicators.stream()
                .map(TechnicalIndicator::getDate)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());

        // Delete existing indicators in this range
        technicalIndicatorRepository.deleteByUserIdAndSymbolAndDateBetween(userId, symbol, minDate, maxDate);

        // Save new indicators
        technicalIndicatorRepository.saveAll(indicators);
        System.out.println("Saved " + indicators.size() + " indicators for user " + userId);
    }
}