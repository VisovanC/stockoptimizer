package com.cv.stockoptimizer.service.ml;

import com.cv.stockoptimizer.model.entity.MLModel;
import com.cv.stockoptimizer.model.entity.StockPrediction;
import com.cv.stockoptimizer.model.entity.TechnicalIndicator;
import com.cv.stockoptimizer.repository.MLModelRepository;
import com.cv.stockoptimizer.repository.StockPredictionRepository;
import com.cv.stockoptimizer.repository.TechnicalIndicatorRepository;
import org.encog.engine.network.activation.ActivationTANH;
import org.encog.ml.data.MLData;
import org.encog.ml.data.MLDataPair;
import org.encog.ml.data.MLDataSet;
import org.encog.ml.data.basic.BasicMLData;
import org.encog.ml.data.basic.BasicMLDataPair;
import org.encog.ml.data.basic.BasicMLDataSet;
import org.encog.neural.networks.BasicNetwork;
import org.encog.neural.networks.layers.BasicLayer;
import org.encog.neural.networks.training.propagation.resilient.ResilientPropagation;
import org.encog.persist.EncogDirectoryPersistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.encog.mathutil.randomize.NguyenWidrowRandomizer;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

@Service
public class NeuralNetworkService {

    private final TechnicalIndicatorRepository technicalIndicatorRepository;
    private final MLModelRepository mlModelRepository;
    private final StockPredictionRepository stockPredictionRepository;

    // Reduced requirements for better compatibility
    private static final int INPUT_WINDOW = 30; // Reduced from 60 to 30 days
    private static final int PREDICTION_DAYS = 5;
    private static final int HIDDEN_NEURONS = 30; // Reduced from 50
    private static final double MAX_ERROR = 0.02; // Increased tolerance
    private static final int MAX_EPOCHS = 500; // Reduced from 1000
    private static final String MODEL_DIRECTORY = "models/";

    @Autowired
    public NeuralNetworkService(TechnicalIndicatorRepository technicalIndicatorRepository,
                                MLModelRepository mlModelRepository,
                                StockPredictionRepository stockPredictionRepository) {
        this.technicalIndicatorRepository = technicalIndicatorRepository;
        this.mlModelRepository = mlModelRepository;
        this.stockPredictionRepository = stockPredictionRepository;

        File directory = new File(MODEL_DIRECTORY);
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            System.out.println("Models directory created: " + created);
        }
    }

    public MLDataSet prepareTrainingData(String symbol, LocalDate from, LocalDate to, String userId) {
        List<TechnicalIndicator> indicators = technicalIndicatorRepository
                .findByUserIdAndSymbolAndDateBetweenOrderByDateAsc(userId, symbol, from, to);

        System.out.println("Found " + indicators.size() + " indicators for " + symbol);

        // Minimum data check - reduced requirement
        int minDataPoints = INPUT_WINDOW + PREDICTION_DAYS + 10; // Added buffer
        if (indicators.size() < minDataPoints) {
            throw new IllegalArgumentException(
                    "Not enough data to prepare training set. Need at least " + minDataPoints +
                            " data points, but only have " + indicators.size()
            );
        }

        MLDataSet dataSet = new BasicMLDataSet();
        int validPairs = 0;

        // Create training pairs with better error handling
        for (int i = 0; i <= indicators.size() - INPUT_WINDOW - PREDICTION_DAYS; i++) {
            try {
                double[] input = new double[INPUT_WINDOW * 5]; // Reduced features from 10 to 5
                int inputIndex = 0;

                double basePrice = indicators.get(i).getPrice();
                if (basePrice <= 0) continue;

                // Fill input with essential features only
                for (int j = i; j < i + INPUT_WINDOW; j++) {
                    TechnicalIndicator indicator = indicators.get(j);

                    // Price (normalized)
                    input[inputIndex++] = indicator.getPrice() / basePrice;

                    // RSI (normalized to 0-1)
                    double rsi = indicator.getRsi14() != null ? indicator.getRsi14() / 100.0 : 0.5;
                    input[inputIndex++] = Math.max(0, Math.min(1, rsi));

                    // SMA ratio
                    double sma20 = indicator.getSma20() != null ? indicator.getSma20() : indicator.getPrice();
                    input[inputIndex++] = sma20 / basePrice;

                    // MACD normalized
                    double macd = indicator.getMacdHistogram() != null ?
                            Math.tanh(indicator.getMacdHistogram() / basePrice) : 0.0;
                    input[inputIndex++] = macd;

                    // Bollinger band position
                    double bbUpper = indicator.getBollingerUpper() != null ?
                            indicator.getBollingerUpper() : indicator.getPrice() * 1.02;
                    double bbLower = indicator.getBollingerLower() != null ?
                            indicator.getBollingerLower() : indicator.getPrice() * 0.98;
                    double bbPosition = (indicator.getPrice() - bbLower) / (bbUpper - bbLower);
                    input[inputIndex++] = Math.max(0, Math.min(1, bbPosition));
                }

                // Calculate output
                double futurePrice = indicators.get(i + INPUT_WINDOW + PREDICTION_DAYS - 1).getPrice();
                double currentPrice = indicators.get(i + INPUT_WINDOW - 1).getPrice();

                if (currentPrice <= 0) continue;

                double priceChange = (futurePrice - currentPrice) / currentPrice;
                priceChange = Math.max(-0.2, Math.min(0.2, priceChange)); // Clip to ±20%

                double[] output = new double[] { priceChange };

                // Validate data
                boolean validData = true;
                for (double v : input) {
                    if (Double.isNaN(v) || Double.isInfinite(v)) {
                        validData = false;
                        break;
                    }
                }

                if (validData && !Double.isNaN(output[0]) && !Double.isInfinite(output[0])) {
                    MLDataPair pair = new BasicMLDataPair(new BasicMLData(input), new BasicMLData(output));
                    dataSet.add(pair);
                    validPairs++;
                }
            } catch (Exception e) {
                System.err.println("Error creating data pair at index " + i + ": " + e.getMessage());
            }
        }

        System.out.println("Created " + validPairs + " valid training pairs for " + symbol);

        if (validPairs < 5) { // Reduced minimum requirement
            throw new IllegalArgumentException(
                    "Not enough valid training pairs. Need at least 5, but only created " + validPairs
            );
        }

        return dataSet;
    }

    public BasicNetwork createNetwork() {
        BasicNetwork network = new BasicNetwork();

        // Simplified network architecture
        network.addLayer(new BasicLayer(null, true, INPUT_WINDOW * 5)); // Input layer
        network.addLayer(new BasicLayer(new ActivationTANH(), true, HIDDEN_NEURONS)); // Hidden layer
        network.addLayer(new BasicLayer(new ActivationTANH(), false, 1)); // Output layer

        network.getStructure().finalizeStructure();
        network.reset();

        new NguyenWidrowRandomizer().randomize(network);

        return network;
    }

    public double trainNetwork(BasicNetwork network, MLDataSet training) {
        ResilientPropagation train = new ResilientPropagation(network, training);

        double error = 1.0;
        int epoch = 1;

        System.out.println("Starting training with " + training.size() + " samples...");

        while (error > MAX_ERROR && epoch <= MAX_EPOCHS) {
            train.iteration();
            error = train.getError();

            if (epoch % 50 == 0) {
                System.out.println("Epoch #" + epoch + " Error: " + error);
            }

            epoch++;
        }

        train.finishTraining();
        System.out.println("Training complete after " + epoch + " epochs. Final error: " + error);

        return error;
    }

    public MLModel trainModelForStock(String symbol, String userId) throws IOException {
        System.out.println("Starting model training for " + symbol + " for user " + userId);

        Optional<MLModel> existingModel = mlModelRepository.findByUserIdAndSymbol(userId, symbol);
        if (existingModel.isPresent()) {
            System.out.println("Found existing model, retraining...");
        }

        LocalDate today = LocalDate.now();
        LocalDate trainStart = today.minusYears(2);

        // First, ensure we have enough indicators
        List<TechnicalIndicator> indicators = technicalIndicatorRepository
                .findByUserIdAndSymbolAndDateBetweenOrderByDateAsc(userId, symbol, trainStart, today);

        System.out.println("Available indicators for training: " + indicators.size());

        if (indicators.size() < INPUT_WINDOW + PREDICTION_DAYS + 10) {
            throw new IOException(
                    "Insufficient technical indicators for " + symbol +
                            ". Need at least " + (INPUT_WINDOW + PREDICTION_DAYS + 10) +
                            " but only have " + indicators.size()
            );
        }

        MLDataSet trainingData;
        try {
            trainingData = prepareTrainingData(symbol, trainStart, today, userId);
        } catch (Exception e) {
            System.err.println("Error preparing training data: " + e.getMessage());
            throw new IOException("Failed to prepare training data for " + symbol + ": " + e.getMessage());
        }

        BasicNetwork network = createNetwork();
        double error;

        try {
            error = trainNetwork(network, trainingData);
        } catch (Exception e) {
            System.err.println("Error training network: " + e.getMessage());
            throw new IOException("Failed to train network for " + symbol + ": " + e.getMessage());
        }

        String modelFilePath = MODEL_DIRECTORY + symbol + "_" + userId + "_model.eg";
        try {
            File modelFile = new File(modelFilePath);
            EncogDirectoryPersistence.saveObject(modelFile, network);
            System.out.println("Model saved to " + modelFilePath);
        } catch (Exception e) {
            System.err.println("Error saving model: " + e.getMessage());
            throw new IOException("Failed to save model for " + symbol + ": " + e.getMessage());
        }

        MLModel model = existingModel.orElse(new MLModel());
        model.setUserId(userId);
        model.setSymbol(symbol);
        model.setModelType("PRICE_PREDICTION");
        model.setInputWindow(INPUT_WINDOW);
        model.setPredictionDays(PREDICTION_DAYS);
        model.setTrainingError(error);
        model.setTrainingDate(today);
        model.setModelFilePath(modelFilePath);
        model.setFeatures(Arrays.asList("PRICE", "RSI", "SMA", "MACD", "BOLLINGER"));

        MLModel savedModel = mlModelRepository.save(model);
        System.out.println("Model metadata saved for " + symbol);

        return savedModel;
    }

    public BasicNetwork loadModel(String symbol, String userId) throws IOException {
        MLModel modelInfo = mlModelRepository.findByUserIdAndSymbol(userId, symbol)
                .orElseThrow(() -> new IllegalArgumentException("No trained model exists for " + symbol));

        File modelFile = new File(modelInfo.getModelFilePath());
        if (!modelFile.exists()) {
            throw new IOException("Model file not found: " + modelInfo.getModelFilePath());
        }

        try {
            return (BasicNetwork) EncogDirectoryPersistence.loadObject(modelFile);
        } catch (Exception e) {
            throw new IOException("Failed to load model for " + symbol + ": " + e.getMessage());
        }
    }

    public List<StockPrediction> predictFuturePrices(String symbol, String userId) throws IOException {
        System.out.println("Generating predictions for " + symbol + " for user " + userId);

        BasicNetwork network = loadModel(symbol, userId);

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(INPUT_WINDOW + 30); // Extra buffer

        List<TechnicalIndicator> recentData = technicalIndicatorRepository
                .findByUserIdAndSymbolAndDateBetweenOrderByDateAsc(userId, symbol, startDate, today);

        System.out.println("Found " + recentData.size() + " recent data points for prediction");

        if (recentData.size() < INPUT_WINDOW) {
            throw new IllegalArgumentException(
                    "Not enough recent data for prediction. Need " + INPUT_WINDOW +
                            " days, but only have " + recentData.size()
            );
        }

        List<TechnicalIndicator> inputWindow = recentData.subList(
                recentData.size() - INPUT_WINDOW, recentData.size());

        // Prepare input
        double[] input = new double[INPUT_WINDOW * 5];
        int inputIndex = 0;
        double basePrice = inputWindow.get(0).getPrice();

        if (basePrice <= 0) {
            throw new IllegalArgumentException("Invalid base price for prediction: " + basePrice);
        }

        for (TechnicalIndicator indicator : inputWindow) {
            input[inputIndex++] = indicator.getPrice() / basePrice;

            double rsi = indicator.getRsi14() != null ? indicator.getRsi14() / 100.0 : 0.5;
            input[inputIndex++] = Math.max(0, Math.min(1, rsi));

            double sma20 = indicator.getSma20() != null ? indicator.getSma20() : indicator.getPrice();
            input[inputIndex++] = sma20 / basePrice;

            double macd = indicator.getMacdHistogram() != null ?
                    Math.tanh(indicator.getMacdHistogram() / basePrice) : 0.0;
            input[inputIndex++] = macd;

            double bbUpper = indicator.getBollingerUpper() != null ?
                    indicator.getBollingerUpper() : indicator.getPrice() * 1.02;
            double bbLower = indicator.getBollingerLower() != null ?
                    indicator.getBollingerLower() : indicator.getPrice() * 0.98;
            double bbPosition = (indicator.getPrice() - bbLower) / (bbUpper - bbLower);
            input[inputIndex++] = Math.max(0, Math.min(1, bbPosition));
        }

        double currentPrice = inputWindow.get(inputWindow.size() - 1).getPrice();

        MLData inputData = new BasicMLData(input);
        MLData output = network.compute(inputData);

        double predictedChange = output.getData(0);
        double predictedChangePercentage = Math.max(-20, Math.min(20, predictedChange * 100));
        double predictedPrice = currentPrice * (1 + predictedChange);

        StockPrediction prediction = new StockPrediction();
        prediction.setUserId(userId);
        prediction.setSymbol(symbol);
        prediction.setPredictionDate(today);
        prediction.setTargetDate(today.plusDays(PREDICTION_DAYS));
        prediction.setCurrentPrice(currentPrice);
        prediction.setPredictedPrice(predictedPrice);
        prediction.setPredictedChangePercentage(predictedChangePercentage);
        prediction.setConfidenceScore(calculateConfidenceScore(predictedChange));

        StockPrediction savedPrediction = stockPredictionRepository.save(prediction);
        System.out.println("Prediction saved for " + symbol + ": " +
                String.format("%.2f%%", predictedChangePercentage));

        return Collections.singletonList(savedPrediction);
    }

    private double calculateConfidenceScore(double prediction) {
        // Confidence based on prediction magnitude
        double magnitude = Math.abs(prediction);
        if (magnitude < 0.01) return 50.0; // Very small change = low confidence
        if (magnitude < 0.05) return 70.0;
        if (magnitude < 0.10) return 80.0;
        return Math.min(90.0, 80.0 + magnitude * 100);
    }

    public MLModel trainModelForStock(String symbol) throws IOException {
        return trainModelForStock(symbol, "system");
    }

    public Map<String, MLModel> trainModelsForMultipleStocks(List<String> symbols, String userId) {
        Map<String, MLModel> results = new HashMap<>();

        for (String symbol : symbols) {
            try {
                MLModel model = trainModelForStock(symbol, userId);
                results.put(symbol, model);
                System.out.println("Successfully trained model for " + symbol);
            } catch (Exception e) {
                System.err.println("Error training model for " + symbol + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        return results;
    }
}