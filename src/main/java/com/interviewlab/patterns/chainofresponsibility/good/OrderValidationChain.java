package com.interviewlab.patterns.chainofresponsibility.good;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Zincirin kendisi, birbirinden bağımsız {@link OrderValidator}'ların sıralı bir listesinden
 * ibarettir. Yeni bir kural eklemek, yeni bir sınıf eklemek ve bu listeye bir satır eklemek
 * anlamına gelir - mevcut hiçbir şey değişmez. {@code chainofresponsibility.bad.MonolithicOrderValidator}'ın
 * dallarının aksine, her validator tamamen kendi başına unit test edilebilir.
 */
@Component
public class OrderValidationChain {

    private final List<OrderValidator> validators = List.of(
            new StockValidator(), new FraudValidator(), new LimitValidator());

    public void validate(OrderValidationRequest request) {
        for (OrderValidator validator : validators) {
            validator.validate(request);
        }
    }
}
