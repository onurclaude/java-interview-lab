package com.interviewlab.web.lab.concurrency;

import com.interviewlab.concurrency.atomic.AbaProblemDemo;
import com.interviewlab.concurrency.race.bad.UnsynchronizedIntCounter;
import com.interviewlab.concurrency.synchronization.good.IntrinsicMonitorCounter;
import com.interviewlab.concurrency.synchronization.good.ReadWriteLockCache;
import com.interviewlab.concurrency.synchronization.good.StampedLockPoint;
import com.interviewlab.web.lab.LabLog;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PRIMARY interactive surface — synchronized/ReentrantLock/ReadWriteLock/StampedLock.
 * "Mutual exclusion gerçekten sağlandı mı?" sorusu FAKE/varsayılan bir değerle değil,
 * {@code maxConcurrentHolders} - kritik bölgeyi GERÇEKTEN aynı anda tutan thread sayısının
 * ölçülen tepe değeri - ile cevaplanır (bkz. docs/DEBUGGER_LABS.md "LOCK LAB").
 */
@RestController
@RequestMapping("/api/labs/concurrency")
public class LockingPrimitivesLabController {

    @PostMapping("/synchronized/bad")
    public Map<String, Object> synchronizedBad(@RequestParam(defaultValue = "10") int threads,
                                                 @RequestParam(defaultValue = "2000") int increments) throws InterruptedException {
        LabLog.banner("SYNCHRONIZED", "BAD (kilit yok)");
        UnsynchronizedIntCounter counter = new UnsynchronizedIntCounter();
        RaceConditionLabController.runConcurrently(threads, () -> {
            for (int i = 0; i < increments; i++) {
                counter.increment();
            }
        });
        int expected = threads * increments;
        LabLog.lesson("Hiçbir mutual exclusion yok - count++ eşzamanlı thread'ler arasında yarıştı.");
        return raceResult("SYNCHRONIZED", "BAD", threads, increments, expected, counter.get());
    }

    @PostMapping("/synchronized/good")
    public Map<String, Object> synchronizedGood(@RequestParam(defaultValue = "10") int threads,
                                                  @RequestParam(defaultValue = "2000") int increments) throws InterruptedException {
        LabLog.banner("SYNCHRONIZED", "GOOD (intrinsic monitor)");
        IntrinsicMonitorCounter counter = new IntrinsicMonitorCounter();
        RaceConditionLabController.runConcurrently(threads, () -> {
            for (int i = 0; i < increments; i++) {
                counter.incrementViaMethod(); // <- BREAKPOINT: Threads panelinde bekleyen thread'lerin BLOCKED olduğunu gör
            }
        });
        int expected = threads * increments;
        LabLog.lesson("synchronized, aynı monitor'u (this) bekleyen tüm thread'leri BLOCKED durumuna sokarak seri hale getirir - hiç kayıp yok.");
        return raceResult("SYNCHRONIZED", "GOOD", threads, increments, expected, counter.get());
    }

    private static Map<String, Object> raceResult(String scenario, String impl, int threads, int increments, int expected, int actual) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scenario", scenario);
        body.put("implementation", impl);
        body.put("threads", threads);
        body.put("incrementsPerThread", increments);
        body.put("expected", expected);
        body.put("actual", actual);
        body.put("lostUpdates", expected - actual);
        body.put("raceObserved", actual != expected);
        return body;
    }

    // ---------- ReentrantLock: gerçek kilit yaşam döngüsü logu + ölçülen mutual exclusion ----------

    @PostMapping("/reentrant-lock/bad")
    public Map<String, Object> reentrantLockBad(@RequestParam(defaultValue = "4") int threads) throws InterruptedException {
        LabLog.banner("REENTRANT LOCK", "BAD (kilit yok)");
        return runLockLifecycleDemo(threads, null);
    }

    @PostMapping("/reentrant-lock/good")
    public Map<String, Object> reentrantLockGood(@RequestParam(defaultValue = "4") int threads) throws InterruptedException {
        LabLog.banner("REENTRANT LOCK", "GOOD (ReentrantLock)");
        return runLockLifecycleDemo(threads, new ReentrantLock());
    }

    /**
     * {@code lock} null ise KASITLI OLARAK hiç kilitlenmez (BAD) - {@code maxConcurrentHolders}
     * GERÇEKTEN 1'in üzerine çıkabilir (kritik bölgede kasıtlı bir {@code Thread.sleep(20)}
     * var, bu yüzden çakışma şansa değil, garantiye yakın). {@code lock} verilmişse (GOOD),
     * her thread ATTEMPTING_LOCK -> LOCK_ACQUIRED -> WORK -> LOCK_RELEASED adımlarını
     * loglar ve {@code maxConcurrentHolders} GERÇEKTEN her zaman tam 1 ölçülür.
     */
    private Map<String, Object> runLockLifecycleDemo(int threads, ReentrantLock lock) throws InterruptedException {
        List<String> lifecycleLog = new CopyOnWriteArrayList<>();
        AtomicInteger currentHolders = new AtomicInteger();
        AtomicInteger maxConcurrentHolders = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        try {
            for (int i = 0; i < threads; i++) {
                String threadLabel = "worker-" + i;
                executor.submit(() -> {
                    ready.countDown();
                    awaitUninterruptibly(start);
                    lifecycleLog.add(threadLabel + " ATTEMPTING_LOCK");
                    if (lock != null) {
                        lock.lock();
                    }
                    try {
                        lifecycleLog.add(threadLabel + " LOCK_ACQUIRED");
                        int concurrentNow = currentHolders.incrementAndGet();
                        maxConcurrentHolders.updateAndGet(prevMax -> Math.max(prevMax, concurrentNow));
                        lifecycleLog.add(threadLabel + " WORK");
                        sleep(20); // kritik bölgeyi kasıtlı olarak genişletir - BAD'de çakışmayı garantiye yakınlaştırır
                        currentHolders.decrementAndGet();
                    } finally {
                        lifecycleLog.add(threadLabel + " LOCK_RELEASED");
                        if (lock != null) {
                            lock.unlock();
                        }
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

        boolean mutualExclusionHeld = maxConcurrentHolders.get() <= 1;
        LabLog.line("threads={}, maxConcurrentHolders={}, mutualExclusionHeld={}", threads, maxConcurrentHolders.get(), mutualExclusionHeld);
        for (String line : lifecycleLog) {
            LabLog.line("{}", line);
        }
        LabLog.lesson(lock == null
                ? "Kilit yoktu - birden fazla thread AYNI ANDA kritik bölgede bulundu (maxConcurrentHolders > 1 olabilir)."
                : "ReentrantLock, her seferinde SADECE BİR thread'in kritik bölgede olmasını garanti etti (maxConcurrentHolders GERÇEKTEN 1 ölçüldü).");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scenario", "REENTRANT_LOCK");
        body.put("implementation", lock == null ? "BAD" : "GOOD");
        body.put("threads", threads);
        body.put("lifecycleLog", lifecycleLog);
        body.put("maxConcurrentHolders", maxConcurrentHolders.get());
        body.put("mutualExclusionHeld", mutualExclusionHeld);
        body.put("lesson", lock == null
                ? "Kilit olmadan kritik bölgeye eşzamanlı giriş MÜMKÜNDÜR."
                : "lock.lock()/unlock() (try/finally içinde) kritik bölgeyi her zaman tek thread'e ayırdı.");
        return body;
    }

    // ---------- ReadWriteLock: eşzamanlı okuyucular, dışlayıcı yazıcı ----------

    @PostMapping("/read-write-lock/demo")
    public Map<String, Object> readWriteLockDemo(@RequestParam(defaultValue = "5") int readerCount) throws InterruptedException {
        LabLog.banner("READ WRITE LOCK", "eşzamanlı okuyucular vs dışlayıcı yazıcı");
        ReadWriteLockCache cache = new ReadWriteLockCache();
        cache.put("k", "v1");

        CountDownLatch allReadersHoldingLock = new CountDownLatch(readerCount);
        CountDownLatch releaseReaders = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(readerCount);
        Instant readersStart = Instant.now();
        try {
            for (int i = 0; i < readerCount; i++) {
                executor.submit(() -> cache.withReadLockHeld(() -> {
                    allReadersHoldingLock.countDown();
                    awaitUninterruptibly(releaseReaders);
                }));
            }
            boolean allReadersConcurrent = allReadersHoldingLock.await(2, TimeUnit.SECONDS);
            long millisForAllReadersToAcquire = Duration.between(readersStart, Instant.now()).toMillis();
            releaseReaders.countDown();
            executor.shutdown();
            executor.awaitTermination(2, TimeUnit.SECONDS);

            LabLog.lesson(readerCount + " okuyucu, HEPSİ AYNI ANDA read lock'u tuttu (" + millisForAllReadersToAcquire
                    + "ms içinde) - bir ReentrantLock/synchronized ile bu SIRAYLA olurdu.");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("scenario", "READ_WRITE_LOCK");
            body.put("readerCount", readerCount);
            body.put("allReadersAcquiredConcurrently", allReadersConcurrent);
            body.put("millisForAllReadersToAcquireLock", millisForAllReadersToAcquire);
            body.put("lesson", "Tüm okuyucular AYNI ANDA read lock'u tuttu - okuma-ağırlıklı erişimde plain lock'tan daha verimli.");
            return body;
        } finally {
            executor.shutdownNow();
        }
    }

    // ---------- StampedLock: optimistic read ----------

    @PostMapping("/stamped-lock/demo")
    public Map<String, Object> stampedLockDemo() {
        LabLog.banner("STAMPED LOCK", "optimistic read");
        StampedLockPoint point = new StampedLockPoint();
        point.move(3, 4);
        double distance = point.distanceFromOrigin(); // <- BREAKPOINT: tryOptimisticRead() + validate() adımlarını izle

        LabLog.lesson("distanceFromOrigin(), hiç kilit almadan (tryOptimisticRead) x/y'yi okudu, sonra "
                + "validate(stamp) ile arada bir yazma OLMADIĞINI doğruladı - kilitsiz, neredeyse bedava bir okuma.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scenario", "STAMPED_LOCK");
        body.put("move", Map.of("deltaX", 3, "deltaY", 4));
        body.put("distanceFromOrigin", distance);
        body.put("expectedDistance", 5.0);
        body.put("lesson", "Optimistic read, kilit almadan x/y okudu ve validate() ile arada yazma olmadığını doğruladı.");
        return body;
    }

    // ---------- ABA Problem: AtomicReference CAS vs AtomicStampedReference ----------

    @PostMapping("/aba/bad")
    public Map<String, Object> abaBad() {
        LabLog.banner("ABA PROBLEM", "BAD (AtomicReference - saf kimlik/değer karşılaştırması)");
        AtomicReference<String> ref = new AtomicReference<>();
        boolean casSucceeded = AbaProblemDemo.casSucceedsDespiteIntermediateChange(ref, "A", "B"); // <- BREAKPOINT: A->B->A sonrası CAS
        LabLog.line("Değer A->B->A şeklinde değişti (aradaki mutasyon), yine de compareAndSet(\"A\", ...) BAŞARILI oldu: {}", casSucceeded);
        LabLog.lesson("AtomicReference.compareAndSet(), SADECE mevcut değeri beklenen değerle karşılaştırır - "
                + "değerin ARADA B'ye gidip A'ya GERİ DÖNDÜĞÜNÜ ASLA BİLEMEZ. 'Değer hâlâ A' ile 'değer hiç "
                + "değişmedi' AYNI ŞEY DEĞİLDİR, ama saf CAS bunu AYIRT EDEMEZ.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scenario", "ABA_PROBLEM");
        body.put("implementation", "BAD");
        body.put("valueSequence", List.of("A", "B", "A"));
        body.put("casSucceededDespiteIntermediateChange", casSucceeded);
        body.put("problem", "CAS, aradaki A->B->A mutasyonunu TESPİT EDEMEDİ - saf kimlik/değer karşılaştırması bunun için YETERSİZ.");
        body.put("nextStep", "POST /api/labs/concurrency/aba/good");
        return body;
    }

    @PostMapping("/aba/good")
    public Map<String, Object> abaGood() {
        LabLog.banner("ABA PROBLEM", "GOOD (AtomicStampedReference - değer + damga)");
        AtomicStampedReference<String> ref = new AtomicStampedReference<>(null, -1);
        boolean casSucceeded = AbaProblemDemo.stampedCasDetectsIntermediateChange(ref, "A", "B"); // <- BREAKPOINT: damga artık UYUŞMUYOR
        LabLog.line("Değer A->B->A şeklinde değişti (damga her mutasyonda arttı), compareAndSet(\"A\", ..., eskiDamga, ...) BAŞARISIZ oldu: {}", casSucceeded);
        LabLog.lesson("AtomicStampedReference, her CAS'te değerin YANI SIRA bir damganın da eşleşmesini ister - "
                + "değer görünüşte AYNI (A) olsa bile, damga aradaki B->A geçişini AÇIĞA ÇIKARIR ve CAS DOĞRU "
                + "şekilde BAŞARISIZ olur.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scenario", "ABA_PROBLEM");
        body.put("implementation", "GOOD");
        body.put("valueSequence", List.of("A", "B", "A"));
        body.put("casSucceededDespiteIntermediateChange", casSucceeded);
        body.put("lesson", "AtomicStampedReference'ın damgası, A->B->A geçişini AÇIĞA ÇIKARDI - CAS DOĞRU şekilde başarısız oldu.");
        return body;
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "CONCURRENCY_PRIMITIVES");
        body.put("note", "Bu lab'ların kalıcı durumu yok - her çağrı bağımsız bir koşu yapar.");
        return body;
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
