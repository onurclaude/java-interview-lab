package com.interviewlab.async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.interviewlab.AbstractPostgresIntegrationTest;
import com.interviewlab.async.spring.AsyncConfig;
import com.interviewlab.async.spring.bad.SelfInvocationNotificationService;
import com.interviewlab.async.spring.good.AsyncSender;
import com.interviewlab.async.spring.good.NotificationService;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Yazı ve mülakat cevapları için docs/async.md dosyasına bakın. */
class AsyncSelfInvocationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private SelfInvocationNotificationService selfInvocationNotificationService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AsyncSender asyncSender;

    @BeforeEach
    void clearExceptions() {
        AsyncConfig.clearUncaughtAsyncExceptions();
    }

    @Test
    void shouldNotApplyAsyncDuringSelfInvocation() {
        String callerThread = selfInvocationNotificationService.notifyUser("hi");

        assertThat(selfInvocationNotificationService.lastAsyncThreadName())
                .as("self-invocation, sendAsync()'in caller'ın kendi thread'inde senkron olarak çalıştığı anlamına gelir")
                .isEqualTo(callerThread);
    }

    @Test
    void shouldRunOnDifferentThreadWhenCalledThroughARealProxy() {
        String callerThread = notificationService.notifyUser("hi");

        await().atMost(Duration.ofSeconds(2)).until(() -> asyncSender.lastAsyncThreadName() != null);
        assertThat(asyncSender.lastAsyncThreadName())
                .as("gerçek bir proxy çağrısı sendAsync()'i caller'ın thread'inde değil, yapılandırılmış async executor'un thread'inde çalıştırmalıdır")
                .isNotEqualTo(callerThread);
    }

    @Test
    void shouldRouteVoidAsyncMethodFailuresToTheUncaughtExceptionHandler() {
        asyncSender.sendAsyncAndFail();

        await().atMost(Duration.ofSeconds(2)).until(() -> !AsyncConfig.uncaughtAsyncExceptions().isEmpty());
        assertThat(AsyncConfig.uncaughtAsyncExceptions())
                .as("void bir @Async metodunun exception'ının gidebileceği tek yer uncaught-exception handler'dır")
                .anyMatch(t -> "boom-void".equals(t.getMessage()));
    }

    @Test
    void shouldCompleteFutureExceptionallyForCompletableFutureReturningAsyncMethod() {
        CompletableFuture<String> future = asyncSender.sendAsyncFutureAndFail();

        assertThat(future.isCompletedExceptionally() || awaitCompletedExceptionally(future)).isTrue();
        assertThat(AsyncConfig.uncaughtAsyncExceptions())
                .as("metod bir CompletableFuture döndürdüğünde, Spring hatayı uncaught-exception handler'a "
                        + "yönlendirmek yerine bu future'ın içine yayar")
                .isEmpty();
    }

    private static boolean awaitCompletedExceptionally(CompletableFuture<String> future) {
        await().atMost(Duration.ofSeconds(2)).until(future::isDone);
        return future.isCompletedExceptionally();
    }
}
