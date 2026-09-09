package com.interviewlab.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.interviewlab.concurrency.thread.bad.UncontrolledThreadCreationService;
import com.interviewlab.concurrency.thread.good.BoundedTaskSubmissionService;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

/** Yazı için docs/java-locks.md / thread basics bölümüne bakın. */
class ThreadCreationBoundsTest {

    @Test
    void shouldLetConcurrentThreadCountGrowUnboundedWithRawThreads() throws InterruptedException {
        UncontrolledThreadCreationService service = new UncontrolledThreadCreationService();
        int burstSize = 200;
        CountDownLatch allStarted = new CountDownLatch(burstSize);
        CountDownLatch release = new CountDownLatch(1);

        for (int i = 0; i < burstSize; i++) {
            service.handle(() -> {
                allStarted.countDown();
                awaitUninterruptibly(release);
            });
        }

        assertThat(allStarted.await(10, java.util.concurrent.TimeUnit.SECONDS))
                .as("200 raw thread'in tamamı eşzamanlı olarak başlamış olmalıdır - hiçbir şey onları kısıtlamaz")
                .isTrue();
        assertThat(service.peakConcurrent())
                .as("zirve eşzamanlılık, tüm burst boyutuna eşittir: bir üst sınır yoktur")
                .isEqualTo(burstSize);

        release.countDown();
    }

    @Test
    void shouldCapConcurrentThreadCountAtPoolSizeWithBoundedExecutor() {
        BoundedTaskSubmissionService service = new BoundedTaskSubmissionService();
        CountDownLatch release = new CountDownLatch(1);

        for (int i = 0; i < 200; i++) {
            service.handle(() -> awaitUninterruptibly(release));
        }

        await().atMost(Duration.ofSeconds(5))
                .until(() -> service.currentConcurrent() == service.poolSize());

        // (Hatalı olabilecek) herhangi bir ekstra thread'e, ortaya ÇIKMADIĞINI kanıtlaması için bir an ver.
        await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(5)).until(() -> true);

        assertThat(service.peakConcurrent())
                .as("%d boyutundaki sınırlı (bounded) pool, burst boyutu ne olursa olsun asla %d taneden fazla görevi eşzamanlı çalıştırmamalıdır",
                        service.poolSize(), service.poolSize())
                .isEqualTo(service.poolSize());

        release.countDown();
        service.shutdown();
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
