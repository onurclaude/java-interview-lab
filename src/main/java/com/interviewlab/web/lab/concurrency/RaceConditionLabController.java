package com.interviewlab.web.lab.concurrency;

import com.interviewlab.concurrency.atomic.AtomicCounterService;
import com.interviewlab.concurrency.race.bad.UnsynchronizedIntCounter;
import com.interviewlab.web.lab.LabLog;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PRIMARY interactive surface — bkz. docs/DEBUGGER_LABS.md "RACE CONDITION" bölümü.
 * {@code threads} thread'in her biri {@code increments} kez {@code increment()} çağırır;
 * TÜMÜ önce hazır olur, sonra AYNI ANDA serbest bırakılır (CountDownLatch ready/start
 * deseni) - şansa/timing'e güvenmek yerine gerçek, tekrarlanabilir çekişme (contention)
 * garanti edilir. Kaybedilen güncelleme sayısı NONDETERMİNİSTİKTİR (koşuya göre değişir) -
 * bu proje kasıtlı olarak sabit bir "yanlış" sayı ASSERT ETMEZ, sadece 0'dan büyük
 * olduğunu (ya da GOOD için tam olarak 0 olduğunu) doğrular.
 */
@RestController
@RequestMapping("/api/labs/concurrency/counter")
public class RaceConditionLabController {

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "RACE_CONDITION");
        body.put("action", "RESET");
        body.put("breakpointHint", "UnsynchronizedIntCounter.increment() içine breakpoint koy - IntelliJ'de "
                + "Threads panelinde birden fazla thread'in AYNI satırda (count++) durduğunu göreceksin.");
        body.put("nextStep", "POST /api/labs/concurrency/counter/bad?threads=20&increments=10000");
        return body;
    }

    @PostMapping("/bad")
    public Map<String, Object> bad(@RequestParam(defaultValue = "20") int threads,
                                    @RequestParam(defaultValue = "10000") int increments) throws InterruptedException {
        LabLog.banner("RACE CONDITION", "BAD (senkronizasyon yok - count++)");
        UnsynchronizedIntCounter counter = new UnsynchronizedIntCounter();
        runConcurrently(threads, () -> {
            for (int i = 0; i < increments; i++) {
                counter.increment();
            }
        });
        int expected = threads * increments;
        int actual = counter.get();
        int lostUpdates = expected - actual;
        LabLog.line("{} thread x {} artış = beklenen {}, gerçek {} (kaybolan: {})", threads, increments, expected, actual, lostUpdates);
        LabLog.lesson("count++ atomik değildir (oku-artır-yaz) - eşzamanlı thread'ler birbirinin yazmasını "
                + "kaybederek üzerine yazdı. Kaybolan güncelleme sayısı koşudan koşuya DEĞİŞİR (nondeterministic) - "
                + "bu, race condition'ların production'da neden bu kadar zor debug edildiğini gösterir.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scenario", "RACE_CONDITION");
        body.put("implementation", "BAD");
        body.put("threads", threads);
        body.put("incrementsPerThread", increments);
        body.put("expected", expected);
        body.put("actual", actual);
        body.put("lostUpdates", lostUpdates);
        body.put("raceObserved", lostUpdates > 0);
        body.put("lesson", "count++ oku-artır-yaz'dır, atomik değildir - eşzamanlı thread'ler arasında güncellemeler kayboldu.");
        body.put("nextStep", "POST /api/labs/concurrency/counter/good?threads=" + threads + "&increments=" + increments);
        return body;
    }

    @PostMapping("/good")
    public Map<String, Object> good(@RequestParam(defaultValue = "20") int threads,
                                     @RequestParam(defaultValue = "10000") int increments) throws InterruptedException {
        LabLog.banner("RACE CONDITION", "GOOD (AtomicInteger)");
        AtomicCounterService counter = new AtomicCounterService();
        runConcurrently(threads, () -> {
            for (int i = 0; i < increments; i++) {
                counter.increment();
            }
        });
        int expected = threads * increments;
        int actual = counter.get();
        LabLog.line("{} thread x {} artış = beklenen {}, gerçek {} - HİÇ kayıp yok", threads, increments, expected, actual);
        LabLog.lesson("AtomicInteger.incrementAndGet(), CAS (compare-and-swap) donanım talimatı ile oku-artır-yaz'ı "
                + "TEK, bölünemez bir adıma indirir - iki thread ASLA aynı eski değer üzerinden ilerleyip birbirini ezemez.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scenario", "RACE_CONDITION");
        body.put("implementation", "GOOD");
        body.put("threads", threads);
        body.put("incrementsPerThread", increments);
        body.put("expected", expected);
        body.put("actual", actual);
        body.put("lostUpdates", 0);
        body.put("raceObserved", false);
        body.put("usedImplementation", "AtomicInteger");
        body.put("lesson", "AtomicInteger, CAS ile atomik increment sağlar - lost update fiziksel olarak imkansız.");
        return body;
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "RACE_CONDITION");
        body.put("note", "Bu lab'ın kalıcı durumu yok - her çağrı taze counter'larla bağımsız bir koşu yapar.");
        return body;
    }

    static void runConcurrently(int threadCount, Runnable task) throws InterruptedException {
        // Faz 1'in kendi RaceConditionTest deadlock dersi: havuz taskCount kadar OLMALI -
        // taskCount > poolSize olursa, ilk poolSize görev "start" latch'ini bekleyerek
        // thread'leri işgal eder, kalan görevler hiçbir zaman bir thread bulamaz.
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
}
