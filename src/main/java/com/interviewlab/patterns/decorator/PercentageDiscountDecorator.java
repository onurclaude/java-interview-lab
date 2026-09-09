package com.interviewlab.patterns.decorator;

public class PercentageDiscountDecorator implements PricedItem {

    private final PricedItem delegate;
    private final double discountPercentage;

    public PercentageDiscountDecorator(PricedItem delegate, double discountPercentage) {
        this.delegate = delegate;
        this.discountPercentage = discountPercentage;
    }

    @Override
    public double price() {
        double base = delegate.price();
        return base - (base * discountPercentage);
    }
}
