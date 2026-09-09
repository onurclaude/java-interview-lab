package com.interviewlab.patterns.chainofresponsibility.good;

public class FraudValidator implements OrderValidator {
    @Override
    public void validate(OrderValidationRequest request) {
        if (request.flaggedForFraud()) {
            throw new IllegalStateException("failed fraud check");
        }
    }
}
