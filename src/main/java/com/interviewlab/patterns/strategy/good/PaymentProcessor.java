package com.interviewlab.patterns.strategy.good;

import com.interviewlab.patterns.factory.PaymentStrategyFactory;
import org.springframework.stereotype.Service;

/** {@code patterns.strategy.bad.IfElsePaymentProcessor}'ın doğru karşılığı. */
@Service
public class PaymentProcessor {

    private final PaymentStrategyFactory paymentStrategyFactory;

    public PaymentProcessor(PaymentStrategyFactory paymentStrategyFactory) {
        this.paymentStrategyFactory = paymentStrategyFactory;
    }

    public String process(String paymentType, double amount) {
        return paymentStrategyFactory.getStrategy(paymentType).pay(amount);
    }
}
