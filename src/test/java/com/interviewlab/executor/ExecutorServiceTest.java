package com.interviewlab.executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.interviewlab.executor.bad.UnboundedQueueExecutorService;
import com.interviewlab.executor.good.ProductionThreadPoolExecutorFactory;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Yazı ve mülakat cevapları için docs/executor-service.md dosyasına bakın. */
class ExecutorServiceTest {

    @Test
    void shouldQueueUnboundedWorkWithNewFixedThreadPool() throws InterruptedException {
        ExecutorService executor = UnboundedQueueExecutorService.newFixedPoolWithHiddenUnboundedQueue(2);
        ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) executor;
        CountDownLatch release = new CountDownLatch(1);

        int taskCount = 5000;
        for (int i = 0; i < taskCount; i++) {
            executor.submit(() -> awaitUninterruptibly(release));
        }

        assertThat(threadPoolExecutor.getQueue().size())
                .as("sadece 2 worker thread ve sınırsız kuyruk ile, ~%d görev, reddedilmeden veya gönderen "
                        + "engellenmeden kuyrukta bekliyor olmalı", taskCount - 2)
                .isGreaterThan(taskCount - 10);

        release.countDown();
        threadPoolExecutor.shutdown();
        assertThat(threadPoolExecutor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void shouldRejectWithAbortPolicyOnceQueueAndPoolAreFull() throws InterruptedException {
        ThreadPoolExecutor executor = ProductionThreadPoolExecutorFactory.abortPolicyExecutor(1, 1, 1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            executor.submit(() -> awaitUninterruptibly(release)); // 1 worker thread'i işgal eder
            executor.submit(() -> awaitUninterruptibly(release)); // kuyruğu doldurur (kapasite 1)

            assertThatThrownBy(() -> executor.submit(() -> awaitUninterruptibly(release)))
                    .as("havuz dolu + kuyruk dolu -> AbortPolicy 3. görevi reddetmeli")
                    .isInstanceOf(RejectedExecutionException.class);
        } finally {
            release.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void shouldRunOnCallingThreadWithCallerRunsPolicyOnceQueueAndPoolAreFull() throws InterruptedException {
        ThreadPoolExecutor executor = ProductionThreadPoolExecutorFactory.callerRunsPolicyExecutor(1, 1, 1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger executedOnCallingThread = new AtomicInteger();
        String callingThreadName = Thread.currentThread().getName();

        try {
            executor.submit(() -> awaitUninterruptibly(release)); // 1 worker thread'i işgal eder
            executor.submit(() -> awaitUninterruptibly(release)); // kuyruğu doldurur (kapasite 1)

            // Havuz dolu + kuyruk dolu: CallerRunsPolicy bu görevi BU thread üzerinde, senkron olarak çalıştırmalı.
            executor.execute(() -> {
                if (Thread.currentThread().getName().equals(callingThreadName)) {
                    executedOnCallingThread.incrementAndGet();
                }
            });

            assertThat(executedOnCallingThread.get())
                    .as("CallerRunsPolicy, reddedilen görevi gönderen thread'in kendisinde çalıştırmış olmalı")
                    .isEqualTo(1);
        } finally {
            release.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void shouldFinishQueuedWorkOnGracefulShutdownButNotAcceptNewWork() throws InterruptedException {
        ThreadPoolExecutor executor = ProductionThreadPoolExecutorFactory.abortPolicyExecutor(2, 2, 10);
        AtomicInteger completed = new AtomicInteger();
        for (int i = 0; i < 5; i++) {
            executor.submit(completed::incrementAndGet);
        }

        boolean terminated = ProductionThreadPoolExecutorFactory.shutdownGracefully(executor, 5);

        assertThat(terminated).isTrue();
        assertThat(completed.get()).as("kuyruğa alınmış 5 görevin tamamı sonuna kadar çalışmalı").isEqualTo(5);
        assertThatThrownBy(() -> executor.submit(completed::incrementAndGet))
                .as("shutdown sonrasında yeni gönderimler reddedilmeli")
                .isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    void shutdownNowShouldReturnTasksThatNeverStarted() {
        ThreadPoolExecutor executor = ProductionThreadPoolExecutorFactory.abortPolicyExecutor(1, 1, 10);
        CountDownLatch blockFirstTask = new CountDownLatch(1);
        executor.submit(() -> awaitUninterruptibly(blockFirstTask)); // tek worker'ı işgal eder
        for (int i = 0; i < 4; i++) {
            executor.submit(() -> { });
        }

        await().atMost(Duration.ofSeconds(2)).until(() -> executor.getQueue().size() == 4);

        var neverStarted = executor.shutdownNow();
        assertThat(neverStarted)
                .as("shutdownNow(), kuyrukta bekleyen görevlerin tam olarak kendisini geri döndürmeli")
                .hasSize(4);

        blockFirstTask.countDown();
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
