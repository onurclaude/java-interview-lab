package com.interviewlab.web.lab.resilience;

import com.interviewlab.resilience.FlakyExternalService;
import com.interviewlab.web.lab.LabLog;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/INTERACTIVE_LABS.md — LAB 16, docs/resilience.md. Retry/CircuitBreaker/RateLimiter/
 * Bulkhead/Timeout/Fallback'in HER BİRİNİN AYNI problemi ("bir bağımlılık başarısız oluyor
 * ya da yavaş") FARKLI şekillerde ele aldığını, {@link FlakyExternalService} üzerinden
 * gerçek Resilience4j nesneleriyle (programatik API, annotation değil - kontrolü tam
 * göstermek için) kanıtlar.
 */
@RestController
@RequestMapping("/api/labs/resilience")
public class ResilienceLabController {

    private final FlakyExternalService flakyExternalService;

    public ResilienceLabController(FlakyExternalService flakyExternalService) {
        this.flakyExternalService = flakyExternalService;
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        flakyExternalService.reset();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "RESILIENCE");
        body.put("action", "RESET");
        body.put("nextStep", "POST /api/labs/resilience/retry");
        return body;
    }

    /**
     * Kontrollü, önceden yapılandırılabilir bir sahte dış bağımlılık: {@code failNext}, ard arda
     * KAÇ çağrının başarısız olacağını; {@code delayMs}, her çağrının simüle edilen gecikmesini;
     * {@code alwaysFail=true} ise SINIRSIZ başarısızlığı ayarlar. {@link #circuitBreaker()}
     * artık BURADA yapılandırılan durumu kullanır - kendi içinde ayrıca reset/setAlwaysFail
     * çağırmaz (bkz. docs/DEBUGGER_LABS.md "RESILIENCE — CIRCUIT BREAKER").
     */
    @PostMapping("/external/config")
    public Map<String, Object> configureExternal(@RequestBody ExternalConfigRequest request) {
        flakyExternalService.reset();
        if (request.failNext() != null) {
            flakyExternalService.failFirstNCalls(request.failNext());
        }
        if (request.delayMs() != null) {
            flakyExternalService.setSimulatedLatencyMillis(request.delayMs());
        }
        if (request.alwaysFail() != null) {
            flakyExternalService.setAlwaysFail(request.alwaysFail());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "RESILIENCE");
        body.put("action", "EXTERNAL_CONFIG");
        body.put("failNext", flakyExternalService.failFirstNCallsConfigured());
        body.put("delayMs", flakyExternalService.simulatedLatencyMillisConfigured());
        body.put("alwaysFail", flakyExternalService.isAlwaysFail());
        body.put("nextStep", "POST /api/labs/resilience/circuit-breaker (bu yapılandırmayı kullanır)");
        return body;
    }

    @GetMapping("/external/state")
    public Map<String, Object> externalState() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "RESILIENCE");
        body.put("callCount", flakyExternalService.callCount());
        body.put("failNext", flakyExternalService.failFirstNCallsConfigured());
        body.put("delayMs", flakyExternalService.simulatedLatencyMillisConfigured());
        body.put("alwaysFail", flakyExternalService.isAlwaysFail());
        return body;
    }

    public record ExternalConfigRequest(Integer failNext, Long delayMs, Boolean alwaysFail) {
    }

    @PostMapping("/retry")
    public Map<String, Object> retry() {
        LabLog.banner("RESILIENCE", "RETRY");
        flakyExternalService.reset();
        flakyExternalService.failFirstNCalls(2); // ilk 2 çağrı başarısız, 3. başarılı

        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(20))
                .retryExceptions(FlakyExternalService.ExternalServiceException.class)
                .build();
        Retry retry = Retry.of("demo-retry", config);
        Supplier<String> decorated = Retry.decorateSupplier(retry, flakyExternalService::call);

        String result;
        String outcome;
        try {
            result = decorated.get();
            outcome = "SUCCEEDED_AFTER_RETRIES";
        } catch (FlakyExternalService.ExternalServiceException e) {
            result = null;
            outcome = "GAVE_UP: " + e.getMessage();
        }
        LabLog.line("3 deneme hakkı verildi, ilk 2 başarısız oldu, 3. denemede: {}", outcome);
        LabLog.lesson("Retry, geçici (transient) hataları şeffaf hale getirir - çağıranın kod tabanı hiç "
                + "retry mantığı yazmadan, arkada 3 kez denenmiş bir çağrıdan tek bir sonuç alır.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "RESILIENCE");
        body.put("mode", "RETRY");
        body.put("maxAttempts", 3);
        body.put("actualCallCount", flakyExternalService.callCount());
        body.put("outcome", outcome);
        body.put("result", result);
        body.put("lesson", "İlk 2 çağrı başarısız oldu ama Retry bunu OTOMATİK olarak 3. denemede telafi etti.");
        return body;
    }

    @PostMapping("/retry-exhausted")
    public Map<String, Object> retryExhausted() {
        LabLog.banner("RESILIENCE", "RETRY (tükendi)");
        flakyExternalService.reset();
        flakyExternalService.setAlwaysFail(true); // hiçbir deneme başarılı olmayacak

        RetryConfig config = RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(10)).build();
        Retry retry = Retry.of("demo-retry-exhausted", config);
        Supplier<String> decorated = Retry.decorateSupplier(retry, flakyExternalService::call);

        String outcome;
        try {
            decorated.get();
            outcome = "UNEXPECTEDLY_SUCCEEDED";
        } catch (FlakyExternalService.ExternalServiceException e) {
            outcome = "ALL_ATTEMPTS_EXHAUSTED";
        }
        LabLog.lesson("Retry sınırsız DEĞİLDİR - retry storm riskine karşı maxAttempts tükenince orijinal "
                + "exception çağırana ulaşır. 'Retry storm': çok agresif retry, zaten zorlanan bir bağımlılığı "
                + "DAHA DA kötüleştirebilir - bu yüzden backoff (waitDuration artan) önemlidir.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "RESILIENCE");
        body.put("mode", "RETRY_EXHAUSTED");
        body.put("maxAttempts", 3);
        body.put("actualCallCount", flakyExternalService.callCount());
        body.put("outcome", outcome);
        body.put("lesson", "3 deneme de başarısız oldu - Retry sonsuza kadar denemez, exception çağırana ulaştı.");
        return body;
    }

    @PostMapping("/circuit-breaker")
    public Map<String, Object> circuitBreaker() {
        LabLog.banner("RESILIENCE", "CIRCUIT BREAKER");
        // KASITLI OLARAK burada reset/setAlwaysFail YOK - bu senaryo, POST /external/config ile
        // ÖNCEDEN yapılandırılmış FlakyExternalService durumunu kullanır (ör.
        // {"failNext": 10, "delayMs": 0} ya da {"alwaysFail": true}). Hiç yapılandırılmadıysa
        // (varsayılan: hep başarılı), devre hiçbir zaman açılmaz - bu da BİLİNÇLİ bir şekilde
        // "yapılandırmadan senaryo anlamsızdır" dersini verir.

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .waitDurationInOpenState(Duration.ofMillis(300))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("demo-cb", config);
        Supplier<String> decorated = CircuitBreaker.decorateSupplier(circuitBreaker, flakyExternalService::call);

        List<String> stateTransitions = new java.util.ArrayList<>();
        stateTransitions.add(circuitBreaker.getState().name());

        // 4 çağrı, hepsi başarısız -> slidingWindow dolar, failure rate %100 -> OPEN
        for (int i = 0; i < 4; i++) {
            try {
                decorated.get();
            } catch (FlakyExternalService.ExternalServiceException ignored) {
                // beklenen
            }
        }
        stateTransitions.add(circuitBreaker.getState().name());
        LabLog.line("4 başarısız çağrıdan sonra circuit breaker durumu: {}", circuitBreaker.getState());

        // Devre AÇIKKEN bir çağrı daha dene - gerçek çağrı YAPILMADAN reddedilmeli
        int callCountBeforeShortCircuit = flakyExternalService.callCount();
        boolean shortCircuited = false;
        try {
            decorated.get();
        } catch (CallNotPermittedException e) {
            shortCircuited = true;
        } catch (FlakyExternalService.ExternalServiceException ignored) {
            // beklenmiyor ama olursa short-circuit olmadı demektir
        }
        int callCountAfterShortCircuit = flakyExternalService.callCount();

        LabLog.lesson("Circuit breaker OPEN durumdayken, altta yatan servisi HİÇ ÇAĞIRMADAN "
                + "CallNotPermittedException fırlattı - zaten zorlanan bir bağımlılığa istek göndermeyi "
                + "durdurdu (fail-fast). waitDurationInOpenState sonunda HALF_OPEN'a geçer, sınırlı sayıda "
                + "deneme çağrısına izin verir.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "RESILIENCE");
        body.put("mode", "CIRCUIT_BREAKER");
        body.put("stateTransitions", stateTransitions);
        body.put("callCountBeforeShortCircuit", callCountBeforeShortCircuit);
        body.put("callCountAfterShortCircuit", callCountAfterShortCircuit);
        body.put("underlyingServiceCalledDuringOpenState", callCountAfterShortCircuit != callCountBeforeShortCircuit);
        body.put("shortCircuited", shortCircuited);
        body.put("lesson", "OPEN durumdayken devre, gerçek servisi HİÇ ÇAĞIRMADAN isteği reddetti (fail-fast).");
        return body;
    }

    @PostMapping("/rate-limiter")
    public Map<String, Object> rateLimiter() {
        LabLog.banner("RESILIENCE", "RATE LIMITER");
        flakyExternalService.reset();

        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(2)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO) // izin yoksa hemen reddet, bekleme
                .build();
        RateLimiter rateLimiter = RateLimiter.of("demo-rl", config);
        Supplier<String> decorated = RateLimiter.decorateSupplier(rateLimiter, flakyExternalService::call);

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
        LabLog.line("1 saniyelik pencerede 2 izin verildi, 5 istek denendi: {} kabul, {} reddedildi", permitted, rejected);
        LabLog.lesson("RateLimiter, bağımlılığın SAĞLIĞINDAN bağımsız çalışır - servis SAĞLIKLI olsa bile, "
                + "belirlenen hızı aşan istekler reddedilir. CircuitBreaker'dan farkı budur: CircuitBreaker "
                + "GÖZLEMLENEN başarısızlığa tepki verir, RateLimiter ÖNCEDEN belirlenmiş bir tavana uyar.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "RESILIENCE");
        body.put("mode", "RATE_LIMITER");
        body.put("limitForPeriod", 2);
        body.put("periodSeconds", 1);
        body.put("attempted", 5);
        body.put("permitted", permitted);
        body.put("rejected", rejected);
        body.put("lesson", "Saniyede 2 izin verildi, 5 istekten sadece 2'si kabul edildi - servis sağlıklı olsa bile.");
        return body;
    }

    @PostMapping("/bulkhead")
    public Map<String, Object> bulkhead() throws InterruptedException {
        LabLog.banner("RESILIENCE", "BULKHEAD");
        flakyExternalService.reset();
        flakyExternalService.setSimulatedLatencyMillis(300);

        BulkheadConfig config = BulkheadConfig.custom().maxConcurrentCalls(2).maxWaitDuration(Duration.ZERO).build();
        Bulkhead bulkhead = Bulkhead.of("demo-bh", config);
        Supplier<String> decorated = Bulkhead.decorateSupplier(bulkhead, flakyExternalService::call);

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
        LabLog.line("maxConcurrentCalls=2, 4 eşzamanlı istek denendi: {} kabul, {} reddedildi (dolu bulkhead)",
                accepted.get(), rejected.get());
        LabLog.lesson("Bulkhead, bir bağımlılığa aynı anda kaç çağrının GİDEBİLECEĞİNİ sınırlar - bir "
                + "bağımlılığın YAVAŞLAMASI, çağıranın TÜM thread havuzunu tüketmesini önler (gemi bölmeleri "
                + "gibi: bir bölme su alsa bile gemi batmaz).");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "RESILIENCE");
        body.put("mode", "BULKHEAD");
        body.put("maxConcurrentCalls", 2);
        body.put("attempted", 4);
        body.put("accepted", accepted.get());
        body.put("rejected", rejected.get());
        body.put("lesson", "Sadece 2 eşzamanlı çağrıya izin verildi - kalan 2'si anında reddedildi (kuyruğa alınmadı).");
        return body;
    }

    @PostMapping("/timeout")
    public Map<String, Object> timeout() {
        LabLog.banner("RESILIENCE", "TIMEOUT");
        flakyExternalService.reset();
        flakyExternalService.setSimulatedLatencyMillis(500); // yavaş "dış servis"

        TimeLimiterConfig config = TimeLimiterConfig.custom().timeoutDuration(Duration.ofMillis(100)).build();
        TimeLimiter timeLimiter = TimeLimiter.of(config);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Supplier<Future<String>> futureSupplier = () -> executor.submit(flakyExternalService::call);

        String outcome;
        try {
            timeLimiter.executeFutureSupplier(futureSupplier);
            outcome = "UNEXPECTEDLY_COMPLETED";
        } catch (TimeoutException e) {
            outcome = "TIMED_OUT";
        } catch (Exception e) {
            outcome = "OTHER_ERROR: " + e.getClass().getSimpleName();
        } finally {
            executor.shutdownNow();
        }
        LabLog.lesson("Dış servis 500ms sürüyor ama timeout 100ms - çağıran, servisin GERÇEKTEN bitmesini "
                + "beklemedi, 100ms'de vazgeçti. Bu, yavaş bir bağımlılığın çağıranı da süresiz yavaşlatmasını "
                + "önler - servis arka planda çalışmaya devam edebilir ama sonucu artık kimse beklemiyor.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "RESILIENCE");
        body.put("mode", "TIMEOUT");
        body.put("simulatedServiceLatencyMillis", 500);
        body.put("timeoutMillis", 100);
        body.put("outcome", outcome);
        body.put("lesson", "500ms süren bir çağrı, 100ms timeout ile TIMED_OUT oldu - çağıran süresiz beklemedi.");
        return body;
    }

    @PostMapping("/fallback")
    public Map<String, Object> fallback() {
        LabLog.banner("RESILIENCE", "FALLBACK");
        flakyExternalService.reset();
        flakyExternalService.setAlwaysFail(true);

        RetryConfig config = RetryConfig.custom().maxAttempts(2).waitDuration(Duration.ofMillis(10)).build();
        Retry retry = Retry.of("demo-fallback", config);
        Supplier<String> decorated = Retry.decorateSupplier(retry, flakyExternalService::call);

        String result;
        boolean fallbackUsed;
        try {
            result = decorated.get();
            fallbackUsed = false;
        } catch (FlakyExternalService.ExternalServiceException e) {
            result = "DEFAULT_CACHED_RESPONSE"; // fallback: eski/varsayılan bir değer dön
            fallbackUsed = true;
        }
        LabLog.lesson("Fallback, TÜM retry'lar tükendiğinde çağırana yine de BİR ŞEY döner (hata fırlatmak "
                + "yerine) - ama bu her zaman doğru DEĞİLDİR: bir fallback'in döndürdüğü 'varsayılan' veri "
                + "BAYAT/YANLIŞ olabilir. Örneğin bir fiyat sorgusu başarısız olduğunda eski bir fiyatı fallback "
                + "olarak dönmek, kullanıcıya YANLIŞ bir fiyat göstermek riski taşır - fallback'in HER "
                + "senaryoda 'güvenli' olduğunu varsaymayın.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "RESILIENCE");
        body.put("mode", "FALLBACK");
        body.put("result", result);
        body.put("fallbackUsed", fallbackUsed);
        body.put("lesson", "Retry tükendi, ama çağıran yine de bir yanıt aldı (exception yerine) - fallback her zaman 'doğru' veri anlamına gelmez.");
        return body;
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "RESILIENCE");
        body.put("flakyServiceCallCount", flakyExternalService.callCount());
        return body;
    }
}
