package com.interviewlab.labrunner;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** IntelliJ'de sağ tık -> Run/Debug. Threads panelinde "VirtualThread[#N]/runnable@ForkJoinPool..." isimlerini ara. */
public final class VirtualThreadLabRunner {

    public static void main(String[] args) throws InterruptedException {
        LabRunnerPrint.banner("VIRTUAL THREADS (Java 21) vs PLATFORM THREADS");

        Thread virtual = Thread.ofVirtual().unstarted(() -> { });
        Thread platform = Thread.ofPlatform().unstarted(() -> { });
        LabRunnerPrint.fact("virtual.isVirtual()", virtual.isVirtual());
        LabRunnerPrint.fact("platform.isVirtual()", platform.isVirtual());

        int taskCount = 2000;
        CountDownLatch done = new CountDownLatch(taskCount);
        Instant start = Instant.now();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) { // BREAKPOINT: burada dur, executor türünü incele
            for (int i = 0; i < taskCount; i++) {
                executor.submit(() -> {
                    try {
                        Thread.sleep(10); // <- her sanal thread burada carrier'ı BIRAKIR (unmount)
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            done.await(30, TimeUnit.SECONDS);
        }
        long elapsedMillis = Duration.between(start, Instant.now()).toMillis();
        LabRunnerPrint.fact(taskCount + " blocking task, elapsed", elapsedMillis + "ms");

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("2000 sanal thread, her biri 10ms bloklayan bir sleep yapsa BİLE, hepsi birkaç");
        LabRunnerPrint.line("GERÇEK OS thread'i (carrier, genelde CPU çekirdek sayısı kadar) üzerinde zaman");
        LabRunnerPrint.line("paylaşımlı çalıştı - 2000 GERÇEK OS thread'i (platform thread) OLUŞTURMADI.");
        LabRunnerPrint.line("Bu, bilimsel bir benchmark İDDİASI değil - sadece N:M eşlemenin ÇALIŞTIĞININ kanıtı.");

        LabRunnerPrint.section("BREAKPOINT");
        LabRunnerPrint.line("VirtualThreadLabRunner.java:21 - Threads panelinde çalışırken kaç 'ForkJoinPool-1-");
        LabRunnerPrint.line("worker-N' (carrier) thread'i olduğunu say - CPU çekirdek sayına yakın olmalı,");
        LabRunnerPrint.line("2000 DEĞİL.");

        LabRunnerPrint.section("TRY");
        LabRunnerPrint.line("Executors.newFixedThreadPool(200) (platform thread havuzu) ile AYNI 2000 görevi");
        LabRunnerPrint.line("dene - Threads panelinde 200 GERÇEK OS thread'i göreceksin, hepsi aynı anda var.");
    }
}
