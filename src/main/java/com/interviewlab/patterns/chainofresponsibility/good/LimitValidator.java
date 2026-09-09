package com.interviewlab.patterns.chainofresponsibility.good;

public class LimitValidator implements OrderValidator {
    @Override
    public void validate(OrderValidationRequest request) {
        if (request.amount() > request.spendingLimit()) {
            throw new IllegalStateException("exceeds spending limit");
        }
    }
}
