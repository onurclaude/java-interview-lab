package com.interviewlab.async.completablefuture.bad;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * NE YANLIŞ?
 * Executor argümanı verilmeden çağrılan {@code CompletableFuture.supplyAsync(task)},
 * {@link java.util.concurrent.ForkJoinPool#commonPool()} üzerinde çalışır - bu, her paralel
 * stream'in, executor belirtilmeyen diğer her {@code CompletableFuture} çağrısının ve
 * bunu kullanan herhangi bir kütüphane kodunun paylaştığı, JVM genelinde tek bir havuzdur.
 *
 * <p>NEDEN YANLIŞ?
 * Common pool'un varsayılan boyutu {@code availableProcessors() - 1} kadardır ve kısa,
 * CPU-yoğun işler için tasarlanmıştır - genel amaçlı bir thread pool değildir ve
 * üzerine kaç mantıksal görevin kuyruğa alınabileceğine dair bir sınır yoktur. Ona yavaş
 * veya bloklayan bir görev (bir ağ çağrısı, bir JDBC sorgusu) göndermek, çok az sayıdaki
 * paylaşılan thread'lerden birini çağrı süresi boyunca meşgul eder.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Uygulamanın {@code parallelStream()} kullanan (bu da common pool'u kullanır) alakasız
 * bir bölümü aniden yavaşlar veya durur; çünkü bu kod, common pool'un thread'lerinin
 * çoğunu sessizce yavaş I/O çağrılarıyla meşgul etmektedir - hiçbir şey tek başına yanlış
 * görünmediği için kök nedeni bulmak çok zor olan, özellikler arası bir performans hatası.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code CompletableFutureTest.shouldStarveCommonPoolWhenUsingDefaultExecutorForBlockingWork()}
 * common pool'u, executor verilmeyen overload üzerinden gönderilen bloklayan görevlerle
 * doyurur ve eşzamanlı bir {@code parallelStream()} işleminin bunun sonucunda ölçülebilir
 * şekilde gecikmeye uğradığını gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * {@code supplyAsync}/{@code runAsync} çağrılarına her zaman, iş yükünüze göre boyutlanmış,
 * açık ve özel bir {@link java.util.concurrent.Executor} verin - bkz.
 * {@link com.interviewlab.async.completablefuture.good.ComposedAggregationService}.
 */
public final class DefaultCommonPoolService {

    private DefaultCommonPoolService() {
    }

    public static CompletableFuture<String> runOnCommonPool(Supplier<String> blockingTask) {
        return CompletableFuture.supplyAsync(blockingTask); // executor yok -> ForkJoinPool.commonPool()
    }
}
