package com.interviewlab.async.completablefuture.bad;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/**
 * NE YANLIŞ?
 * Birbirinden bağımsız, yavaş üç çağrının her biri {@code supplyAsync} ile başlatılıyor,
 * ama her birinde hemen ardından {@code .get()} çağrılıyor - bu da onların eşzamanlı
 * çalışmasına ve sonuçların sonradan birleştirilmesine izin vermek yerine, birbiri
 * ardına sırayla çalıştırılmalarına yol açıyor.
 *
 * <p>NEDEN YANLIŞ?
 * Art arda {@code supplyAsync(...).get()} çağrısı şu anlama gelir: görev 1'i başlat,
 * bitene kadar blokla, SONRA görev 2'yi başlat, bitene kadar blokla, SONRA görev 3'ü
 * başlat. Her görev, bir öncekinin tamamen beklenmesinden sonra başlar - aralarında
 * hiçbir örtüşme yoktur. Kod, üç asenkron görev gönderiminin bedelini öder ama
 * paralellikten hiçbir kazanç elde etmez; toplam süre, üçünü bir döngüde senkron
 * olarak çağırmakla aynı şekilde, üç çağrının TOPLAMI kadardır.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Siparişleri, ödemeleri ve önerileri (her biri ~200ms) birleştiren bir endpoint,
 * ~200ms yerine ~600ms sürer ve bu "asenkron" kod, iyileştirmeye çalıştığı senkron
 * versiyondan daha zor okunur - her iki dünyanın da en kötüsü.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code CompletableFutureTest.shouldTakeSumOfDurationsWhenBlockingOnEachFutureImmediately()}
 * duvar saati süresini ölçer ve bunun üç simüle edilmiş çağrı süresinin maksimumuna değil,
 * toplamına yakın olduğunu gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * Önce üçünü de başlat, SONRA birleştir - bkz.
 * {@link com.interviewlab.async.completablefuture.good.ComposedAggregationService}.
 */
public class BlockingGetAggregationService {

    private final Executor executor;

    public BlockingGetAggregationService(Executor executor) {
        this.executor = executor;
    }

    public String aggregateSequentiallyByBlocking(java.util.function.Supplier<String> orders,
                                                   java.util.function.Supplier<String> payments,
                                                   java.util.function.Supplier<String> recommendations)
            throws ExecutionException, InterruptedException {
        String ordersResult = CompletableFuture.supplyAsync(orders, executor).get();
        String paymentsResult = CompletableFuture.supplyAsync(payments, executor).get();
        String recommendationsResult = CompletableFuture.supplyAsync(recommendations, executor).get();
        return ordersResult + "|" + paymentsResult + "|" + recommendationsResult;
    }
}
