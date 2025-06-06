package com.cv.stockoptimizer.model.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Document(collection = "technical_indicators")
@CompoundIndexes({
        @CompoundIndex(name = "userId_symbol_date_idx", def = "{'userId': 1, 'symbol': 1, 'date': 1}", unique = true)
})
public class TechnicalIndicator {
    @Id
    private String id;

    private String userId;
    private String symbol;
    private LocalDate date;
    private double price;
    private Double sma20;
    private Double sma50;
    private Double sma200;
    private Double rsi14;
    private Double macdLine;
    private Double macdSignal;
    private Double macdHistogram;
    private Double bollingerUpper;
    private Double bollingerMiddle;
    private Double bollingerLower;

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

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public Double getSma20() {
        return sma20;
    }

    public void setSma20(Double sma20) {
        this.sma20 = sma20;
    }

    public Double getSma50() {
        return sma50;
    }

    public void setSma50(Double sma50) {
        this.sma50 = sma50;
    }

    public Double getSma200() {
        return sma200;
    }

    public void setSma200(Double sma200) {
        this.sma200 = sma200;
    }

    public Double getRsi14() {
        return rsi14;
    }

    public void setRsi14(Double rsi14) {
        this.rsi14 = rsi14;
    }

    public Double getMacdLine() {
        return macdLine;
    }

    public void setMacdLine(Double macdLine) {
        this.macdLine = macdLine;
    }

    public Double getMacdSignal() {
        return macdSignal;
    }

    public void setMacdSignal(Double macdSignal) {
        this.macdSignal = macdSignal;
    }

    public Double getMacdHistogram() {
        return macdHistogram;
    }

    public void setMacdHistogram(Double macdHistogram) {
        this.macdHistogram = macdHistogram;
    }

    public Double getBollingerUpper() {
        return bollingerUpper;
    }

    public void setBollingerUpper(Double bollingerUpper) {
        this.bollingerUpper = bollingerUpper;
    }

    public Double getBollingerMiddle() {
        return bollingerMiddle;
    }

    public void setBollingerMiddle(Double bollingerMiddle) {
        this.bollingerMiddle = bollingerMiddle;
    }

    public Double getBollingerLower() {
        return bollingerLower;
    }

    public void setBollingerLower(Double bollingerLower) {
        this.bollingerLower = bollingerLower;
    }

    public String getUserId() { return userId; }

    public void setUserId(String userId) { this.userId = userId; }
}