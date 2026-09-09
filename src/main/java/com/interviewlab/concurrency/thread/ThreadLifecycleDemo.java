package com.interviewlab.concurrency.thread;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Her {@link Thread.State}'te park edilmiş gerçek {@link Thread}'ler üretir, böylece bir test
 * yaşam döngüsünü bir diyagramdan okumak yerine gözlemleyebilir:
 *
 * <pre>
 * NEW -&gt; RUNNABLE -&gt; {BLOCKED | WAITING | TIMED_WAITING} -&gt; RUNNABLE -&gt; TERMINATED
 * </pre>
 *
 * <p>Buradaki her factory metodu, metodun adıyla anılan durumda kasıtlı olarak park edilmiş
 * bir {@link Thread} ile birlikte, çağıranın onu serbest bırakmak için ihtiyaç duyduğu her
 * türlü latch/lock'u geri verir - her durumun nasıl yakalandığını ve serbest bırakıldığını
 * görmek için {@code ThreadLifecycleTest}'e bakın.
 */
public final class ThreadLifecycleDemo {

    private ThreadLifecycleDemo() {
    }

    /** Oluşturulmuş ama başlatılmamış bir thread: her zaman NEW. */
    public static Thread newUnstartedThread() {
        return new Thread(() -> { }, "lifecycle-new");
    }

    /** Verilen süre kadar uyur: uyurken TIMED_WAITING olarak gözlemlenebilir. */
    public static Thread timedWaitingThread(long sleepMillis) {
        return new Thread(() -> {
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "lifecycle-timed-waiting");
    }

    /** Zaman aşımı olmadan {@code latch.await()} üzerinde bloklanır: sayaç sıfırlanana kadar WAITING olarak gözlemlenebilir. */
    public static Thread waitingThread(CountDownLatch latch) {
        return new Thread(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "lifecycle-waiting");
    }

    /**
     * Çağıran tarafından zaten tutulan bir {@code synchronized} bloğa girmeye çalışır:
     * monitor'ü beklerken BLOCKED olarak gözlemlenebilir.
     */
    public static Thread blockedThread(Object monitor) {
        return new Thread(() -> {
            synchronized (monitor) {
                // Buraya ulaşmak, kilidin alındığı anlamına gelir - yapılacak başka bir şey yok.
            }
        }, "lifecycle-blocked");
    }

    /** Görevi no-op olan bir thread: TERMINATED'i gözlemlemek için çalıştırın ve join edin. */
    public static Thread terminatingThread() {
        return new Thread(() -> { }, "lifecycle-terminated");
    }

    /** {@code stop} ayarlanana kadar meşgul döngüde (busy-loop) çalışır: dönerken RUNNABLE olarak gözlemlenebilir. */
    public static Thread runnableThread(AtomicBoolean stop) {
        return new Thread(() -> {
            while (!stop.get()) {
                // OS scheduler'ın onu RUNNABLE tutması için kasıtlı olarak CPU-bound
            }
        }, "lifecycle-runnable");
    }
}
