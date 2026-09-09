package com.interviewlab.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewlab.concurrency.threadlocal.bad.LeakyCorrelationIdService;
import com.interviewlab.concurrency.threadlocal.good.CleanCorrelationIdService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Yazı ve mülakat cevabı için docs/java-locks.md (ThreadLocal bölümü) dosyasına bakın. */
class ThreadLocalTest {

    @Test
    void shouldLeakThreadLocalStateWhenRemoveIsNotCalled() throws Exception {
        LeakyCorrelationIdService service = new LeakyCorrelationIdService();
        // Single-thread pool: task 1 ve task 2'nin tam olarak aynı OS thread'inde çalışmasını garanti eder.
        ExecutorService singleThreadPool = Executors.newSingleThreadExecutor();
        try {
            singleThreadPool.submit(() -> service.setCorrelationId("request-A")).get();

            Future<String> secondTaskResult = singleThreadPool.submit(service::getCorrelationId);
            assertThat(secondTaskResult.get())
                    .as("pool'daki thread hiçbir zaman temizlenmediği için, tamamen alakasız ikinci bir görev "
                            + "birinci görevin correlation id'sini devralır")
                    .isEqualTo("request-A");
        } finally {
            singleThreadPool.shutdown();
        }
    }

    @Test
    void shouldNotLeakThreadLocalStateWhenRemoveIsCalledInFinally() throws Exception {
        CleanCorrelationIdService service = new CleanCorrelationIdService();
        ExecutorService singleThreadPool = Executors.newSingleThreadExecutor();
        try {
            singleThreadPool.submit(() -> service.runWithCorrelationId("request-A", () -> null)).get();

            Future<String> secondTaskResult = singleThreadPool.submit(service::getCorrelationId);
            assertThat(secondTaskResult.get())
                    .as("finally bloğundaki remove(), bu thread'deki bir sonraki görev için temiz bir sayfa bırakmalıdır")
                    .isNull();
        } finally {
            singleThreadPool.shutdown();
        }
    }
}
