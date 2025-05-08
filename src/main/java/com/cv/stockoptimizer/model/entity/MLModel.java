package com.cv.stockoptimizer.model.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.List;

@Document(collection = "ml_models")
public class MLModel {
    @Id
    private String id;

    private String symbol;
    private String modelType;
    private int inputWindow;
    private int predictionDays;
    private double trainingError;
    private LocalDate trainingDate;
    private String modelFilePath;
    private List<String> features;

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public int getInputWindow() {
        return inputWindow;
    }

    public void setInputWindow(int inputWindow) {
        this.inputWindow = inputWindow;
    }

    public int getPredictionDays() {
        return predictionDays;
    }

    public void setPredictionDays(int predictionDays) {
        this.predictionDays = predictionDays;
    }

    public double getTrainingError() {
        return trainingError;
    }

    public void setTrainingError(double trainingError) {
        this.trainingError = trainingError;
    }

    public LocalDate getTrainingDate() {
        return trainingDate;
    }

    public void setTrainingDate(LocalDate trainingDate) {
        this.trainingDate = trainingDate;
    }

    public String getModelFilePath() {
        return modelFilePath;
    }

    public void setModelFilePath(String modelFilePath) {
        this.modelFilePath = modelFilePath;
    }

    public List<String> getFeatures() {
        return features;
    }

    public void setFeatures(List<String> features) {
        this.features = features;
    }
}
