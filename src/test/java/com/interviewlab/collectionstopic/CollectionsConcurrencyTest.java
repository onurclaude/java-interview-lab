package com.interviewlab.collectionstopic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.interviewlab.collectionstopic.bad.CompoundCheckThenActCacheService;
import com.interviewlab.collectionstopic.bad.UnsafeHashMapCacheService;
import com.interviewlab.collectionstopic.good.AtomicComputeIfAbsentCacheService;
import com.interviewlab.collectionstopic.good.BlockingQueueProducerConsumer;
import com.interviewlab.collectionstopic.good.ConcurrentHashMapCacheService;
import com.interviewlab.collectionstopic.good.SafeIterationListHolder;
import java.util.ConcurrentModificationException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** HashMap/ConcurrentHashMap/CopyOnWriteArrayList yazıları için docs mülakat materyaline bakın. */
class CollectionsConcurrencyTest {

    @Test
    void shouldThrowConcurrentModificationExceptionWithPlainHashMap() throws InterruptedException {
        UnsafeHashMapCacheService service = new UnsafeHashMapCacheService();
        for (int i = 0; i < 1000; i++) {
            service.put("key-" + i, "value-" + i);
        }

        AtomicBoolean sawConcurrentModification = new AtomicBoolean(false);
        AtomicBoolean stop = new AtomicBoolean(false);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> {
            while (!stop.get()) {
                try {
                    for (String value : service.rawMap().values()) {
                        // sadece iterasyon yapılıyor
                    }
                } catch (ConcurrentModificationException e) {
                    sawConcurrentModification.set(true);
                    stop.set(true);
                }
            }
        });
        executor.submit(() -> {
            int i = 1000;
            while (!stop.get()) {
                service.put("key-" + (i++), "value");
            }
        });

        long deadline = System.currentTimeMillis() + 5000;
        while (!sawConcurrentModification.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        stop.set(true);
        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);

        assertThat(sawConcurrentModification.get())
                .as("başka bir thread yapısal olarak değiştirirken düz bir HashMap üzerinde iterasyon yapmak eninde sonunda CME fırlatmalıdır")
                .isTrue();
    }

    @Test
    void shouldNotThrowConcurrentModificationExceptionWithConcurrentHashMap() throws InterruptedException {
        ConcurrentHashMapCacheService service = new ConcurrentHashMapCacheService();
        for (int i = 0; i < 1000; i++) {
            service.put("key-" + i, "value-" + i);
        }

        AtomicBoolean sawException = new AtomicBoolean(false);
        AtomicBoolean stop = new AtomicBoolean(false);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> {
            while (!stop.get()) {
                try {
                    for (String value : service.rawMap().values()) {
                        // sadece iterasyon yapılıyor
                    }
                } catch (RuntimeException e) {
                    sawException.set(true);
                }
            }
        });
        executor.submit(() -> {
            int i = 1000;
            while (!stop.get()) {
                service.put("key-" + (i++), "value");
            }
        });

        Thread.sleep(500);
        stop.set(true);
        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);

        assertThat(sawException.get()).isFalse();
    }

    @Test
    void shouldComputeValueMultipleTimesWithCheckThenActPattern() throws InterruptedException {
        CompoundCheckThenActCacheService service = new CompoundCheckThenActCacheService();
        runConcurrentFirstAccess(() -> service.getOrCompute("shared-key", () -> expensiveValue()));

        assertThat(service.computationCount())
                .as("ConcurrentHashMap üzerinde check-then-act, maliyetli hesaplamanın birden fazla kez çalışmasını engellemez")
                .isGreaterThan(1);
    }

    @Test
    void shouldComputeValueExactlyOnceWithComputeIfAbsent() throws InterruptedException {
        AtomicComputeIfAbsentCacheService service = new AtomicComputeIfAbsentCacheService();
        runConcurrentFirstAccess(() -> service.getOrCompute("shared-key", () -> expensiveValue()));

        assertThat(service.computationCount())
                .as("computeIfAbsent, mapping fonksiyonunun her key için tam olarak bir kez çalışmasını garanti eder")
                .isEqualTo(1);
    }

    @Test
    void shouldNeverThrowConcurrentModificationExceptionWithCopyOnWriteArrayList() throws InterruptedException {
        SafeIterationListHolder holder = new SafeIterationListHolder();
        for (int i = 0; i < 100; i++) {
            holder.add("listener-" + i);
        }

        AtomicBoolean sawException = new AtomicBoolean(false);
        AtomicBoolean stop = new AtomicBoolean(false);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> {
            while (!stop.get()) {
                try {
                    for (String listener : holder.listeners()) {
                        // sadece bir snapshot üzerinde iterasyon yapılıyor
                    }
                } catch (RuntimeException e) {
                    sawException.set(true);
                }
            }
        });
        executor.submit(() -> {
            int i = 100;
            while (!stop.get()) {
                holder.add("listener-" + (i++));
            }
        });

        Thread.sleep(500);
        stop.set(true);
        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);

        assertThat(sawException.get()).isFalse();
    }

    private static void runConcurrentFirstAccess(Runnable access) throws InterruptedException {
        int callers = 20;
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(callers);
        for (int i = 0; i < callers; i++) {
            executor.submit(() -> {
                ready.countDown();
                awaitUninterruptibly(start);
                access.run();
                done.countDown();
            });
        }
        ready.await();
        start.countDown();
        done.await(10, TimeUnit.SECONDS);
        executor.shutdown();
    }

    // ---------- BlockingQueue: ArrayBlockingQueue (sınırlı) vs LinkedBlockingQueue (varsayılan sınırsız) ----------

    @Test
    void shouldBlockProducerWhenArrayBlockingQueueIsFull() throws Exception {
        BlockingQueueProducerConsumer producerConsumer = new BlockingQueueProducerConsumer(new ArrayBlockingQueue<>(2));
        producerConsumer.produce(1);
        producerConsumer.produce(2); // kapasite (2) doldu

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> blockedProduce = executor.submit(() -> {
                try {
                    producerConsumer.produce(3); // BLOKLAMALI - kuyruk dolu
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            assertThatThrownBy(() -> blockedProduce.get(200, TimeUnit.MILLISECONDS))
                    .as("kuyruk dolu iken put(), consume() ile yer açılana kadar BLOKLAMALI - hemen dönmemeli")
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);

            producerConsumer.consume(); // yer aç
            blockedProduce.get(2, TimeUnit.SECONDS); // artık tamamlanmalı
            assertThat(producerConsumer.size()).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldNeverBlockProducerWithDefaultUnboundedLinkedBlockingQueue() throws Exception {
        // LinkedBlockingQueue()'nun parametresiz constructor'ı Integer.MAX_VALUE kapasiteli -
        // ExecutorService'in newFixedThreadPool'daki "gizli sınırsız kuyruk" tuzağıyla AYNI kök neden.
        BlockingQueueProducerConsumer producerConsumer = new BlockingQueueProducerConsumer(new LinkedBlockingQueue<>());
        for (int i = 0; i < 10_000; i++) {
            producerConsumer.produce(i); // hiçbiri bloklamaz - "sınırlı" görünen bir kuyruk aslında sınırsız
        }
        assertThat(producerConsumer.size()).isEqualTo(10_000);
    }

    @Test
    void shouldReturnFalseImmediatelyWithNonBlockingOfferWhenFull() {
        BlockingQueueProducerConsumer producerConsumer = new BlockingQueueProducerConsumer(new ArrayBlockingQueue<>(1));
        assertThat(producerConsumer.tryProduceNonBlocking(1)).isTrue();
        assertThat(producerConsumer.tryProduceNonBlocking(2))
                .as("offer(), put()'un aksine hiçbir zaman bloklamaz - kapasite doluysa hemen false döner")
                .isFalse();
    }

    private static String expensiveValue() {
        try {
            Thread.sleep(20); // eşzamanlı ilk erişimlerin güvenilir şekilde çakışması için yeterince geniş bir pencere
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "computed";
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
