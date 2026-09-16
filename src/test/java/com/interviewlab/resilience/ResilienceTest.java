package com.interviewlab.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadConfig;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * {@code /api/labs/resilience/*} ile aynı davranışın, ayrı bir otomatik regresyon testi
 * olarak kanıtı - bkz. docs/resilience.md. Bu proje bir davranışı yalnızca interactive
 * HTTP ile göstermeyi yeterli saymaz; her ikisi de (HTTP + test) aynı gerçeği doğrulamalı.
 */
class ResilienceTest {

    @Test
    void shouldSucceedAfterTransientFailuresWithRetry() {
        FlakyExternalService service = new FlakyExternalService();
        service.failFirstNCalls(2);

        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .retryExceptions(FlakyExternalService.ExternalServiceException.class)
                .build();
        Retry retry = Retry.of("test-retry", config);
        Supplier<String> decorated = Retry.decorateSupplier(retry, service::call);

        String result = decorated.get();

        assertThat(result).isEqualTo("success on call #3");
        assertThat(service.callCount()).isEqualTo(3);
    }

    @Test
    void shouldExhaustRetriesAndPropagateExceptionWhenAlwaysFailing() {
        FlakyExternalService service = new FlakyExternalService();
        service.setAlwaysFail(true);

        RetryConfig config = RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(5)).build();
        Retry retry = Retry.of("test-retry-exhausted", config);
        Supplier<String> decorated = Retry.decorateSupplier(retry, service::call);

        assertThatThrownBy(decorated::get).isInstanceOf(FlakyExternalService.ExternalServiceException.class);
        assertThat(service.callCount())
                .as("Retry sonsuza kadar denemez - maxAttempts (3) kadar dener, sonra pes eder")
                .isEqualTo(3);
    }

    @Test
    void shouldOpenCircuitAfterFailureThresholdAndShortCircuitWithoutCallingService() {
        FlakyExternalService service = new FlakyExternalService();
        service.setAlwaysFail(true);

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .waitDurationInOpenState(Duration.ofSeconds(10)) // testte açık kalmalı, HALF_OPEN'a geçmemeli
                .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("test-cb", config);
        Supplier<String> decorated = CircuitBreaker.decorateSupplier(circuitBreaker, service::call);

        for (int i = 0; i < 4; i++) {
            try {
                decorated.get();
            } catch (FlakyExternalService.ExternalServiceException ignored) {
                // beklenen
            }
        }
        assertThat(circuitBreaker.getState())
                .as("4 ardışık başarısızlıktan sonra devre CLOSED'dan OPEN'a geçmeli")
                .isEqualTo(CircuitBreaker.State.OPEN);

        int callCountBeforeShortCircuit = service.callCount();
        assertThatThrownBy(decorated::get)
                .as("OPEN devre, gerçek servisi hiç çağırmadan reddetmeli")
                .isInstanceOf(CallNotPermittedException.class);
        assertThat(service.callCount())
                .as("short-circuit olan çağrı, altta yatan servise HİÇ ULAŞMAMALI")
                .isEqualTo(callCountBeforeShortCircuit);
    }

    @Test
    void shouldRejectRequestsExceedingConfiguredRateEvenWhenServiceIsHealthy() {
        FlakyExternalService service = new FlakyExternalService(); // hiç başarısız olmuyor - servis SAĞLIKLI

        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(2)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build();
        RateLimiter rateLimiter = RateLimiter.of("test-rl", config);
        Supplier<String> decorated = RateLimiter.decorateSupplier(rateLimiter, service::call);

        int permitted = 0;
        int rejected = 0;
        for (int i = 0; i < 5; i++) {
            try {
                decorated.get();
                permitted++;
            } catch (RequestNotPermitted e) {
                rejected++;
            }
        }

        assertThat(permitted).as("saniyede 2 izin verildi").isEqualTo(2);
        assertThat(rejected)
                .as("servis TAMAMEN SAĞLIKLI olsa bile, hız tavanını aşan istekler reddedilmeli")
                .isEqualTo(3);
    }

    @Test
    void shouldLimitConcurrentCallsWithBulkhead() throws InterruptedException {
        FlakyExternalService service = new FlakyExternalService();
        service.setSimulatedLatencyMillis(200);

        BulkheadConfig config = BulkheadConfig.custom().maxConcurrentCalls(2).maxWaitDuration(Duration.ZERO).build();
        Bulkhead bulkhead = Bulkhead.of("test-bh", config);
        Supplier<String> decorated = Bulkhead.decorateSupplier(bulkhead, service::call);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(4);
        try {
            for (int i = 0; i < 4; i++) {
                executor.submit(() -> {
                    try {
                        decorated.get();
                        accepted.incrementAndGet();
                    } catch (BulkheadFullException e) {
                        rejected.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            done.await(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
        }

        assertThat(accepted.get()).as("maxConcurrentCalls=2 - sadece 2 çağrı kabul edilmeli").isEqualTo(2);
        assertThat(rejected.get()).as("kalan 2 çağrı, kuyruğa alınmadan anında reddedilmeli").isEqualTo(2);
    }

    @Test
    void shouldRunSemaphoreBulkheadSynchronouslyOnCallingThreadButThreadPoolBulkheadAsynchronously() throws Exception {
        // semaphore Bulkhead: sadece EŞZAMANLI ÇAĞRI SAYISINI sınırlar - çağıranı BLOKE eder,
        // iş HÂLÂ çağıranın kendi thread'inde çalışır (senkron).
        FlakyExternalService syncService = new FlakyExternalService();
        syncService.setSimulatedLatencyMillis(100);
        Bulkhead semaphoreBulkhead = Bulkhead.of("sem-bh", BulkheadConfig.custom().maxConcurrentCalls(4).build());
        Supplier<String> semaphoreDecorated = Bulkhead.decorateSupplier(semaphoreBulkhead, syncService::call);

        Instant start = Instant.now();
        semaphoreDecorated.get(); // burada 100ms BLOKE olmalı - iş bu thread'de çalışıyor
        long elapsedMillis = Duration.between(start, Instant.now()).toMillis();

        assertThat(elapsedMillis)
                .as("semaphore Bulkhead senkrondur - get() çağrısı, işin GERÇEKTEN bitmesini bekler")
                .isGreaterThanOrEqualTo(90);

        // ThreadPoolBulkhead: işi AYRI, sınırlı bir thread havuzuna gönderir - decorateSupplier
        // ÇAĞIRANI BLOKE ETMEZ, hemen bir CompletionStage döner; iş BAŞKA bir thread'de çalışır.
        FlakyExternalService asyncService = new FlakyExternalService();
        asyncService.setSimulatedLatencyMillis(100);
        ThreadPoolBulkhead threadPoolBulkhead = ThreadPoolBulkhead.of("tp-bh",
                ThreadPoolBulkheadConfig.custom().coreThreadPoolSize(2).maxThreadPoolSize(2).queueCapacity(1).build());
        Supplier<String> asyncSupplier = asyncService::call;
        Supplier<CompletionStage<String>> asyncDecorated = ThreadPoolBulkhead.decorateSupplier(threadPoolBulkhead, asyncSupplier);

        Instant asyncStart = Instant.now();
        CompletionStage<String> future = asyncDecorated.get();
        long callReturnedAfterMillis = Duration.between(asyncStart, Instant.now()).toMillis();

        assertThat(callReturnedAfterMillis)
                .as("ThreadPoolBulkhead ASENKRONDUR - decorateSupplier().get() çağrısı, iş bitmeden HEMEN döner")
                .isLessThan(50);

        String result = future.toCompletableFuture().get(2, TimeUnit.SECONDS);
        assertThat(result).isEqualTo("success on call #1");
    }

    @Test
    void shouldTimeOutSlowCallWithoutWaitingForItToFinish() {
        FlakyExternalService service = new FlakyExternalService();
        service.setSimulatedLatencyMillis(500);

        TimeLimiterConfig config = TimeLimiterConfig.custom().timeoutDuration(Duration.ofMillis(100)).build();
        TimeLimiter timeLimiter = TimeLimiter.of(config);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Supplier<Future<String>> futureSupplier = () -> executor.submit(service::call);

        try {
            assertThatThrownBy(() -> timeLimiter.executeFutureSupplier(futureSupplier))
                    .as("500ms süren bir çağrı, 100ms timeout ile kesilmeli")
                    .isInstanceOf(TimeoutException.class);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldReturnFallbackValueInsteadOfPropagatingExceptionWhenRetriesExhausted() {
        FlakyExternalService service = new FlakyExternalService();
        service.setAlwaysFail(true);

        RetryConfig config = RetryConfig.custom().maxAttempts(2).waitDuration(Duration.ofMillis(5)).build();
        Retry retry = Retry.of("test-fallback", config);
        Supplier<String> decorated = Retry.decorateSupplier(retry, service::call);

        String result;
        try {
            result = decorated.get();
        } catch (FlakyExternalService.ExternalServiceException e) {
            result = "DEFAULT_CACHED_RESPONSE"; // fallback
        }

        assertThat(result)
                .as("retry tükendiğinde, çağıran exception yerine bir fallback değeri almalı")
                .isEqualTo("DEFAULT_CACHED_RESPONSE");
    }
}
