package com.interviewlab.labrunner;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/** IntelliJ'de sağ tık -> Run/Debug. */
public final class BlockingQueueLabRunner {

    public static void main(String[] args) throws InterruptedException {
        LabRunnerPrint.banner("BLOCKING QUEUE — producer/consumer, put() gerçekten BLOKE EDER");

        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(2);
        queue.put(1);
        queue.put(2); // kapasite (2) doldu

        Thread producer = new Thread(() -> {
            try {
                Instant start = Instant.now();
                queue.put(3); // BREAKPOINT 1: burada dur - Threads panelinde bu thread'in WAITING olduğunu gör
                long blockedMillis = Duration.between(start, Instant.now()).toMillis();
                LabRunnerPrint.fact("producer blocked for", blockedMillis + "ms");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "lab-producer");
        producer.start();

        Thread.sleep(300);
        LabRunnerPrint.fact("producer state while queue full", producer.getState()); // BREAKPOINT 2

        queue.take(); // yer aç - producer'ın BLOKE OLMUŞ put()'u şimdi tamamlanabilir
        producer.join();

        LabRunnerPrint.fact("final queue size", queue.size());

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("BlockingQueue.put(), ham bir Queue.add()'in AKSİNE, kapasite doluysa GERÇEKTEN");
        LabRunnerPrint.line("BLOKE EDER (exception fırlatmaz, hemen dönmez) - yer açılana kadar bekler. Bu,");
        LabRunnerPrint.line("producer'ı DOĞAL olarak yavaşlatan bir backpressure mekanizmasıdır.");

        LabRunnerPrint.section("BREAKPOINT");
        LabRunnerPrint.line("BlockingQueueLabRunner.java:16 (queue.put(3)) - producer thread'i Threads panelinde");
        LabRunnerPrint.line("bul, durumunun WAITING (ya da TIMED_WAITING) olduğunu gör.");

        LabRunnerPrint.section("TRY");
        LabRunnerPrint.line("queue.put(3) yerine queue.offer(3) dene (bloklamayan varyant) - hemen false döner,");
        LabRunnerPrint.line("producer thread'i HİÇ bloklanmaz. new LinkedBlockingQueue<>() (kapasitesiz)");
        LabRunnerPrint.line("kullanmayı dene - put() ASLA bloklamaz, tıpkı ExecutorService lab'ındaki gizli");
        LabRunnerPrint.line("sınırsız kuyruk tuzağı gibi.");
    }
}
