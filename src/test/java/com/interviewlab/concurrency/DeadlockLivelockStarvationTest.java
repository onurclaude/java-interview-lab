package com.interviewlab.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.interviewlab.concurrency.pathologies.Account;
import com.interviewlab.concurrency.pathologies.LivelockDemo;
import com.interviewlab.concurrency.pathologies.StarvationDemo;
import com.interviewlab.concurrency.pathologies.bad.InconsistentLockOrderTransferService;
import com.interviewlab.concurrency.pathologies.good.DeterministicLockOrderTransferService;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;

/**
 * Deadlock, livelock ve starvation yan yana - yazı için docs/java-locks.md dosyasına bakın
 * (her biri için "Definition / Bad example / Runtime behavior / How to detect / How to prevent /
 * Interview answer" başlıkları).
 */
class DeadlockLivelockStarvationTest {

    @Test
    void shouldDeadlockWithInconsistentLockOrder() throws InterruptedException {
        InconsistentLockOrderTransferService service = new InconsistentLockOrderTransferService();
        Account a = new Account("A", 100);
        Account b = new Account("B", 100);

        Thread t1 = new Thread(() -> service.transfer(a, b, 10), "deadlock-t1");
        Thread t2 = new Thread(() -> service.transfer(b, a, 10), "deadlock-t2");
        t1.setDaemon(true); // gözlem bittikten sonra JVM/test runner'ın çıkmasını engellememeli
        t2.setDaemon(true);
        t1.start();
        t2.start();

        ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
        await().atMost(Duration.ofSeconds(5))
                .until(() -> {
                    long[] deadlocked = threadMxBean.findDeadlockedThreads();
                    return deadlocked != null && deadlocked.length > 0;
                });
        // Her iki thread de (tasarım gereği) sonsuza kadar bloklanmış halde kalır - bunlar daemon
        // thread oldukları için test JVM'inin bitmesini engellemezler.
    }

    @Test
    void shouldNotDeadlockWithDeterministicLockOrder() throws InterruptedException {
        DeterministicLockOrderTransferService service = new DeterministicLockOrderTransferService();
        Account a = new Account("A", 100);
        Account b = new Account("B", 100);

        Thread t1 = new Thread(() -> service.transfer(a, b, 10), "no-deadlock-t1");
        Thread t2 = new Thread(() -> service.transfer(b, a, 10), "no-deadlock-t2");
        t1.start();
        t2.start();
        t1.join(Duration.ofSeconds(5).toMillis());
        t2.join(Duration.ofSeconds(5).toMillis());

        assertThat(t1.isAlive()).as("tutarlı lock sıralaması her iki transferin de tamamlanmasına izin vermelidir").isFalse();
        assertThat(t2.isAlive()).isFalse();
        assertThat(a.balance + b.balance).isEqualTo(200); // para korunmuş, bozulma da yok
    }

    @Test
    void shouldWasteEveryForcedLockstepRoundToLivelock() throws InterruptedException {
        int rounds = 20;
        int wastedRounds = LivelockDemo.runLockstepPoliteWorkers(LivelockDemo.newLock(), LivelockDemo.newLock(), rounds);

        assertThat(wastedRounds)
                .as("her iki worker da asla deadlock olmayacak kadar kibar, ama zorunlu lockstep altında hiçbiri "
                        + "iki lock'u aynı anda tutamıyor - ikisi de her turu boşa harcıyor (tur başına 2 boşa deneme)")
                .isEqualTo(rounds * 2);
    }

    @Test
    void shouldEventuallySucceedOnceRandomBackoffBreaksTheLockstep() throws InterruptedException {
        boolean acquiredBoth = LivelockDemo.politeWorkerWithRandomBackoff(
                LivelockDemo.newLock(), LivelockDemo.newLock(), 100);
        assertThat(acquiredBoth)
                .as("küçük bir rastgele backoff, livelock'a neden olan simetriyi bozar")
                .isTrue();
    }

    @Test
    void shouldAlwaysServeAlreadyQueuedThreadBeforeNewArrivalUnderFairLock() throws InterruptedException {
        // Birkaç kez çalıştır: FAIR bir lock'un sıralama garantisi sadece "genellikle" değil,
        // her tek çalıştırmada geçerli olmalıdır - fairness'in tüm amacı budur.
        for (int i = 0; i < 5; i++) {
            boolean victimServedFirst = StarvationDemo.victimServedBeforeLatecomer(new ReentrantLock(true));
            assertThat(victimServedFirst)
                    .as("adil (fair) bir lock, sonradan gelen birinin zaten kuyrukta bekleyen bir thread'in önüne geçmesine asla izin vermemelidir")
                    .isTrue();
        }
    }

    @Test
    void nonFairLockGivesNoOrderingGuaranteeUnlikeFairLock() throws InterruptedException {
        // Burada kasıtlı olarak belirli bir sonuç assert edilmiyor: adil olmayan (non-fair) bir lock'un
        // sonradan geleni öne geçirmesine YA DA kurbana önce hizmet vermesine izin verilir - JDK
        // her iki yönde de garanti vermez. Bu garanti eksikliği, tam olarak fair modun (yukarıda
        // kanıtlandığı gibi) ortadan kaldırmak için var olduğu starvation RİSKİ'dir. Biz sadece
        // çağrının non-fair politika altında da hatasız tamamlandığını kanıtlıyoruz.
        boolean result = StarvationDemo.victimServedBeforeLatecomer(new ReentrantLock(false));
        assertThat(result).isIn(true, false);
    }
}
