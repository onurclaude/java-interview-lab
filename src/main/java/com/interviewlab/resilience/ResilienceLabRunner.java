package com.interviewlab.resilience;

import com.interviewlab.labrunner.LabRunnerPrint;

import com.interviewlab.resilience.FlakyExternalService;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** IntelliJ'de sağ tık -> Run/Debug. Postman/HTTP GEREKMEZ - Spring de GEREKMEZ (saf POJO + gerçek Resilience4j nesneleri). */
public final class ResilienceLabRunner {

    public static void main(String[] args) throws InterruptedException {
        retryDemo();
        circuitBreakerDemo();
        rateLimiterDemo();
        bulkheadDemo();
        timeoutDemo();
        fallbackDemo();
    }

    private static void retryDemo() {
        LabRunnerPrint.banner("RESILIENCE — RETRY");
        FlakyExternalService service = new FlakyExternalService();
        service.failFirstNCalls(2);
        RetryConfig config = RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(20)).build();
        Retry retry = Retry.of("demo-retry", config);
        Supplier<String> decorated = Retry.decorateSupplier(retry, service::call); // <- BREAKPOINT 1: FlakyExternalService.call() 3 KEZ HIT olur
        String result;
        try {
            result = decorated.get();
        } catch (Exception e) {
            result = "GAVE_UP: " + e.getMessage();
        }
        LabRunnerPrint.fact("RETRY result", result);
        LabRunnerPrint.fact("RETRY actualCallCount", service.callCount());
    }

    private static void circuitBreakerDemo() {
        LabRunnerPrint.banner("RESILIENCE — CIRCUIT BREAKER");
        FlakyExternalService service = new FlakyExternalService();
        service.setAlwaysFail(true);
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50).slidingWindowSize(4).minimumNumberOfCalls(4)
                .waitDurationInOpenState(Duration.ofMillis(300)).permittedNumberOfCallsInHalfOpenState(2).build();
        CircuitBreaker cb = CircuitBreaker.of("demo-cb", config);
        Supplier<String> decorated = CircuitBreaker.decorateSupplier(cb, service::call);
        List<String> transitions = new ArrayList<>();
        transitions.add(cb.getState().name());
        for (int i = 0; i < 4; i++) {
            try { decorated.get(); } catch (FlakyExternalService.ExternalServiceException ignored) { }
        }
        transitions.add(cb.getState().name()); // <- BREAKPOINT 2: CLOSED -> OPEN
        boolean shortCircuited = false;
        int before = service.callCount();
        try {
            decorated.get();
        } catch (CallNotPermittedException e) {
            shortCircuited = true; // <- BREAKPOINT 3: gerçek servis HİÇ çağrılmadı
        } catch (FlakyExternalService.ExternalServiceException ignored) { }
        int after = service.callCount();
        LabRunnerPrint.fact("stateTransitions", transitions);
        LabRunnerPrint.fact("shortCircuited (OPEN'da gerçek çağrı engellendi)", shortCircuited);
        LabRunnerPrint.fact("callCount OPEN öncesi/sonrası (aynı olmalı)", before + "/" + after);
    }

    private static void rateLimiterDemo() {
        LabRunnerPrint.banner("RESILIENCE — RATE LIMITER");
        FlakyExternalService service = new FlakyExternalService();
        RateLimiterConfig config = RateLimiterConfig.custom().limitForPeriod(2).limitRefreshPeriod(Duration.ofSeconds(1)).timeoutDuration(Duration.ZERO).build();
        RateLimiter rl = RateLimiter.of("demo-rl", config);
        Supplier<String> decorated = RateLimiter.decorateSupplier(rl, service::call);
        int permitted = 0, rejected = 0;
        for (int i = 0; i < 5; i++) {
            try { decorated.get(); permitted++; } catch (RequestNotPermitted e) { rejected++; } // <- BREAKPOINT 4
        }
        LabRunnerPrint.fact("permitted/rejected (5 istekten)", permitted + "/" + rejected);
    }

    private static void bulkheadDemo() throws InterruptedException {
        LabRunnerPrint.banner("RESILIENCE — BULKHEAD");
        FlakyExternalService service = new FlakyExternalService();
        service.setSimulatedLatencyMillis(300);
        BulkheadConfig config = BulkheadConfig.custom().maxConcurrentCalls(2).maxWaitDuration(Duration.ZERO).build();
        Bulkhead bh = Bulkhead.of("demo-bh", config);
        Supplier<String> decorated = Bulkhead.decorateSupplier(bh, service::call);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(4);
        try {
            for (int i = 0; i < 4; i++) {
                executor.submit(() -> {
                    try { decorated.get(); accepted.incrementAndGet(); }
                    catch (BulkheadFullException e) { rejected.incrementAndGet(); } // <- BREAKPOINT 5
                    finally { done.countDown(); }
                });
            }
            done.await(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
        }
        LabRunnerPrint.fact("accepted/rejected (4 eşzamanlı istekten)", accepted.get() + "/" + rejected.get());
    }

    private static void timeoutDemo() {
        LabRunnerPrint.banner("RESILIENCE — TIMEOUT");
        FlakyExternalService service = new FlakyExternalService();
        service.setSimulatedLatencyMillis(500);
        TimeLimiterConfig config = TimeLimiterConfig.custom().timeoutDuration(Duration.ofMillis(100)).build();
        TimeLimiter tl = TimeLimiter.of(config);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Supplier<Future<String>> futureSupplier = () -> executor.submit(service::call);
        String outcome;
        try {
            tl.executeFutureSupplier(futureSupplier); // <- BREAKPOINT 6: 500ms servis, 100ms limit
            outcome = "UNEXPECTEDLY_COMPLETED";
        } catch (TimeoutException e) {
            outcome = "TIMED_OUT";
        } catch (Exception e) {
            outcome = "OTHER_ERROR: " + e.getClass().getSimpleName();
        } finally {
            executor.shutdownNow();
        }
        LabRunnerPrint.fact("TIMEOUT outcome", outcome);
    }

    private static void fallbackDemo() {
        LabRunnerPrint.banner("RESILIENCE — FALLBACK");
        FlakyExternalService service = new FlakyExternalService();
        service.setAlwaysFail(true);
        RetryConfig config = RetryConfig.custom().maxAttempts(2).waitDuration(Duration.ofMillis(10)).build();
        Retry retry = Retry.of("demo-fallback", config);
        Supplier<String> decorated = Retry.decorateSupplier(retry, service::call);
        String result;
        boolean fallbackUsed;
        try {
            result = decorated.get();
            fallbackUsed = false;
        } catch (FlakyExternalService.ExternalServiceException e) {
            result = "DEFAULT_CACHED_RESPONSE"; // <- BREAKPOINT 7
            fallbackUsed = true;
        }
        LabRunnerPrint.fact("FALLBACK result/fallbackUsed", result + "/" + fallbackUsed);

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("Her Resilience4j pattern'i AYNI problemi (yavaş/başarısız bağımlılık) FARKLI stratejilerle");
        LabRunnerPrint.line("ele alır - Retry telafi eder, CircuitBreaker fail-fast yapar, RateLimiter önceden");
        LabRunnerPrint.line("belirlenmiş bir tavana uyar, Bulkhead eşzamanlılığı sınırlar, Timeout süresiz beklemeyi");
        LabRunnerPrint.line("engeller, Fallback yine de BİR ŞEY döner.");
    }
}
