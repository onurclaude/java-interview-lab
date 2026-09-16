package com.interviewlab.resilience;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Gerçek bir "başarısız olabilen dış servis"in (ödeme gateway'i, üçüncü taraf API'si) yerini
 * tutan, davranışı kontrol edilebilir sahte bir bağımlılık - Resilience4j lab'larının hepsi
 * bunun üzerinden çalışır. Gerçek production kodunda bu, `RestTemplate`/`WebClient` ile
 * yapılan bir HTTP çağrısı olurdu.
 */
@Component
public class FlakyExternalService {

    private final AtomicInteger callCount = new AtomicInteger();
    private volatile int failFirstNCalls = 0;
    private volatile long simulatedLatencyMillis = 0;
    private volatile boolean alwaysFail = false;

    public String call() {
        int attempt = callCount.incrementAndGet();
        if (simulatedLatencyMillis > 0) {
            sleep(simulatedLatencyMillis);
        }
        if (alwaysFail || attempt <= failFirstNCalls) {
            throw new ExternalServiceException("simulated failure on call #" + attempt);
        }
        return "success on call #" + attempt;
    }

    public void reset() {
        callCount.set(0);
        failFirstNCalls = 0;
        simulatedLatencyMillis = 0;
        alwaysFail = false;
    }

    public void failFirstNCalls(int n) {
        failFirstNCalls = n;
    }

    public void setAlwaysFail(boolean value) {
        alwaysFail = value;
    }

    public void setSimulatedLatencyMillis(long millis) {
        simulatedLatencyMillis = millis;
    }

    public int callCount() {
        return callCount.get();
    }

    public int failFirstNCallsConfigured() {
        return failFirstNCalls;
    }

    public long simulatedLatencyMillisConfigured() {
        return simulatedLatencyMillis;
    }

    public boolean isAlwaysFail() {
        return alwaysFail;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static final class ExternalServiceException extends RuntimeException {
        public ExternalServiceException(String message) {
            super(message);
        }
    }
}
