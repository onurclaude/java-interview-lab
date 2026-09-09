package com.interviewlab.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.interviewlab.concurrency.thread.ThreadLifecycleDemo;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * O durumda park edilmiş gerçek bir thread kullanarak her {@link Thread.State}'i gezer - tam
 * yaşam döngüsüyle ilgili yazı ve mülakat cevabı için docs/java-locks.md dosyasına bakın.
 */
class ThreadLifecycleTest {

    @Test
    void shouldStartInNewState() {
        Thread thread = ThreadLifecycleDemo.newUnstartedThread();
        assertThat(thread.getState()).isEqualTo(Thread.State.NEW);
    }

    @Test
    void shouldBeRunnableWhileBusyLooping() throws InterruptedException {
        AtomicBoolean stop = new AtomicBoolean(false);
        Thread thread = ThreadLifecycleDemo.runnableThread(stop);
        thread.start();

        await().atMost(Duration.ofSeconds(2)).until(() -> thread.getState() == Thread.State.RUNNABLE);

        stop.set(true);
        thread.join();
        assertThat(thread.getState()).isEqualTo(Thread.State.TERMINATED);
    }

    @Test
    void shouldBeTimedWaitingWhileSleeping() throws InterruptedException {
        Thread thread = ThreadLifecycleDemo.timedWaitingThread(2000);
        thread.start();

        await().atMost(Duration.ofSeconds(2)).until(() -> thread.getState() == Thread.State.TIMED_WAITING);

        thread.join();
    }

    @Test
    void shouldBeWaitingUntilLatchIsReleased() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Thread thread = ThreadLifecycleDemo.waitingThread(latch);
        thread.start();

        await().atMost(Duration.ofSeconds(2)).until(() -> thread.getState() == Thread.State.WAITING);

        latch.countDown();
        thread.join();
        assertThat(thread.getState()).isEqualTo(Thread.State.TERMINATED);
    }

    @Test
    void shouldBeBlockedWaitingForAMonitorHeldByAnotherThread() throws InterruptedException {
        Object monitor = new Object();
        CountDownLatch holderReady = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            synchronized (monitor) {
                holderReady.countDown();
                awaitUninterruptibly(releaseHolder);
            }
        }, "lifecycle-holder");
        holder.start();
        assertThat(holderReady.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

        Thread blocked = ThreadLifecycleDemo.blockedThread(monitor);
        blocked.start();

        await().atMost(Duration.ofSeconds(2)).until(() -> blocked.getState() == Thread.State.BLOCKED);

        releaseHolder.countDown();
        blocked.join();
        holder.join();
    }

    @Test
    void shouldTerminateAfterRunCompletes() throws InterruptedException {
        Thread thread = ThreadLifecycleDemo.terminatingThread();
        thread.start();
        thread.join();
        assertThat(thread.getState()).isEqualTo(Thread.State.TERMINATED);
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
