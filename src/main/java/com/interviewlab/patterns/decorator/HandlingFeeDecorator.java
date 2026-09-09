package com.interviewlab.patterns.decorator;

public class HandlingFeeDecorator implements PricedItem {

    private final PricedItem delegate;
    private final double feeAmount;

    public HandlingFeeDecorator(PricedItem delegate, double feeAmount) {
        this.delegate = delegate;
        this.feeAmount = feeAmount;
    }

    @Override
    public double price() {
        return delegate.price() + feeAmount;
    }
}
