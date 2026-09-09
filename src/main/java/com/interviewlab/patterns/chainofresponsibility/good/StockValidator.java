package com.interviewlab.patterns.chainofresponsibility.good;

public class StockValidator implements OrderValidator {
    @Override
    public void validate(OrderValidationRequest request) {
        if (request.quantity() > request.stockAvailable()) {
            throw new IllegalStateException("insufficient stock");
        }
    }
}
