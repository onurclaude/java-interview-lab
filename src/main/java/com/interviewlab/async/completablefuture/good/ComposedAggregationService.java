package com.interviewlab.async.completablefuture.good;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * {@link com.interviewlab.async.completablefuture.bad.BlockingGetAggregationService} ve
 * {@link com.interviewlab.async.completablefuture.bad.DefaultCommonPoolService} sınıflarının
 * doğru karşılığı: üç çağrı da herhangi bir şey bloklanmadan önce başlatılır ve paylaşılan
 * common pool yerine açık, özel bir {@link Executor} üzerinde çalışır.
 */
public class ComposedAggregationService {

    private final Executor executor;

    public ComposedAggregationService(Executor executor) {
        this.executor = executor;
    }

    public CompletableFuture<String> aggregateConcurrently(Supplier<String> orders,
                                                            Supplier<String> payments,
                                                            Supplier<String> recommendations) {
        CompletableFuture<String> ordersFuture = CompletableFuture.supplyAsync(orders, executor);
        CompletableFuture<String> paymentsFuture = CompletableFuture.supplyAsync(payments, executor);
        CompletableFuture<String> recommendationsFuture = CompletableFuture.supplyAsync(recommendations, executor);

        // Bu noktada üçü de zaten eşzamanlı olarak çalışıyor; allOf sadece en son biteni
        // bekler, ardından sonuçlar okunur (zaten hazırdır, bloklama olmaz).
        return CompletableFuture.allOf(ordersFuture, paymentsFuture, recommendationsFuture)
                .thenApply(ignoredVoid -> ordersFuture.join() + "|" + paymentsFuture.join() + "|" + recommendationsFuture.join());
    }
}
