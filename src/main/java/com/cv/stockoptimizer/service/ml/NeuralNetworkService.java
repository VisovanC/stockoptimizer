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

/**
 * Service for training and using neural networks with the Encog framework
 */
@Service
public class NeuralNetworkService {

    private final TechnicalIndicatorRepository technicalIndicatorRepository;
    private final MLModelRepository mlModelRepository;
    private final StockPredictionRepository stockPredictionRepository;

    // Configuration constants
    private static final int INPUT_WINDOW = 60; // Number of days to look back
    private static final int PREDICTION_DAYS = 5; // Number of days to predict ahead
    private static final int HIDDEN_NEURONS = 50; // Number of neurons in hidden layer
    private static final double MAX_ERROR = 0.01; // Training stops when error reaches this value
    private static final int MAX_EPOCHS = 1000; // Reduced for faster training
    private static final String MODEL_DIRECTORY = "models/";

    @Autowired
    public NeuralNetworkService(TechnicalIndicatorRepository technicalIndicatorRepository,
                                MLModelRepository mlModelRepository,
                                StockPredictionRepository stockPredictionRepository) {
        this.technicalIndicatorRepository = technicalIndicatorRepository;
        this.mlModelRepository = mlModelRepository;
        this.stockPredictionRepository = stockPredictionRepository;

        // Create models directory if it doesn't exist
        File directory = new File(MODEL_DIRECTORY);
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            System.out.println("Models directory created: " + created);
        }
    }

    /**
     * Prepare data for neural network training
     */
    public MLDataSet prepareTrainingData(String symbol, LocalDate from, LocalDate to, String userId) {
        // Fetch technical indicators for the given period
        List<TechnicalIndicator> indicators = technicalIndicatorRepository
                .findByUserIdAndSymbolAndDateBetweenOrderByDateAsc(userId, symbol, from, to);

        System.out.println("Found " + indicators.size() + " indicators for " + symbol);

        if (indicators.size() < INPUT_WINDOW + PREDICTION_DAYS) {
            throw new IllegalArgumentException("Not enough data to prepare training set. Need at least " +
                    (INPUT_WINDOW + PREDICTION_DAYS) + " data points, but only have " + indicators.size());
        }



        // Prepare data pairs (input -> output)
        MLDataSet dataSet = new BasicMLDataSet();
        int validPairs = 0;

        // For each possible starting point in our time series
        for (int i = 0; i <= indicators.size() - INPUT_WINDOW - PREDICTION_DAYS; i++) {
            try {
                // Extract features for this window
                double[] input = new double[INPUT_WINDOW * 10]; // 10 features per day
                int inputIndex = 0;

                // Check if we have valid data for this window
                boolean hasValidData = true;
                double basePrice = indicators.get(i).getPrice();

                if (basePrice <= 0) {
                    continue; // Skip if base price is invalid
                }

                // Fill input vector with technical indicators for INPUT_WINDOW days
                // In prepareTrainingData() method, update the input preparation similarly:
                for (int j = i; j < i + INPUT_WINDOW; j++) {
                    TechnicalIndicator indicator = indicators.get(j);

                    // Use the same fallback logic as above
                    input[inputIndex++] = indicator.getPrice() / basePrice;
                    input[inputIndex++] = indicator.getSma20() != null ? indicator.getSma20() / basePrice : indicator.getPrice() / basePrice;
                    input[inputIndex++] = indicator.getSma50() != null ? indicator.getSma50() / basePrice : indicator.getPrice() / basePrice;
                    input[inputIndex++] = indicator.getSma200() != null ? indicator.getSma200() / basePrice : indicator.getPrice() / basePrice;
                    input[inputIndex++] = indicator.getRsi14() != null ? indicator.getRsi14() / 100.0 : 0.5;
                    input[inputIndex++] = indicator.getMacdLine() != null ? indicator.getMacdLine() / basePrice : 0.0;
                    input[inputIndex++] = indicator.getMacdSignal() != null ? indicator.getMacdSignal() / basePrice : 0.0;
                    input[inputIndex++] = indicator.getMacdHistogram() != null ? indicator.getMacdHistogram() / basePrice : 0.0;
                    input[inputIndex++] = indicator.getBollingerUpper() != null ? indicator.getBollingerUpper() / basePrice : indicator.getPrice() * 1.02 / basePrice;
                    input[inputIndex++] = indicator.getBollingerLower() != null ? indicator.getBollingerLower() / basePrice : indicator.getPrice() * 0.98 / basePrice;
                }

                // Output is the price change for the next PREDICTION_DAYS days
                double futurePrice = indicators.get(i + INPUT_WINDOW + PREDICTION_DAYS - 1).getPrice();
                double currentPrice = indicators.get(i + INPUT_WINDOW - 1).getPrice();

                if (currentPrice <= 0) {
                    continue; // Skip if current price is invalid
                }

                double priceChange = (futurePrice - currentPrice) / currentPrice;

                // Clip extreme values
                priceChange = Math.max(-0.5, Math.min(0.5, priceChange));

                // Normalize to the range [-1, 1] using tanh-like scaling
                double normalizedPriceChange = Math.tanh(priceChange * 2);

                double[] output = new double[1];
                output[0] = normalizedPriceChange;

                // Before creating the MLDataPair
                if (Double.isNaN(normalizedPriceChange) || Double.isInfinite(normalizedPriceChange)) {
                    System.err.println("Skipping invalid price change at index " + i + ": " + normalizedPriceChange);
                    continue;
                }

                boolean validInput = true;
                for (double value : input) {
                    if (Double.isNaN(value) || Double.isInfinite(value)) {
                        validInput = false;
                        break;
                    }
                }

                if (!validInput) {
                    System.err.println("Skipping invalid input at index " + i);
                    continue;
                }

                // Create a data pair and add to the dataset
                MLDataPair pair = new BasicMLDataPair(new BasicMLData(input), new BasicMLData(output));
                dataSet.add(pair);
                validPairs++;
            } catch (Exception e) {
                System.err.println("Error creating data pair at index " + i + ": " + e.getMessage());
            }
        }

        System.out.println("Created " + validPairs + " valid training pairs for " + symbol);

        if (validPairs < 10) {
            throw new IllegalArgumentException("Not enough valid training pairs. Need at least 10, but only created " + validPairs);
        }

        return dataSet;
    }

    /**
     * Create a new neural network for price prediction
     */
    public BasicNetwork createNetwork() {
        BasicNetwork network = new BasicNetwork();

        // Input layer: 10 features per day for INPUT_WINDOW days
        network.addLayer(new BasicLayer(null, true, INPUT_WINDOW * 10));

        // Hidden layers
        network.addLayer(new BasicLayer(new ActivationTANH(), true, HIDDEN_NEURONS));
        network.addLayer(new BasicLayer(new ActivationTANH(), true, HIDDEN_NEURONS / 2));

        // Output layer: predicted price change
        network.addLayer(new BasicLayer(new ActivationTANH(), false, 1));

        // Finalize the network structure
        network.getStructure().finalizeStructure();
        network.reset();

        new NguyenWidrowRandomizer().randomize(network);

        return network;
    }

    /**
     * Train a neural network with the prepared data
     */
    public double trainNetwork(BasicNetwork network, MLDataSet training) {
        // Create training algorithm
        ResilientPropagation train = new ResilientPropagation(network, training);

        // Train in epochs
        double error = 1.0;
        int epoch = 1;

        System.out.println("Starting training with " + training.size() + " samples...");

        while (error > MAX_ERROR && epoch <= MAX_EPOCHS) {
            train.iteration();
            error = train.getError();

            if (epoch % 100 == 0) {
                System.out.println("Epoch #" + epoch + " Error: " + error);
            }

            epoch++;
        }

        train.finishTraining();

        System.out.println("Training complete after " + epoch + " epochs. Final error: " + error);
        return error;


    }

    /**
     * Train model for a specific stock
     */
    public MLModel trainModelForStock(String symbol, String userId) throws IOException {
        System.out.println("Starting model training for " + symbol + " for user " + userId);

        // Check if model already exists for this user
        Optional<MLModel> existingModel = mlModelRepository.findByUserIdAndSymbol(userId, symbol);
        if (existingModel.isPresent()) {
            System.out.println("Found existing model for " + symbol + " for user " + userId + ", retraining...");
        }

        // Prepare dates for training data
        LocalDate today = LocalDate.now();
        LocalDate trainStart = today.minusYears(2);

        // Prepare data with userId
        MLDataSet trainingData;
        try {
            trainingData = prepareTrainingData(symbol, trainStart, today, userId);
        } catch (Exception e) {
            System.err.println("Error preparing training data: " + e.getMessage());
            throw new IOException("Failed to prepare training data for " + symbol + ": " + e.getMessage());
        }

        // Create and train the network
        BasicNetwork network = createNetwork();
        double error;
        try {
            error = trainNetwork(network, trainingData);
        } catch (Exception e) {
            System.err.println("Error training network: " + e.getMessage());
            throw new IOException("Failed to train network for " + symbol + ": " + e.getMessage());
        }

        // Save the network
        String modelFilePath = MODEL_DIRECTORY + symbol + "_model.eg";
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
        model.setSymbol(symbol);
        model.setModelType("PRICE_PREDICTION");
        model.setInputWindow(INPUT_WINDOW);
        model.setPredictionDays(PREDICTION_DAYS);
        model.setTrainingError(error);
        model.setTrainingDate(today);
        model.setModelFilePath(modelFilePath);
        model.setFeatures(Arrays.asList(
                "PRICE", "SMA20", "SMA50", "SMA200", "RSI14",
                "MACD_LINE", "MACD_SIGNAL", "MACD_HISTOGRAM",
                "BOLLINGER_UPPER", "BOLLINGER_LOWER"));

        MLModel savedModel = mlModelRepository.save(model);
        System.out.println("Model metadata saved for " + symbol);

        return mlModelRepository.save(model);
    }

    /**
     * Load a trained model for a given stock
     */
    public BasicNetwork loadModel(String symbol) throws IOException {
        // First try to find with system userId
        MLModel modelInfo = mlModelRepository.findByUserIdAndSymbol("system", symbol)
                .orElseGet(() -> mlModelRepository.findBySymbol(symbol)
                        .orElseThrow(() -> new IllegalArgumentException("No trained model exists for " + symbol)));

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

    /**
     * Make predictions for the future price movements
     */
    public List<StockPrediction> predictFuturePrices(String symbol, String userId) throws IOException {
        System.out.println("Generating predictions for " + symbol + " for user " + userId);

        // Load the model for this user
        BasicNetwork network = loadModel(symbol);

        // Get recent data for this user
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(INPUT_WINDOW * 2);

        List<TechnicalIndicator> recentData = technicalIndicatorRepository
                .findByUserIdAndSymbolAndDateBetweenOrderByDateAsc(userId, symbol, startDate, today);


        System.out.println("Found " + recentData.size() + " recent data points for " + symbol);

        // If we don't have enough recent data, get more historical data
        if (recentData.size() < INPUT_WINDOW) {
            startDate = today.minusDays(365); // Get up to a year
            recentData = technicalIndicatorRepository
                    .findByUserIdAndSymbolAndDateBetweenOrderByDateAsc(userId, symbol, startDate, today);

            System.out.println("Extended search found " + recentData.size() + " data points");
        }

        // Ensure we have enough data
        if (recentData.size() < INPUT_WINDOW) {
            throw new IllegalArgumentException("Not enough recent data for prediction. Need " +
                    INPUT_WINDOW + " days, but only have " + recentData.size());
        }

        // Extract the last INPUT_WINDOW days of data
        List<TechnicalIndicator> inputWindow = recentData.subList(
                recentData.size() - INPUT_WINDOW, recentData.size());


        // Prepare input for the neural network
        double[] input = new double[INPUT_WINDOW * 10];
        int inputIndex = 0;

        // Base price for normalization
        double basePrice = inputWindow.get(0).getPrice();

        if (basePrice <= 0) {
            throw new IllegalArgumentException("Invalid base price for prediction: " + basePrice);
        }

        // Fill input vector
        for (TechnicalIndicator indicator : inputWindow) {
            // Handle missing values more carefully
            input[inputIndex++] = indicator.getPrice() / basePrice;

            // For SMA values, use the price as fallback if SMA is null
            input[inputIndex++] = indicator.getSma20() != null ? indicator.getSma20() / basePrice : indicator.getPrice() / basePrice;
            input[inputIndex++] = indicator.getSma50() != null ? indicator.getSma50() / basePrice : indicator.getPrice() / basePrice;
            input[inputIndex++] = indicator.getSma200() != null ? indicator.getSma200() / basePrice : indicator.getPrice() / basePrice;

            // For RSI, use neutral value (50) if null
            input[inputIndex++] = indicator.getRsi14() != null ? indicator.getRsi14() / 100.0 : 0.5;

            // For MACD, use 0 if null
            input[inputIndex++] = indicator.getMacdLine() != null ? indicator.getMacdLine() / basePrice : 0.0;
            input[inputIndex++] = indicator.getMacdSignal() != null ? indicator.getMacdSignal() / basePrice : 0.0;
            input[inputIndex++] = indicator.getMacdHistogram() != null ? indicator.getMacdHistogram() / basePrice : 0.0;

            // For Bollinger bands, use price +/- standard deviation estimate
            input[inputIndex++] = indicator.getBollingerUpper() != null ? indicator.getBollingerUpper() / basePrice : indicator.getPrice() * 1.02 / basePrice;
            input[inputIndex++] = indicator.getBollingerLower() != null ? indicator.getBollingerLower() / basePrice : indicator.getPrice() * 0.98 / basePrice;
        }

        // Get the current price
        double currentPrice = inputWindow.get(inputWindow.size() - 1).getPrice();

        System.out.println("Base price: " + basePrice);
        System.out.println("Input window size: " + inputWindow.size());

        // Check for NaN in input
        for (int i = 0; i < input.length; i++) {
            if (Double.isNaN(input[i]) || Double.isInfinite(input[i])) {
                System.err.println("Invalid input at index " + i + ": " + input[i]);
            }
        }

        // Run the neural network
        MLData inputData = new BasicMLData(input);
        MLData output = network.compute(inputData);

        // Debug the output
        double normalizedPrediction = output.getData(0);
        System.out.println("Neural network output: " + normalizedPrediction);

        // Convert the output back to a price change percentage
        double predictedChangePercentage = Math.max(-0.3, Math.min(0.3, normalizedPrediction * 0.1));
        System.out.println("Predicted change percentage: " + predictedChangePercentage);

        // Calculate predicted price
        double predictedPrice = currentPrice * (1 + predictedChangePercentage);
        System.out.println("Current price: " + currentPrice + ", Predicted price: " + predictedPrice);

        // Create prediction record
        StockPrediction prediction = new StockPrediction();
        prediction.setUserId(userId); // Set userId
        prediction.setSymbol(symbol);
        prediction.setPredictionDate(today);
        prediction.setTargetDate(today.plusDays(PREDICTION_DAYS));
        prediction.setCurrentPrice(currentPrice);
        prediction.setPredictedPrice(predictedPrice);
        prediction.setPredictedChangePercentage(predictedChangePercentage * 100);
        prediction.setConfidenceScore(calculateConfidenceScore(normalizedPrediction));

        // Save and return the prediction
        StockPrediction savedPrediction = stockPredictionRepository.save(prediction);
        System.out.println("Prediction saved for " + symbol + ": " +
                String.format("%.2f%%", predictedChangePercentage * 100));

        return predictFuturePrices(symbol, "system");
    }

    /**
     * Calculate a confidence score based on the model output
     */
    private double calculateConfidenceScore(double normalizedPrediction) {
        // Simple confidence calculation based on the distance from zero
        return Math.min(100, Math.abs(normalizedPrediction) * 100);
    }

    public MLModel trainModelForStock(String symbol) throws IOException {
        // Use a default userId or system userId for backward compatibility
        return trainModelForStock(symbol, "system");
    }

    /**
     * Create and train models for multiple stocks
     */
   /* public Map<String, MLModel> trainModelsForMultipleStocks(List<String> symbols) {
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
    }*/
}