package com.interviewlab.concurrency.race;

import com.interviewlab.labrunner.LabRunnerPrint;

import com.interviewlab.concurrency.atomic.AtomicCounterService;
import com.interviewlab.concurrency.race.bad.UnsynchronizedIntCounter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** IntelliJ'de sağ tık -> Run/Debug. Postman/HTTP GEREKMEZ - Spring de GEREKMEZ (saf POJO). */
public final class RaceConditionLabRunner {

    public static void main(String[] args) throws InterruptedException {
        LabRunnerPrint.banner("RACE CONDITION — count++ atomik değildir");

        int threads = 20;
        int increments = 10000;

        UnsynchronizedIntCounter badCounter = new UnsynchronizedIntCounter();
        runConcurrently(threads, () -> {
            for (int i = 0; i < increments; i++) {
                badCounter.increment(); // <- BREAKPOINT 1: Threads panelinde birden fazla thread'in AYNI satırda durduğunu gör
            }
        });
        int expected = threads * increments;
        int badActual = badCounter.get();
        LabRunnerPrint.fact("BAD expected", expected);
        LabRunnerPrint.fact("BAD actual (nondeterministic)", badActual);
        LabRunnerPrint.fact("BAD lostUpdates", expected - badActual);

        AtomicCounterService goodCounter = new AtomicCounterService();
        runConcurrently(threads, () -> {
            for (int i = 0; i < increments; i++) {
                goodCounter.increment(); // <- BREAKPOINT 2: AtomicInteger.incrementAndGet() - CAS
            }
        });
        int goodActual = goodCounter.get();
        LabRunnerPrint.fact("GOOD expected", expected);
        LabRunnerPrint.fact("GOOD actual (HER ZAMAN tam)", goodActual);

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("count++ üç bytecode adımıdır (getfield/iadd/putfield) - atomik DEĞİLDİR. AtomicInteger,");
        LabRunnerPrint.line("donanım CAS (compare-and-swap) talimatıyla bunu TEK adıma indirir.");

        LabRunnerPrint.section("BREAKPOINT");
        LabRunnerPrint.line("BREAKPOINT 1'de dur, Threads panelini aç - 20 worker thread'in bir kısmının AYNI");
        LabRunnerPrint.line("satırda (count++) RUNNABLE olduğunu gör. BREAKPOINT 2'de AtomicInteger'ın iç CAS");
        LabRunnerPrint.line("döngüsünü (Evaluate Expression: goodCounter üzerinde) incele.");

        LabRunnerPrint.section("TRY");
        LabRunnerPrint.line("threads/increments değerlerini artır - BAD'deki lostUpdates SIFIRDAN BÜYÜK kalmaya");
        LabRunnerPrint.line("devam eder ama TAM SAYISI her koşuda DEĞİŞİR (nondeterministic); GOOD HER ZAMAN tamdır.");
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
