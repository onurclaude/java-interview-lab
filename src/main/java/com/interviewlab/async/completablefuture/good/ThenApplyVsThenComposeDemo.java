package com.interviewlab.async.completablefuture.good;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * {@code thenApply}, düz bir değeri düz bir değere eşler. Eşleme fonksiyonunun kendisi bir
 * {@code CompletableFuture} döndürüyorsa (yani BAŞKA bir asenkron adım başlatıyorsa),
 * {@code thenApply} bunu yine de düz bir değer gibi ele alır - bu da açmak için ikinci bir
 * {@code join()}/{@code get()} gerektiren, hantal, iç içe bir
 * {@code CompletableFuture<CompletableFuture<T>>} üretir. {@code thenCompose} tam olarak bu
 * durum için vardır: eşleme fonksiyonunun bir {@code CompletableFuture} döndürmesini bekleyerek
 * ve onu aynı, tek seviyeli future içine açarak "düzleştirir" - {@code Optional.map}'e karşı
 * {@code Optional.flatMap}'in asenkron karşılığıdır.
 */
public final class ThenApplyVsThenComposeDemo {

    private ThenApplyVsThenComposeDemo() {
    }

    public static CompletableFuture<CompletableFuture<String>> nestedWithThenApply(
            CompletableFuture<String> initial, Function<String, CompletableFuture<String>> asyncStep) {
        return initial.thenApply(asyncStep);
    }

    public static CompletableFuture<String> flattenedWithThenCompose(
            CompletableFuture<String> initial, Function<String, CompletableFuture<String>> asyncStep) {
        return initial.thenCompose(asyncStep);
    }
}
