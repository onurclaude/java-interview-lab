package com.interviewlab.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.interviewlab.concurrency.atomic.AbaProblemDemo;
import com.interviewlab.concurrency.atomic.AtomicCounterService;
import com.interviewlab.concurrency.volatiletopic.bad.VolatileCounter;
import com.interviewlab.concurrency.volatiletopic.good.ShutdownFlagWorker;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;
import org.junit.jupiter.api.Test;

/** Yazı ve mülakat cevapları için docs/atomic.md dosyasına bakın. */
class VolatileAndAtomicTest {

    private static final int INCREMENTS = 2000;
    private static final int THREADS = 16;

    @Test
    void shouldLoseIncrementOperationsWithVolatileCounter() throws InterruptedException {
        VolatileCounter counter = new VolatileCounter();
        runConcurrentIncrements(counter::increment);
        assertThat(counter.get())
                .as("volatile, atomikliği değil görünürlüğü garanti eder - bazı artışlar kaybolur")
                .isLessThan(INCREMENTS);
    }

    @Test
    void shouldProduceCorrectCountWithAtomicInteger() throws InterruptedException {
        AtomicCounterService counter = new AtomicCounterService();
        runConcurrentIncrements(counter::increment);
        assertThat(counter.get())
                .as("CAS tabanlı increment atomiktir - her artış sayılır")
                .isEqualTo(INCREMENTS);
    }

    @Test
    void shouldMakeShutdownFlagVisibleAcrossThreads() throws InterruptedException {
        ShutdownFlagWorker worker = new ShutdownFlagWorker();
        Thread workerThread = new Thread(worker);
        workerThread.start();

        await().atMost(Duration.ofSeconds(2)).until(() -> worker.getIterationsCompleted() > 0);
        worker.requestStop();

        workerThread.join(2000);
        assertThat(workerThread.isAlive())
                .as("volatile flag, worker thread'e hızlı bir şekilde görünür hale gelmelidir")
                .isFalse();
    }

    @Test
    void shouldSucceedFalsePositivelyWithPlainAtomicReferenceUnderAbaInterleaving() {
        boolean casSucceeded = AbaProblemDemo.casSucceedsDespiteIntermediateChange(
                new AtomicReference<>(), "A", "B");
        assertThat(casSucceeded)
                .as("düz bir CAS, 'hiç değişmedi' ile 'değişip tekrar eski haline döndü' durumlarını ayırt edemez")
                .isTrue();
    }

    @Test
    void shouldDetectAbaInterleavingWithAtomicStampedReference() {
        boolean casSucceeded = AbaProblemDemo.stampedCasDetectsIntermediateChange(
                new AtomicStampedReference<>(null, 0), "A", "B");
        assertThat(casSucceeded)
                .as("değer aynı görünse bile stamp değişti, bu yüzden CAS doğru şekilde başarısız olur")
                .isFalse();
    }

    private static void runConcurrentIncrements(Runnable increment) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch done = new CountDownLatch(INCREMENTS);
        for (int i = 0; i < INCREMENTS; i++) {
            executor.submit(() -> {
                increment.run();
                done.countDown();
            });
        }
        done.await(10, TimeUnit.SECONDS);
        executor.shutdown();
    }
}
