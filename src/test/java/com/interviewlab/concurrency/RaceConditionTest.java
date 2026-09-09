package com.interviewlab.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewlab.concurrency.race.bad.UnsafeBankAccount;
import com.interviewlab.concurrency.race.good.SynchronizedBankAccount;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Yazı ve mülakat cevabı için docs/java-locks.md dosyasına bakın. */
class RaceConditionTest {

    private static final int WITHDRAWALS = 500;
    private static final int AMOUNT_EACH = 1;
    private static final int STARTING_BALANCE = WITHDRAWALS * AMOUNT_EACH;

    @Test
    void shouldLoseUpdatesWithUnsynchronizedWithdraw() throws InterruptedException {
        UnsafeBankAccount account = new UnsafeBankAccount(STARTING_BALANCE);

        runConcurrently(WITHDRAWALS, () -> account.withdraw(AMOUNT_EACH));

        assertThat(account.getBalance())
                .as("başarı bildiren her withdrawal gerçekten atomik olsaydı, "
                        + "hesap tamamen boşalıp sıfıra inerdi")
                .isNotZero();
    }

    @Test
    void shouldPreventLostUpdatesWithSynchronizedWithdraw() throws InterruptedException {
        SynchronizedBankAccount account = new SynchronizedBankAccount(STARTING_BALANCE);

        runConcurrently(WITHDRAWALS, () -> account.withdraw(AMOUNT_EACH));

        assertThat(account.getBalance())
                .as("synchronized, her withdrawal'ı atomik yapar, bu yüzden hesap tam olarak sıfıra iner")
                .isZero();
    }

    private static void runConcurrently(int taskCount, Runnable task) throws InterruptedException {
        // Havuz taskCount kadar thread içermeli: her görev önce ready.countDown() yapıp start
        // latch'ini bekliyor - havuz taskCount'tan küçük olsaydı (ör. sabit 16), ilk 16 görev
        // thread'leri işgal edip start'ı beklemeye başlar, kalan görevler hiçbir zaman bir
        // thread bulamaz, ready hiçbir zaman 0'a inmez ve ana thread ready.await()'te
        // (timeout'suz) sonsuza kadar kilitlenirdi - tam olarak yaşadığımız hang buydu.
        ExecutorService executor = Executors.newFixedThreadPool(taskCount);
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(taskCount);

        for (int i = 0; i < taskCount; i++) {
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

        ready.await(); // tüm görevler kuyruğa alındı ve yarışmak üzere
        start.countDown(); // rekabeti maksimize etmek için hepsini aynı anda serbest bırak
        done.await(10, TimeUnit.SECONDS);
        executor.shutdown();
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
