package com.interviewlab.patterns.templatemethod;

public class WalletPaymentProcessing extends PaymentProcessingTemplate {

    @Override
    protected String authorize(double amount) {
        return "authorized-via-balance-check:" + amount;
    }
}
