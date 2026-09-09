package com.interviewlab.async.completablefuture.good;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Üç istisna işleme kombinatörünü ve aralarındaki farkları gösterir:
 * <ul>
 *   <li>{@code exceptionally} - bir fallback değer sağlayarak hatadan kurtulur; SADECE
 *       hata yolunda çalışır, bir {@code catch} bloğuna karşılık gelir.</li>
 *   <li>{@code handle} - HEM başarı HEM hata yolunda çalışır, {@code (result, throwable)}
 *       parametrelerini alır ve bunlardan tam olarak biri null olmaz; dönüş değeri her
 *       durumda yeni aşamanın sonucu olur.</li>
 *   <li>{@code whenComplete} - {@code handle} gibi {@code (result, throwable)} çiftini
 *       gözlemler, ama sonucu değiştirmez (dönüş değeri yok sayılır) - loglama gibi yan
 *       etkiler için kullanılır, bir {@code finally} bloğuna karşılık gelir.</li>
 * </ul>
 */
public final class ExceptionPropagationDemo {

    private ExceptionPropagationDemo() {
    }

    public static CompletableFuture<String> withExceptionally(Supplier<String> task, String fallback) {
        return CompletableFuture.supplyAsync(task).exceptionally(throwable -> fallback);
    }

    public static CompletableFuture<String> withHandle(Supplier<String> task, String fallback) {
        return CompletableFuture.supplyAsync(task).handle((result, throwable) -> throwable != null ? fallback : result);
    }

    /** whenComplete sonucu asla değiştirmez - başarısız bir future, çalıştıktan sonra da başarısız kalır. */
    public static CompletableFuture<String> withWhenCompleteObserving(Supplier<String> task, AtomicReference<Throwable> observedFailure) {
        return CompletableFuture.supplyAsync(task)
                .whenComplete((result, throwable) -> observedFailure.set(throwable));
    }
}
