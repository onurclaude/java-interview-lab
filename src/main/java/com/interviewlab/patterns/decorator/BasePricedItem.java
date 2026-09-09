package com.interviewlab.patterns.decorator;

public class BasePricedItem implements PricedItem {

    private final double basePrice;

    public BasePricedItem(double basePrice) {
        this.basePrice = basePrice;
    }

    @Override
    public double price() {
        return basePrice;
    }
}
