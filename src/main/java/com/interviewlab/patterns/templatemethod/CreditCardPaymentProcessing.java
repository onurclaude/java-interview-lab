package com.interviewlab.patterns.templatemethod;

public class CreditCardPaymentProcessing extends PaymentProcessingTemplate {

    @Override
    protected String authorize(double amount) {
        return "authorized-via-3ds:" + amount;
    }
}
