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

    public List<TechnicalIndicator> calculateAllIndicators(String symbol, LocalDate from, LocalDate to, String userId) {
        // Fetch stock data for the given user and period
        List<StockData> stockDataList = stockDataRepository
                .findByUserIdAndSymbolAndDateBetweenOrderByDateAsc(userId, symbol, from, to);

        System.out.println("Calculating indicators for " + symbol + " with " + stockDataList.size() + " data points");

        if (stockDataList.isEmpty()) {
            return Collections.emptyList();
        }

        // Delete existing indicators for this user, symbol and date range
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

        // Create technical indicator objects
        List<TechnicalIndicator> indicators = new ArrayList<>();

        for (int i = 0; i < stockDataList.size(); i++) {
            StockData stockData = stockDataList.get(i);

            TechnicalIndicator indicator = new TechnicalIndicator();
            indicator.setUserId(userId);
            indicator.setSymbol(symbol);
            indicator.setDate(stockData.getDate());
            indicator.setPrice(stockData.getClose());

            // Set moving averages - always provide values even if using the price as fallback
            indicator.setSma20(i >= 19 && i < sma20.size() && !Double.isNaN(sma20.get(i)) ?
                    sma20.get(i) : stockData.getClose());
            indicator.setSma50(i >= 49 && i < sma50.size() && !Double.isNaN(sma50.get(i)) ?
                    sma50.get(i) : stockData.getClose());
            indicator.setSma200(i >= 199 && i < sma200.size() && !Double.isNaN(sma200.get(i)) ?
                    sma200.get(i) : stockData.getClose());

            // Set RSI - default to 50 (neutral) if not available
            indicator.setRsi14(i >= 13 && i < rsi14.size() && !Double.isNaN(rsi14.get(i)) ?
                    rsi14.get(i) : 50.0);

            // Set MACD - default to 0 if not available
            List<Double> macdLine = macd.get("macdLine");
            List<Double> signalLine = macd.get("signalLine");
            List<Double> histogram = macd.get("histogram");

            indicator.setMacdLine(i < macdLine.size() && !Double.isNaN(macdLine.get(i)) ?
                    macdLine.get(i) : 0.0);
            indicator.setMacdSignal(i < signalLine.size() && !Double.isNaN(signalLine.get(i)) ?
                    signalLine.get(i) : 0.0);
            indicator.setMacdHistogram(i < histogram.size() && !Double.isNaN(histogram.get(i)) ?
                    histogram.get(i) : 0.0);

            // Set Bollinger Bands - use price +/- 2% as default
            List<Double> upperBand = bollingerBands.get("upperBand");
            List<Double> middleBand = bollingerBands.get("middleBand");
            List<Double> lowerBand = bollingerBands.get("lowerBand");

            indicator.setBollingerUpper(i >= 19 && i < upperBand.size() && !Double.isNaN(upperBand.get(i)) ?
                    upperBand.get(i) : stockData.getClose() * 1.02);
            indicator.setBollingerMiddle(i >= 19 && i < middleBand.size() && !Double.isNaN(middleBand.get(i)) ?
                    middleBand.get(i) : stockData.getClose());
            indicator.setBollingerLower(i >= 19 && i < lowerBand.size() && !Double.isNaN(lowerBand.get(i)) ?
                    lowerBand.get(i) : stockData.getClose() * 0.98);

            indicators.add(indicator);
        }

        // Save all indicators at once
        List<TechnicalIndicator> savedIndicators = technicalIndicatorRepository.saveAll(indicators);
        System.out.println("Saved " + savedIndicators.size() + " technical indicators for " + symbol);

        return savedIndicators;
    }

    private List<Double> calculateMovingAverage(List<Double> data, int period) {
        List<Double> result = new ArrayList<>(Collections.nCopies(data.size(), Double.NaN));

        if (data.size() < period) {
            return result;
        }

        for (int i = period - 1; i < data.size(); i++) {
            double sum = 0;
            for (int j = 0; j < period; j++) {
                sum += data.get(i - j);
            }
            result.set(i, sum / period);
        }

        return result;
    }

    private List<Double> calculateRSI(List<Double> data, int period) {
        List<Double> result = new ArrayList<>(Collections.nCopies(data.size(), Double.NaN));

        if (data.size() < period + 1) {
            return result;
        }

        // Calculate price changes
        List<Double> changes = new ArrayList<>();
        changes.add(0.0);
        for (int i = 1; i < data.size(); i++) {
            changes.add(data.get(i) - data.get(i - 1));
        }

        // Calculate initial average gains and losses
        double sumGain = 0;
        double sumLoss = 0;
        for (int i = 1; i <= period; i++) {
            double change = changes.get(i);
            if (change > 0) {
                sumGain += change;
            } else {
                sumLoss += Math.abs(change);
            }
        }

        double avgGain = sumGain / period;
        double avgLoss = sumLoss / period;

        // Calculate RSI using Wilder's smoothing
        for (int i = period; i < data.size(); i++) {
            if (avgLoss == 0) {
                result.set(i, 100.0);
            } else {
                double rs = avgGain / avgLoss;
                result.set(i, 100 - (100 / (1 + rs)));
            }

            // Update averages using Wilder's smoothing
            double change = changes.get(i);
            if (change > 0) {
                avgGain = (avgGain * (period - 1) + change) / period;
                avgLoss = (avgLoss * (period - 1)) / period;
            } else {
                avgGain = (avgGain * (period - 1)) / period;
                avgLoss = (avgLoss * (period - 1) + Math.abs(change)) / period;
            }
        }

        return result;
    }

    private Map<String, List<Double>> calculateMACD(List<Double> data) {
        Map<String, List<Double>> result = new HashMap<>();

        // Calculate EMAs
        List<Double> ema12 = calculateEMA(data, 12);
        List<Double> ema26 = calculateEMA(data, 26);

        // Calculate MACD line
        List<Double> macdLine = new ArrayList<>(Collections.nCopies(data.size(), Double.NaN));
        for (int i = 0; i < data.size(); i++) {
            if (i >= 25 && !Double.isNaN(ema12.get(i)) && !Double.isNaN(ema26.get(i))) {
                macdLine.set(i, ema12.get(i) - ema26.get(i));
            }
        }

        // Calculate signal line (9-day EMA of MACD)
        List<Double> signalLine = calculateEMA(macdLine, 9);

        // Calculate histogram
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

    private List<Double> calculateEMA(List<Double> data, int period) {
        List<Double> result = new ArrayList<>(Collections.nCopies(data.size(), Double.NaN));

        if (data.size() < period) {
            return result;
        }

        // Calculate SMA for first value
        double sum = 0;
        int validCount = 0;
        for (int i = 0; i < period && i < data.size(); i++) {
            if (!Double.isNaN(data.get(i))) {
                sum += data.get(i);
                validCount++;
            }
        }

        if (validCount > 0) {
            result.set(period - 1, sum / validCount);
        }

        // Calculate EMA
        double multiplier = 2.0 / (period + 1);
        for (int i = period; i < data.size(); i++) {
            if (!Double.isNaN(data.get(i)) && !Double.isNaN(result.get(i - 1))) {
                result.set(i, (data.get(i) - result.get(i - 1)) * multiplier + result.get(i - 1));
            }
        }

        return result;
    }

    private Map<String, List<Double>> calculateBollingerBands(List<Double> data, int period, double stdDevMultiplier) {
        Map<String, List<Double>> result = new HashMap<>();

        // Calculate middle band (SMA)
        List<Double> middleBand = calculateMovingAverage(data, period);

        // Calculate standard deviation
        List<Double> upperBand = new ArrayList<>(Collections.nCopies(data.size(), Double.NaN));
        List<Double> lowerBand = new ArrayList<>(Collections.nCopies(data.size(), Double.NaN));

        for (int i = period - 1; i < data.size(); i++) {
            if (!Double.isNaN(middleBand.get(i))) {
                double sum = 0;
                for (int j = 0; j < period; j++) {
                    double diff = data.get(i - j) - middleBand.get(i);
                    sum += diff * diff;
                }
                double stdDev = Math.sqrt(sum / period);

                upperBand.set(i, middleBand.get(i) + stdDevMultiplier * stdDev);
                lowerBand.set(i, middleBand.get(i) - stdDevMultiplier * stdDev);
            }
        }

        result.put("upperBand", upperBand);
        result.put("middleBand", middleBand);
        result.put("lowerBand", lowerBand);

        return result;
    }

    public void saveIndicatorsForUser(List<TechnicalIndicator> indicators, String userId, String symbol) {
        if (indicators.isEmpty()) return;

        LocalDate minDate = indicators.stream()
                .map(TechnicalIndicator::getDate)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());

        LocalDate maxDate = indicators.stream()
                .map(TechnicalIndicator::getDate)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());

        technicalIndicatorRepository.deleteByUserIdAndSymbolAndDateBetween(userId, symbol, minDate, maxDate);
        technicalIndicatorRepository.saveAll(indicators);
        System.out.println("Saved " + indicators.size() + " indicators for user " + userId);
    }
}