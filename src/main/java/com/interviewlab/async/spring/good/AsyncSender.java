package com.interviewlab.async.spring.good;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * {@link NotificationService}'in {@link #sendAsync(String)}'i gerçek Spring proxy'si
 * üzerinden çağırabilmesi için özellikle kendi bean'inde yaşar - bunun önlediği hata
 * senaryosu için bkz. {@link com.interviewlab.async.spring.bad.SelfInvocationNotificationService}.
 */
@Service
public class AsyncSender {

    private final AtomicReference<String> lastAsyncThreadName = new AtomicReference<>();

    @Async("labAsyncExecutor")
    public void sendAsync(String message) {
        lastAsyncThreadName.set(Thread.currentThread().getName());
    }

    /** void dönen @Async: burada bir hatayı AsyncUncaughtExceptionHandler dışında kimse gözlemleyemez. */
    @Async("labAsyncExecutor")
    public void sendAsyncAndFail() {
        throw new IllegalStateException("boom-void");
    }

    /** CompletableFuture dönen @Async: Spring bunun yerine döndürülen future'ı istisnalı olarak tamamlar. */
    @Async("labAsyncExecutor")
    public CompletableFuture<String> sendAsyncFutureAndFail() {
        throw new IllegalStateException("boom-future");
    }

    public String lastAsyncThreadName() {
        return lastAsyncThreadName.get();
    }
}
