package com.interviewlab.concurrency.thread.good;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * {@link com.interviewlab.concurrency.thread.bad.UncontrolledThreadCreationService}'in
 * doğru karşılığı: iş, küçük, sabit boyutlu bir havuza (pool) gönderilir, bu yüzden aynı anda
 * kaç görev gönderilirse gönderilsin eşzamanlı çalışma sınırlıdır. (Bu, minimal bir
 * gösterimdir - gerçek production {@link ThreadPoolExecutor} ayarlaması: sınırlı kuyruklar,
 * reddetme politikaları, özel thread factory'leri, ayrılmış {@code executor} paketinde ele
 * alınır.)
 */
@Service
public class BoundedTaskSubmissionService {

    private static final int POOL_SIZE = 4;

    private final ExecutorService executor = new ThreadPoolExecutor(
            POOL_SIZE, POOL_SIZE, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1000));

    private final AtomicInteger concurrent = new AtomicInteger();
    private final AtomicInteger peakConcurrent = new AtomicInteger();

    public void handle(Runnable task) {
        executor.submit(() -> {
            int now = concurrent.incrementAndGet();
            peakConcurrent.accumulateAndGet(now, Math::max);
            try {
                task.run();
            } finally {
                concurrent.decrementAndGet();
            }
        });
    }

    public int peakConcurrent() {
        return peakConcurrent.get();
    }

    public int currentConcurrent() {
        return concurrent.get();
    }

    public void resetPeak() {
        peakConcurrent.set(0);
    }

    public int poolSize() {
        return POOL_SIZE;
    }

    /** Doğru yaşam döngüsü: bu bean yok edildiğinde havuzun thread'lerini serbest bırakır (tam shutdown/awaitTermination/shutdownNow yaşam döngüsü docs/executor-service.md içinde ele alınmıştır). */
    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
