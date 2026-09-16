package com.interviewlab.concurrency.volatiletopic;

import com.interviewlab.labrunner.LabRunnerPrint;

import com.interviewlab.concurrency.volatiletopic.bad.VolatileCounter;
import com.interviewlab.concurrency.volatiletopic.good.ShutdownFlagWorker;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** IntelliJ'de sağ tık -> Run/Debug. Postman/HTTP GEREKMEZ - Spring de GEREKMEZ (saf POJO). */
public final class VolatileLabRunner {

    public static void main(String[] args) throws InterruptedException {
        LabRunnerPrint.banner("VOLATILE — visibility vs atomicity");

        VolatileCounter counter = new VolatileCounter();
        int threads = 20;
        int increments = 2000;
        runConcurrently(threads, () -> {
            for (int i = 0; i < increments; i++) {
                counter.increment(); // <- BREAKPOINT 1: volatile OLMASINA RAĞMEN count++ hâlâ 3 bytecode adımı
            }
        });
        int expected = threads * increments;
        int actual = counter.get();
        LabRunnerPrint.fact("misconception-check expected/actual", expected + "/" + actual);
        LabRunnerPrint.fact("stillRaced", actual != expected);

        ShutdownFlagWorker worker = new ShutdownFlagWorker();
        Thread workerThread = new Thread(worker::run, "lab-volatile-worker");
        workerThread.start();
        Thread.sleep(50);
        worker.requestStop(); // <- BREAKPOINT 2: TEK bir volatile atama (compound değil)
        workerThread.join(2000);
        LabRunnerPrint.fact("workerStoppedWithinTimeout", !workerThread.isAlive());
        LabRunnerPrint.fact("iterationsCompletedBeforeStop", worker.getIterationsCompleted());

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("volatile SADECE görünürlük garanti eder - count++ gibi compound (oku-değiştir-yaz)");
        LabRunnerPrint.line("operasyonlar hâlâ race'e girer. requestStop() gibi TEK bir atama için ise volatile");
        LabRunnerPrint.line("TEK BAŞINA yeterlidir.");
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
}
