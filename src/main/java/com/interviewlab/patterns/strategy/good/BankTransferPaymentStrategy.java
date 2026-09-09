package com.interviewlab.patterns.strategy.good;

import org.springframework.stereotype.Component;

@Component
public class BankTransferPaymentStrategy implements PaymentStrategy {

    @Override
    public String paymentType() {
        return "BANK_TRANSFER";
    }

    @Override
    public String pay(double amount) {
        return "Initiated bank transfer: " + amount;
    }
}
