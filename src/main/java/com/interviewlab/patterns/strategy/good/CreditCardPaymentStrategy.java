package com.interviewlab.patterns.strategy.good;

import org.springframework.stereotype.Component;

@Component
public class CreditCardPaymentStrategy implements PaymentStrategy {

    @Override
    public String paymentType() {
        return "CREDIT_CARD";
    }

    @Override
    public String pay(double amount) {
        return "Charged credit card: " + amount;
    }
}
