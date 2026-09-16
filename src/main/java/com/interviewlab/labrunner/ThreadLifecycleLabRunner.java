package com.interviewlab.labrunner;

import java.util.concurrent.CountDownLatch;

/** IntelliJ'de sağ tık -> Run/Debug. */
public final class ThreadLifecycleLabRunner {

    public static void main(String[] args) throws InterruptedException {
        LabRunnerPrint.banner("THREAD LIFECYCLE — Thread.State geçişleri");

        Object monitor = new Object();
        CountDownLatch enteredWait = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            synchronized (monitor) {
                try {
                    enteredWait.countDown();
                    monitor.wait(); // BREAKPOINT 1: burada dur, worker.getState() WAITING olmalı
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "lab-lifecycle-worker");

        LabRunnerPrint.fact("state before start()", worker.getState());
        worker.start();
        enteredWait.await();
        Thread.sleep(50);
        LabRunnerPrint.fact("state after wait() entered", worker.getState()); // BREAKPOINT 2: değişkeni incele

        synchronized (monitor) {
            monitor.notifyAll();
        }
        Thread.sleep(50);
        LabRunnerPrint.fact("state after notifyAll()", worker.getState());
        worker.join();
        LabRunnerPrint.fact("state after join()", worker.getState());

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("NEW -> RUNNABLE (start() sonrası) -> WAITING (Object.wait() içinde, timeout YOK) ->");
        LabRunnerPrint.line("RUNNABLE (notifyAll() sonrası, monitor'u tekrar kazanmaya çalışırken BLOCKED da");
        LabRunnerPrint.line("olabilirdi eğer monitor meşgulse) -> TERMINATED (run() bitince).");

        LabRunnerPrint.section("BREAKPOINT");
        LabRunnerPrint.line("ThreadLifecycleLabRunner.java:14 (monitor.wait()) - Threads panelinde");
        LabRunnerPrint.line("'lab-lifecycle-worker'ı bul, durumunun WAITING olduğunu gör.");

        LabRunnerPrint.section("TRY");
        LabRunnerPrint.line("monitor.wait() yerine monitor.wait(5000) dene (TIMED_WAITING durumunu gör).");
        LabRunnerPrint.line("Thread.sleep(100000) ekleyip ana thread'i BLOCKED değil TIMED_WAITING olarak gözlemle.");
    }
}
