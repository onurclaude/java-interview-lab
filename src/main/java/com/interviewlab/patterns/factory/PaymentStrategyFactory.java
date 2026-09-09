package com.interviewlab.patterns.factory;

import com.interviewlab.patterns.strategy.good.PaymentStrategy;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Factory + Strategy birlikte çalışıyor: Spring, context'teki her {@link PaymentStrategy}
 * bean'ini (ödeme türü başına bir tane) bir {@code List} olarak enjekte eder; bu factory'nin
 * tek işi bir tür adını doğru örneğe çevirmektir. Yeni bir {@link PaymentStrategy}
 * implementasyonu eklemek ({@code @Component} ile işaretlenmiş) burada hiçbir değişiklik
 * gerektirmez - otomatik olarak devreye girer. Bu factory olmasaydı, çağıranların her somut
 * strateji sınıfını kendileri bilmesi (ve aralarından seçim yapması) gerekirdi; bu factory
 * ile, tek bir arama metoduna ve ortak arayüze bağımlı olurlar.
 */
@Component
public class PaymentStrategyFactory {

    private final Map<String, PaymentStrategy> strategiesByType;

    public PaymentStrategyFactory(List<PaymentStrategy> strategies) {
        this.strategiesByType = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(PaymentStrategy::paymentType, Function.identity()));
    }

    public PaymentStrategy getStrategy(String paymentType) {
        PaymentStrategy strategy = strategiesByType.get(paymentType);
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown payment type: " + paymentType);
        }
        return strategy;
    }
}
