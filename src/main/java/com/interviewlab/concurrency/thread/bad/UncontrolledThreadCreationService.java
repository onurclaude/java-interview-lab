package com.interviewlab.concurrency.thread.bad;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * NE YANLIŞ?
 * {@link #handle(Runnable)}'a yapılan her çağrı, aynı anda kaç thread var olabileceğine
 * dair hiçbir sınır olmadan {@code new Thread(task).start()} yapar.
 *
 * <p>NEDEN YANLIŞ?
 * Her platform {@link Thread} bir stack rezerve eder (JVM/OS varsayılanlarına göre genellikle
 * ~512KB-1MB) ve gerçek bir OS thread'i ile context-switching yükü maliyetine sahiptir.
 * Burada hiçbir şey eşzamanlı thread sayısını CPU'nun ya da alt katman kaynaklarının (DB
 * bağlantı havuzu, uzak servis) gerçekte kaldırabileceği miktarla sınırlamaz - sayı, çağıranların
 * bu metodu çağırma hızıyla tam olarak aynı hızda büyür.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Gelen isteklerde ani bir artış (ya da bir retry fırtınası), saniyeler içinde binlerce
 * thread oluşturur; her biri context-switching yoluyla CPU için çekişir, stack'ler için
 * heap/native belleği tüketir ve/veya çok daha küçük bir bağlantı limitine sahip bir alt
 * katman sistemine binlerce eşzamanlı bağlantı açar - tek, belirgin bir nedeni olmayan,
 * "her şey aynı anda yavaşladı" gibi görünen bir kesinti.
 *
 * <p>GERÇEKTE NE OLUYOR / NASIL REPRODUCE EDİLİR?
 * {@code ThreadCreationBoundsTest.shouldLetConcurrentThreadCountGrowUnboundedWithRawThreads()},
 * serbest bırakılana kadar hepsi bloklanan 200 görev tetikler, {@link #peakConcurrent} ile
 * eşzamanlı çalışan thread sayısının zirvesini takip eder ve bunun ~200'e kadar tırmandığını
 * gösterir - yani tam olarak çağıranın ani yük büyüklüğü kadar, bu sınıfta bunu sınırlayan
 * hiçbir şey yokken.
 *
 * <p>NASIL DÜZELTİLİR?
 * Bunun yerine işi, alt katman kaynaklarının gerçekten kaldırabileceği boyutta ayarlanmış,
 * sınırlı bir {@link java.util.concurrent.ExecutorService}'e gönder (bkz.
 * {@link com.interviewlab.concurrency.thread.good.BoundedTaskSubmissionService}). Tam
 * production ayarlaması (kuyruk sınırları, reddetme politikası) executor laboratuvarında
 * (docs/executor-service.md) ele alınmıştır.
 */
@Service
public class UncontrolledThreadCreationService {

    private final AtomicInteger concurrent = new AtomicInteger();
    private final AtomicInteger peakConcurrent = new AtomicInteger();

    public void handle(Runnable task) {
        Thread thread = new Thread(() -> {
            int now = concurrent.incrementAndGet();
            peakConcurrent.accumulateAndGet(now, Math::max);
            try {
                task.run();
            } finally {
                concurrent.decrementAndGet();
            }
        });
        thread.start();
    }

    public int peakConcurrent() {
        return peakConcurrent.get();
    }

    public void resetPeak() {
        peakConcurrent.set(0);
    }
}
