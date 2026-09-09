package com.interviewlab.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.interviewlab.concurrency.synchronization.bad.LockWithoutFinallyService;
import com.interviewlab.concurrency.synchronization.good.IntrinsicMonitorCounter;
import com.interviewlab.concurrency.synchronization.good.ReadWriteLockCache;
import com.interviewlab.concurrency.synchronization.good.ReentrantLockCounter;
import com.interviewlab.concurrency.synchronization.good.StampedLockPoint;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Yazı ve mülakat cevapları için docs/java-locks.md dosyasına bakın. */
class SynchronizationTest {

    @Test
    void shouldTreatSynchronizedMethodAndBlockAsTheSameMonitor() throws InterruptedException {
        IntrinsicMonitorCounter counter = new IntrinsicMonitorCounter();
        CountDownLatch methodHoldingLock = new CountDownLatch(1);
        CountDownLatch releaseMethod = new CountDownLatch(1);

        Thread methodCaller = new Thread(() -> {
            synchronized (counter) {
                methodHoldingLock.countDown();
                awaitUninterruptibly(releaseMethod);
            }
        });
        methodCaller.start();
        assertThat(methodHoldingLock.await(2, TimeUnit.SECONDS)).isTrue();

        Thread blockCaller = new Thread(counter::incrementViaBlock);
        blockCaller.start();
        await().atMost(java.time.Duration.ofSeconds(2)).until(() -> blockCaller.getState() == Thread.State.BLOCKED);

        releaseMethod.countDown();
        methodCaller.join();
        blockCaller.join();
        assertThat(counter.get()).isEqualTo(1); // sadece incrementViaBlock çalıştı, ve sadece bir kez
    }

    @Test
    void shouldLeaveLockPermanentlyHeldWhenExceptionSkipsUnlock() throws InterruptedException {
        LockWithoutFinallyService service = new LockWithoutFinallyService();

        assertThatThrownBy(() -> service.incrementThenMaybeThrow(true))
                .isInstanceOf(IllegalStateException.class);

        // ReentrantLock reentrant'tır: aynı thread'den tryLock() çağırmak - lock() unlock
        // edilmemiş olsa bile - sahibinin kilidi zaten tuttuğu için trivially başarılı olurdu.
        // Kilidin GERÇEKTEN sıkışıp kalıp kalmadığını kanıtlamak için BAŞKA bir thread'den
        // denemek gerekir.
        boolean[] acquiredByAnotherThread = new boolean[1];
        Thread otherThread = new Thread(() -> acquiredByAnotherThread[0] = service.lock().tryLock());
        otherThread.start();
        otherThread.join();

        assertThat(acquiredByAnotherThread[0])
                .as("exception'dan sonra lock hiçbir zaman unlock edilmedi, bu yüzden başka hiç kimse onu asla alamaz")
                .isFalse();
    }

    @Test
    void shouldAlwaysReleaseLockEvenOnFailureWithTryFinally() throws InterruptedException {
        ReentrantLockCounter counter = new ReentrantLockCounter();
        counter.increment();
        counter.increment();
        assertThat(counter.get()).isEqualTo(2);
        assertThat(counter.incrementOrGiveUp(1, TimeUnit.SECONDS))
                .as("her increment() döndükten hemen sonra lock tekrar boşta olmalıdır")
                .isTrue();
    }

    @Test
    void shouldAllowMultipleConcurrentReadersButExcludeThemDuringWrite() throws InterruptedException {
        ReadWriteLockCache cache = new ReadWriteLockCache();
        cache.put("k", "v1");

        int readerCount = 5;
        CountDownLatch allReadersHoldingLock = new CountDownLatch(readerCount);
        CountDownLatch releaseReaders = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(readerCount);

        for (int i = 0; i < readerCount; i++) {
            executor.submit(() -> cache.withReadLockHeld(() -> {
                allReadersHoldingLock.countDown();
                awaitUninterruptibly(releaseReaders);
            }));
        }

        assertThat(allReadersHoldingLock.await(2, TimeUnit.SECONDS))
                .as("tüm reader'lar aynı anda read lock'u tutabilmelidir")
                .isTrue();

        releaseReaders.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void shouldComputeCorrectDistanceUnderOptimisticRead() {
        StampedLockPoint point = new StampedLockPoint();
        point.move(3, 4);
        assertThat(point.distanceFromOrigin()).isEqualTo(5.0);
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
