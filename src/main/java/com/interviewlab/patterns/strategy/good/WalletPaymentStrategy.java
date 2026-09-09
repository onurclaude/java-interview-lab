package com.interviewlab.patterns.strategy.good;

import org.springframework.stereotype.Component;

@Component
public class WalletPaymentStrategy implements PaymentStrategy {

    @Override
    public String paymentType() {
        return "WALLET";
    }

    @Override
    public String pay(double amount) {
        return "Debited wallet: " + amount;
    }
}
