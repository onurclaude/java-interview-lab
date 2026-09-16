package com.interviewlab.concurrency;

import com.interviewlab.labrunner.LabRunnerPrint;

import com.interviewlab.concurrency.atomic.AbaProblemDemo;
import com.interviewlab.concurrency.race.bad.UnsynchronizedIntCounter;
import com.interviewlab.concurrency.synchronization.good.IntrinsicMonitorCounter;
import com.interviewlab.concurrency.synchronization.good.ReadWriteLockCache;
import com.interviewlab.concurrency.synchronization.good.StampedLockPoint;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.concurrent.locks.ReentrantLock;

/** IntelliJ'de sağ tık -> Run/Debug. Postman/HTTP GEREKMEZ - Spring de GEREKMEZ (saf POJO). */
public final class LockingPrimitivesLabRunner {

    public static void main(String[] args) throws InterruptedException {
        synchronizedDemo();
        reentrantLockDemo();
        readWriteLockDemo();
        stampedLockDemo();
        abaDemo();
    }

    private static void synchronizedDemo() throws InterruptedException {
        LabRunnerPrint.banner("SYNCHRONIZED — mutual exclusion");
        UnsynchronizedIntCounter bad = new UnsynchronizedIntCounter();
        runConcurrently(10, () -> { for (int i = 0; i < 2000; i++) bad.increment(); }); // <- BREAKPOINT 1
        LabRunnerPrint.fact("BAD actual/expected", bad.get() + "/" + 20000);

        IntrinsicMonitorCounter good = new IntrinsicMonitorCounter();
        runConcurrently(10, () -> { for (int i = 0; i < 2000; i++) good.incrementViaMethod(); }); // <- BREAKPOINT 2: diğer thread'ler BLOCKED
        LabRunnerPrint.fact("GOOD actual/expected", good.get() + "/" + 20000);

        LabRunnerPrint.section("BREAKPOINT");
        LabRunnerPrint.line("BREAKPOINT 2'de dur, Threads panelinde 9/10 thread'in BLOCKED olduğunu gör.");
    }

    private static void reentrantLockDemo() throws InterruptedException {
        LabRunnerPrint.banner("REENTRANT LOCK — kilit yaşam döngüsü");
        int threads = 4;
        List<String> lifecycleLog = new CopyOnWriteArrayList<>();
        AtomicInteger maxConcurrentHolders = new AtomicInteger();
        runLockLifecycle(threads, null, lifecycleLog, maxConcurrentHolders); // BAD
        LabRunnerPrint.fact("BAD maxConcurrentHolders", maxConcurrentHolders.get());

        lifecycleLog.clear();
        maxConcurrentHolders.set(0);
        runLockLifecycle(threads, new ReentrantLock(), lifecycleLog, maxConcurrentHolders); // GOOD <- BREAKPOINT 3
        LabRunnerPrint.fact("GOOD maxConcurrentHolders (HER ZAMAN 1)", maxConcurrentHolders.get());
    }

    private static void runLockLifecycle(int threads, ReentrantLock lock, List<String> lifecycleLog, AtomicInteger maxConcurrentHolders) throws InterruptedException {
        AtomicInteger currentHolders = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        try {
            for (int i = 0; i < threads; i++) {
                String label = "worker-" + i;
                executor.submit(() -> {
                    ready.countDown();
                    awaitUninterruptibly(start);
                    lifecycleLog.add(label + " ATTEMPTING_LOCK");
                    if (lock != null) lock.lock();
                    try {
                        lifecycleLog.add(label + " LOCK_ACQUIRED"); // <- BREAKPOINT 3
                        int now = currentHolders.incrementAndGet();
                        maxConcurrentHolders.updateAndGet(prev -> Math.max(prev, now));
                        sleep(20);
                        currentHolders.decrementAndGet();
                    } finally {
                        lifecycleLog.add(label + " LOCK_RELEASED");
                        if (lock != null) lock.unlock();
                        done.countDown();
                    }
                });
            }
            ready.await();
            start.countDown();
            done.await(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
        }
    }

    private static void readWriteLockDemo() throws InterruptedException {
        LabRunnerPrint.banner("READ WRITE LOCK — eşzamanlı okuyucular");
        ReadWriteLockCache cache = new ReadWriteLockCache();
        cache.put("k", "v1");
        int readerCount = 5;
        CountDownLatch allHolding = new CountDownLatch(readerCount);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(readerCount);
        Instant start = Instant.now();
        try {
            for (int i = 0; i < readerCount; i++) {
                executor.submit(() -> cache.withReadLockHeld(() -> { // <- BREAKPOINT
                    allHolding.countDown();
                    awaitUninterruptibly(release);
                }));
            }
            boolean allConcurrent = allHolding.await(2, TimeUnit.SECONDS);
            long millis = Duration.between(start, Instant.now()).toMillis();
            release.countDown();
            executor.shutdown();
            executor.awaitTermination(2, TimeUnit.SECONDS);
            LabRunnerPrint.fact("allReadersAcquiredConcurrently", allConcurrent);
            LabRunnerPrint.fact("millisForAllReadersToAcquire", millis);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void stampedLockDemo() {
        LabRunnerPrint.banner("STAMPED LOCK — optimistic read");
        StampedLockPoint point = new StampedLockPoint();
        point.move(3, 4);
        double distance = point.distanceFromOrigin(); // <- BREAKPOINT: tryOptimisticRead()+validate() adımlarını izle
        LabRunnerPrint.fact("distanceFromOrigin (beklenen 5.0)", distance);
    }

    private static void abaDemo() {
        LabRunnerPrint.banner("ABA PROBLEM — AtomicReference vs AtomicStampedReference");
        AtomicReference<String> ref = new AtomicReference<>();
        boolean badCasSucceeded = AbaProblemDemo.casSucceedsDespiteIntermediateChange(ref, "A", "B"); // <- BREAKPOINT 1
        LabRunnerPrint.fact("BAD casSucceededDespiteIntermediateChange", badCasSucceeded);

        AtomicStampedReference<String> stampedRef = new AtomicStampedReference<>(null, -1);
        boolean goodCasSucceeded = AbaProblemDemo.stampedCasDetectsIntermediateChange(stampedRef, "A", "B"); // <- BREAKPOINT 2
        LabRunnerPrint.fact("GOOD casSucceededDespiteIntermediateChange", goodCasSucceeded);

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("Saf AtomicReference CAS'i SADECE değere bakar - A->B->A geçişini fark edemez (BAD: true,");
        LabRunnerPrint.line("yanlış pozitif). AtomicStampedReference bir DAMGA ekler - aynı değer görünse bile damga");
        LabRunnerPrint.line("uyuşmazlığı geçişi açığa çıkarır (GOOD: false, doğru red).");
    }

    private static void runConcurrently(int threadCount, Runnable task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threadCount);
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    awaitUninterruptibly(start);
                    try {
                        task.run();
                    } finally {
                        done.countDown();
                    }
                });
            }
            ready.await();
            start.countDown();
            done.await(60, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
