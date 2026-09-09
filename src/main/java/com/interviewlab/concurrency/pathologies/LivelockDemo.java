package com.interviewlab.concurrency.pathologies;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Livelock: deadlock'un aksine, her iki thread de {@code RUNNABLE} kalır - hiçbiri bloke
 * olmaz - ama hiçbiri ilerleme kaydetmez, çünkü ikisi de kendi durumunu sıfırlayan bir
 * şekilde birbirine tepki vermeye devam eder. Klasik gerçek dünya benzetmesi, koridorda
 * karşılaşan ve ikisi de diğerinin geçmesi için kenara çekilip sonra ikisi de geri adım atan
 * iki kişidir - bu sonsuza kadar sürer.
 *
 * <p>Burada, iki işçinin (worker) her ikisi de A ve B kilitlerine ihtiyaç duyar. Her biri
 * önce {@code tryLock()} ile A'yı kapar; B'yi de alamazsa, "kibar" işçi A'yı hemen serbest
 * bırakır ve yeniden dener - tam olarak deadlock'a girmemek için. Her iki işçi de senkron
 * (lockstep) şekilde yeniden denerse (burada patolojiyi şansa bağlı zamanlamaya güvenmek
 * yerine deterministik hale getirmek için bir {@link CyclicBarrier} ile zorlanmıştır), hiçbiri
 * asla her iki kilidi aynı anda tutmadan sonsuza kadar serbest bırakıp yeniden deneyebilir.
 *
 * <p><b>Gerçek hayatta nasıl tespit edilir?</b> Deadlock'un aksine, {@code ThreadMXBean}
 * livelock'a giren thread'leri deadlock olarak RAPORLAMAZ (çünkü bloke değillerdir). Belirti,
 * CPU kullanımının yüksek kalması ama hiçbir çıktı (throughput) üretilmemesidir: thread'ler
 * sürekli {@code RUNNABLE}, loglar sonsuza kadar aynı yeniden deneme mesajını gösterir, ama
 * ileriye doğru bir ilerleme yoktur.
 *
 * <p><b>Çözüm:</b> senkron (lockstep) simetriyi boz - örneğin yeniden denemeden önce küçük,
 * rastgele bir gecikme (backoff) ekle ({@link #politeWorkerWithRandomBackoff}), ya da
 * deadlock çözümünde olduğu gibi kesin bir kilit sırasına geri dön.
 */
public final class LivelockDemo {

    private LivelockDemo() {
    }

    /**
     * İki "kibar" işçi arasında tam olarak {@code rounds} kadar senkron (lockstep) yeniden
     * deneme zorlar ve bu turlardan kaçının hiçbir işçinin her iki kilidi de tutmadan
     * bittiğini - yani kaç turun livelock nedeniyle boşa gittiğini - döndürür.
     */
    public static int runLockstepPoliteWorkers(Lock lockA, Lock lockB, int rounds) throws InterruptedException {
        CyclicBarrier roundStart = new CyclicBarrier(2);
        CyclicBarrier bothHoldFirst = new CyclicBarrier(2);
        CyclicBarrier bothAttempted = new CyclicBarrier(2);
        CyclicBarrier roundEnd = new CyclicBarrier(2);
        AtomicInteger wastedRounds = new AtomicInteger();

        Runnable worker1 = politeWorker(lockA, lockB, roundStart, bothHoldFirst, bothAttempted, roundEnd, rounds, wastedRounds);
        Runnable worker2 = politeWorker(lockB, lockA, roundStart, bothHoldFirst, bothAttempted, roundEnd, rounds, wastedRounds);

        Thread t1 = new Thread(worker1, "livelock-worker-1");
        Thread t2 = new Thread(worker2, "livelock-worker-2");
        t1.setDaemon(true);
        t2.setDaemon(true);
        t1.start();
        t2.start();
        t1.join(TimeUnit.SECONDS.toMillis(10));
        t2.join(TimeUnit.SECONDS.toMillis(10));
        return wastedRounds.get();
    }

    private static Runnable politeWorker(Lock myFirst, Lock myAlso, CyclicBarrier roundStart, CyclicBarrier bothHoldFirst,
                                          CyclicBarrier bothAttempted, CyclicBarrier roundEnd, int rounds, AtomicInteger wastedRounds) {
        return () -> {
            for (int round = 0; round < rounds; round++) {
                awaitBarrier(roundStart);
                boolean gotBoth = false;
                myFirst.lock();
                try {
                    // HER İKİ işçinin de ikinciyi denemeden önce kendi ilk kilidini tuttuğunu
                    // garanti eder - bu olmadan, bir işçi diğeri daha başlamadan tüm
                    // lock/tryLock/unlock döngüsünü bitirebilir ve livelock'a girecek gerçek
                    // bir çekişme (contention) oluşmazdı.
                    awaitBarrier(bothHoldFirst);
                    // Kasıtlı olarak asla bloklamaz (tryLock), ikinci kilidi de alamazsa kendi
                    // kilidini hemen bırakır - deadlock'a asla girmeyecek kadar "kibar", ama
                    // livelock'a sebep olan da tam olarak bu kibarlıktır.
                    if (myAlso.tryLock()) {
                        try {
                            gotBoth = true;
                        } finally {
                            myAlso.unlock();
                        }
                    }
                    // Bu ikinci bariyer olmadan, bothHoldFirst sonrası işletim sisteminin ilk
                    // zamanladığı thread, kendi denemesini baştan sona tamamlayıp diğer thread
                    // tryLock() çağırmadan ÖNCE myFirst'ü serbest bırakabilir - bu da ikinci
                    // thread'in başarılı olmasına ve tüm gösterimi geçersiz kılmasına yol açar.
                    // Bu bariyer, HER İKİ deneme de tamamlanana kadar iki thread'i de myFirst'ü
                    // tutar durumda bırakır.
                    awaitBarrier(bothAttempted);
                } finally {
                    myFirst.unlock();
                }
                if (!gotBoth) {
                    wastedRounds.incrementAndGet();
                }
                awaitBarrier(roundEnd);
            }
        };
    }

    /** Çözüm: küçük, rastgele bir gecikme (backoff) simetriyi bozar, böylece eninde sonunda bir işçi her iki kilidi de alır. */
    public static boolean politeWorkerWithRandomBackoff(Lock lockA, Lock lockB, int maxAttempts) throws InterruptedException {
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            lockA.lock();
            try {
                if (lockB.tryLock()) {
                    try {
                        return true;
                    } finally {
                        lockB.unlock();
                    }
                }
            } finally {
                lockA.unlock();
            }
            Thread.sleep(random.nextInt(1, 10)); // senkronu (lockstep) bozar - livelock versiyonundan temel fark budur
        }
        return false;
    }

    private static void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (BrokenBarrierException | java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Lock newLock() {
        return new ReentrantLock();
    }
}
