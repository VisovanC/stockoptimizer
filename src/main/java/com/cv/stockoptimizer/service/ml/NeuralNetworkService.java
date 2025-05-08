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
    private static final int MAX_EPOCHS = 5000; // Maximum training epochs
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
            directory.mkdirs();
        }
    }

    /**
     * Prepare data for neural network training
     *
     * @param symbol Stock symbol
     * @param from Start date
     * @param to End date
     * @return Prepared MLDataSet for training
     */
    public MLDataSet prepareTrainingData(String symbol, LocalDate from, LocalDate to) {
        // Fetch technical indicators for the given period
        List<TechnicalIndicator> indicators = technicalIndicatorRepository
                .findBySymbolAndDateBetweenOrderByDateAsc(symbol, from, to);

        if (indicators.size() < INPUT_WINDOW + PREDICTION_DAYS) {
            throw new IllegalArgumentException("Not enough data to prepare training set");
        }

        // Prepare data pairs (input -> output)
        List<MLDataPair> pairs = new ArrayList<>();

        // For each possible starting point in our time series
        for (int i = 0; i < indicators.size() - INPUT_WINDOW - PREDICTION_DAYS; i++) {
            // Extract features for this window
            double[] input = new double[INPUT_WINDOW * 10]; // 10 features per day
            int inputIndex = 0;

            // Fill input vector with technical indicators for INPUT_WINDOW days
            for (int j = i; j < i + INPUT_WINDOW; j++) {
                TechnicalIndicator indicator = indicators.get(j);

                // Normalize price data by dividing by the first price in the window
                double basePrice = indicators.get(i).getPrice();

                // Add features: relative price, SMA20, SMA50, SMA200, RSI, MACD, Bollinger
                input[inputIndex++] = indicator.getPrice() / basePrice;
                input[inputIndex++] = indicator.getSma20() != null ? indicator.getSma20() / basePrice : 1.0;
                input[inputIndex++] = indicator.getSma50() != null ? indicator.getSma50() / basePrice : 1.0;
                input[inputIndex++] = indicator.getSma200() != null ? indicator.getSma200() / basePrice : 1.0;
                input[inputIndex++] = indicator.getRsi14() != null ? indicator.getRsi14() / 100.0 : 0.5; // Normalize RSI to 0-1
                input[inputIndex++] = indicator.getMacdLine() != null ? indicator.getMacdLine() / basePrice : 0.0;
                input[inputIndex++] = indicator.getMacdSignal() != null ? indicator.getMacdSignal() / basePrice : 0.0;
                input[inputIndex++] = indicator.getMacdHistogram() != null ? indicator.getMacdHistogram() / basePrice : 0.0;
                input[inputIndex++] = indicator.getBollingerUpper() != null ? indicator.getBollingerUpper() / basePrice : 1.0;
                input[inputIndex++] = indicator.getBollingerLower() != null ? indicator.getBollingerLower() / basePrice : 1.0;
            }

            // Output is the price change for the next PREDICTION_DAYS days
            double futurePrice = indicators.get(i + INPUT_WINDOW + PREDICTION_DAYS - 1).getPrice();
            double currentPrice = indicators.get(i + INPUT_WINDOW - 1).getPrice();
            double priceChange = (futurePrice - currentPrice) / currentPrice; // Percentage change

            // Normalize to the range [-1, 1] using tanh-like scaling
            double normalizedPriceChange = Math.tanh(priceChange * 2); // Scale factor of 2 can be adjusted

            double[] output = new double[1];
            output[0] = normalizedPriceChange;

            // Create a data pair and add to the list
            MLDataPair pair = new BasicMLDataPair(new BasicMLData(input), new BasicMLData(output));
            pairs.add(pair);
        }

        // Convert to Encog's MLDataSet
        MLDataSet dataSet = new BasicMLDataSet();
        for (MLDataPair pair : pairs) {
            dataSet.add(pair);
        }

        return dataSet;
    }

    /**
     * Create a new neural network for price prediction
     *
     * @return Configured but untrained neural network
     */
    public BasicNetwork createNetwork() {
        BasicNetwork network = new BasicNetwork();

        // Input layer: 10 features per day for INPUT_WINDOW days
        network.addLayer(new BasicLayer(null, true, INPUT_WINDOW * 10));

        // Hidden layers
        network.addLayer(new BasicLayer(new ActivationTANH(), true, HIDDEN_NEURONS));
        network.addLayer(new BasicLayer(new ActivationTANH(), true, HIDDEN_NEURONS / 2));

        // Output layer: predicted price change for PREDICTION_DAYS ahead
        network.addLayer(new BasicLayer(new ActivationTANH(), false, 1));

        // Finalize the network structure
        network.getStructure().finalizeStructure();
        network.reset();

        return network;
    }

    /**
     * Train a neural network with the prepared data
     *
     * @param network Neural network to train
     * @param training Training data set
     * @return Training error achieved
     */
    public double trainNetwork(BasicNetwork network, MLDataSet training) {
        // Create training algorithm
        ResilientPropagation train = new ResilientPropagation(network, training);

        // Train in epochs
        double error = 1.0;
        int epoch = 1;

        while (error > MAX_ERROR && epoch < MAX_EPOCHS) {
            train.iteration();
            error = train.getError();

            if (epoch % 100 == 0) {
                System.out.println("Epoch #" + epoch + " Error: " + error);
            }

            epoch++;
        }

        train.finishTraining();

        System.out.println("Training complete. Final error: " + error);
        return error;
    }

    /**
     * Train model for a specific stock
     *
     * @param symbol Stock symbol
     * @return Trained model information
     */
    public MLModel trainModelForStock(String symbol) throws IOException {
        // Prepare dates for training data
        LocalDate today = LocalDate.now();
        LocalDate trainStart = today.minusYears(5); // 5 years of training data

        // Prepare data
        MLDataSet trainingData = prepareTrainingData(symbol, trainStart, today);

        // Create and train the network
        BasicNetwork network = createNetwork();
        double error = trainNetwork(network, trainingData);

        // Save the network
        String modelFilePath = MODEL_DIRECTORY + symbol + "_model.eg";
        EncogDirectoryPersistence.saveObject(new File(modelFilePath), network);

        // Save model metadata to MongoDB
        MLModel model = new MLModel();
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

        return mlModelRepository.save(model);
    }

    /**
     * Load a trained model for a given stock
     *
     * @param symbol Stock symbol
     * @return Loaded neural network
     */
    public BasicNetwork loadModel(String symbol) throws IOException {
        MLModel modelInfo = mlModelRepository.findBySymbol(symbol)
                .orElseThrow(() -> new IllegalArgumentException("No trained model exists for " + symbol));

        File modelFile = new File(modelInfo.getModelFilePath());
        if (!modelFile.exists()) {
            throw new IOException("Model file not found: " + modelInfo.getModelFilePath());
        }

        return (BasicNetwork) EncogDirectoryPersistence.loadObject(modelFile);
    }

    /**
     * Make predictions for the future price movements
     *
     * @param symbol Stock symbol
     * @return Prediction results
     */
    public List<StockPrediction> predictFuturePrices(String symbol) throws IOException {
        // Load the model
        BasicNetwork network = loadModel(symbol);

        // Get the most recent data for prediction
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(INPUT_WINDOW + 10); // Add buffer for weekends/holidays

        List<TechnicalIndicator> recentData = technicalIndicatorRepository
                .findBySymbolAndDateBetweenOrderByDateAsc(symbol, startDate, today);

        // Ensure we have enough data
        if (recentData.size() < INPUT_WINDOW) {
            throw new IllegalArgumentException("Not enough recent data for prediction");
        }

        // Extract the last INPUT_WINDOW days of data
        List<TechnicalIndicator> inputWindow = recentData.subList(
                recentData.size() - INPUT_WINDOW, recentData.size());

        // Prepare input for the neural network
        double[] input = new double[INPUT_WINDOW * 10];
        int inputIndex = 0;

        // Base price for normalization
        double basePrice = inputWindow.get(0).getPrice();

        // Fill input vector
        for (TechnicalIndicator indicator : inputWindow) {
            input[inputIndex++] = indicator.getPrice() / basePrice;
            input[inputIndex++] = indicator.getSma20() != null ? indicator.getSma20() / basePrice : 1.0;
            input[inputIndex++] = indicator.getSma50() != null ? indicator.getSma50() / basePrice : 1.0;
            input[inputIndex++] = indicator.getSma200() != null ? indicator.getSma200() / basePrice : 1.0;
            input[inputIndex++] = indicator.getRsi14() != null ? indicator.getRsi14() / 100.0 : 0.5;
            input[inputIndex++] = indicator.getMacdLine() != null ? indicator.getMacdLine() / basePrice : 0.0;
            input[inputIndex++] = indicator.getMacdSignal() != null ? indicator.getMacdSignal() / basePrice : 0.0;
            input[inputIndex++] = indicator.getMacdHistogram() != null ? indicator.getMacdHistogram() / basePrice : 0.0;
            input[inputIndex++] = indicator.getBollingerUpper() != null ? indicator.getBollingerUpper() / basePrice : 1.0;
            input[inputIndex++] = indicator.getBollingerLower() != null ? indicator.getBollingerLower() / basePrice : 1.0;
        }

        // Get the current price
        double currentPrice = inputWindow.get(inputWindow.size() - 1).getPrice();

        // Run the neural network
        MLData inputData = new BasicMLData(input);
        MLData output = network.compute(inputData);

        // Convert the output back to a price change percentage
        double normalizedPrediction = output.getData(0);
        double predictedChangePercentage = Math.tanh(normalizedPrediction) / 2; // Reverse the tanh scaling

        // Calculate predicted price
        double predictedPrice = currentPrice * (1 + predictedChangePercentage);

        // Create prediction record
        StockPrediction prediction = new StockPrediction();
        prediction.setSymbol(symbol);
        prediction.setPredictionDate(today);
        prediction.setTargetDate(today.plusDays(PREDICTION_DAYS));
        prediction.setCurrentPrice(currentPrice);
        prediction.setPredictedPrice(predictedPrice);
        prediction.setPredictedChangePercentage(predictedChangePercentage * 100); // Convert to percentage
        prediction.setConfidenceScore(calculateConfidenceScore(normalizedPrediction));

        // Save and return the prediction
        return Collections.singletonList(stockPredictionRepository.save(prediction));
    }

    /**
     * Calculate a confidence score based on the model output
     *
     * @param normalizedPrediction The normalized output from the neural network
     * @return Confidence score (0-100)
     */
    private double calculateConfidenceScore(double normalizedPrediction) {
        // Simple confidence calculation based on the distance from zero
        // (outputs closer to -1 or 1 are considered more confident)
        return Math.abs(normalizedPrediction) * 100;
    }

    /**
     * Create and train models for multiple stocks
     *
     * @param symbols List of stock symbols to train models for
     * @return Map of symbols to training results
     */
    public Map<String, MLModel> trainModelsForMultipleStocks(List<String> symbols) {
        Map<String, MLModel> results = new HashMap<>();

        for (String symbol : symbols) {
            try {
                MLModel model = trainModelForStock(symbol);
                results.put(symbol, model);
                System.out.println("Successfully trained model for " + symbol);
            } catch (Exception e) {
                System.err.println("Error training model for " + symbol + ": " + e.getMessage());
            }
        }

        return results;
    }

    /**
     * Evaluate model accuracy using historical data
     *
     * @param symbol Stock symbol
     * @param startDate Start date for evaluation
     * @param endDate End date for evaluation
     * @return Evaluation metrics
     */
    public Map<String, Double> evaluateModel(String symbol, LocalDate startDate, LocalDate endDate) throws IOException {
        // Load the model
        BasicNetwork network = loadModel(symbol);

        // Get historical data for the evaluation period
        List<TechnicalIndicator> historicalData = technicalIndicatorRepository
                .findBySymbolAndDateBetweenOrderByDateAsc(symbol, startDate.minusDays(INPUT_WINDOW), endDate);

        if (historicalData.size() < INPUT_WINDOW + 10) {
            throw new IllegalArgumentException("Not enough historical data for evaluation");
        }

        // Prepare evaluation metrics
        double totalError = 0.0;
        int correctDirectionCount = 0;
        int totalPredictions = 0;

        // For each possible starting point in our evaluation period
        for (int i = 0; i <= historicalData.size() - INPUT_WINDOW - PREDICTION_DAYS; i++) {
            // Prepare input data
            double[] input = new double[INPUT_WINDOW * 10];
            int inputIndex = 0;

            // Base price for normalization
            double basePrice = historicalData.get(i).getPrice();

            // Fill input vector
            for (int j = i; j < i + INPUT_WINDOW; j++) {
                TechnicalIndicator indicator = historicalData.get(j);
                input[inputIndex++] = indicator.getPrice() / basePrice;
                input[inputIndex++] = indicator.getSma20() != null ? indicator.getSma20() / basePrice : 1.0;
                input[inputIndex++] = indicator.getSma50() != null ? indicator.getSma50() / basePrice : 1.0;
                input[inputIndex++] = indicator.getSma200() != null ? indicator.getSma200() / basePrice : 1.0;
                input[inputIndex++] = indicator.getRsi14() != null ? indicator.getRsi14() / 100.0 : 0.5;
                input[inputIndex++] = indicator.getMacdLine() != null ? indicator.getMacdLine() / basePrice : 0.0;
                input[inputIndex++] = indicator.getMacdSignal() != null ? indicator.getMacdSignal() / basePrice : 0.0;
                input[inputIndex++] = indicator.getMacdHistogram() != null ? indicator.getMacdHistogram() / basePrice : 0.0;
                input[inputIndex++] = indicator.getBollingerUpper() != null ? indicator.getBollingerUpper() / basePrice : 1.0;
                input[inputIndex++] = indicator.getBollingerLower() != null ? indicator.getBollingerLower() / basePrice : 1.0;
            }

            // Get actual prices
            double currentPrice = historicalData.get(i + INPUT_WINDOW - 1).getPrice();
            double futurePrice = historicalData.get(i + INPUT_WINDOW + PREDICTION_DAYS - 1).getPrice();
            double actualChange = (futurePrice - currentPrice) / currentPrice;

            // Run the neural network
            MLData inputData = new BasicMLData(input);
            MLData output = network.compute(inputData);

            // Convert the output back to a price change
            double normalizedPrediction = output.getData(0);
            double predictedChange = Math.tanh(normalizedPrediction) / 2;

            // Calculate error (Mean Absolute Percentage Error)
            double error = Math.abs(predictedChange - actualChange);
            totalError += error;

            // Check if prediction direction was correct
            if ((predictedChange > 0 && actualChange > 0) || (predictedChange < 0 && actualChange < 0)) {
                correctDirectionCount++;
            }

            totalPredictions++;
        }

        // Calculate average metrics
        double meanAbsoluteError = totalError / totalPredictions;
        double directionAccuracy = (double) correctDirectionCount / totalPredictions * 100.0;

        // Return evaluation metrics
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("meanAbsoluteError", meanAbsoluteError);
        metrics.put("directionAccuracy", directionAccuracy);
        metrics.put("totalPredictions", (double) totalPredictions);

        return metrics;
    }

    /**
     * Retrain models periodically to include the latest market data
     */
    //@Scheduled(cron = "0 0 2 * * SUN") // Run at 2 AM every Sunday
    public void retrainAllModels() {
        // Get all existing models
        List<MLModel> existingModels = mlModelRepository.findAll();

        for (MLModel model : existingModels) {
            try {
                trainModelForStock(model.getSymbol());
                System.out.println("Successfully retrained model for " + model.getSymbol());
            } catch (Exception e) {
                System.err.println("Error retraining model for " + model.getSymbol() + ": " + e.getMessage());
            }
        }
    }
}