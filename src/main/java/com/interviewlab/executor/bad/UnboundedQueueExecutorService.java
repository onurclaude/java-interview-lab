package com.interviewlab.executor.bad;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * NE YANLIŞ?
 * {@code Executors.newFixedThreadPool(n)}, ona bir thread sayısı verdiğiniz için sınırlı
 * görünür - ama arkasındaki kuyruk sınırsız bir
 * {@link java.util.concurrent.LinkedBlockingQueue}'dur.
 *
 * <p>NEDEN YANLIŞ?
 * "Sınırlı thread sayısı", "uçuşta sınırlı iş" ile aynı şey değildir. Görevler {@code n}
 * thread'in onları tüketebileceğinden daha hızlı gelirse, reddedilmezler ve çağıranı
 * bloklamazlar - sadece kuyrukta, hiçbir sınır olmadan, birer birer
 * {@code Runnable}/{@code Callable} nesnesi (artı yakaladığı her şey) olarak birikirler.
 * {@code Executors.newCachedThreadPool()} tam tersi bir soruna sahiptir: kuyruğu sıfır
 * kapasiteli bir {@link java.util.concurrent.SynchronousQueue}'dur, bu yüzden kuyruğa almak
 * yerine sürekli yepyeni thread'ler oluşturur - sınırsız kuyruk yerine sınırsız thread sayısı.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Yavaş bir alt katman bağımlılığı, görevlerin geldiğinden daha yavaş bitmesine neden olur.
 * {@code newFixedThreadPool} ile, kuyruk JVM'in heap'i tükenene kadar sınırsız büyür - ilk
 * bakışta bir bellek sızıntısı gibi görünen bir {@code OutOfMemoryError}. {@code
 * newCachedThreadPool} ile, aynı backpressure'sız (geri baskısız) geliş deseni bunun yerine
 * sınırsız thread oluşturur ve native bellek/OS thread limitlerini tüketir.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code ExecutorServiceTest.shouldQueueUnboundedWorkWithNewFixedThreadPool()}, bir
 * {@code newFixedThreadPool(2)}'ye worker thread sayısından çok daha fazla bloklayan görev
 * gönderir ve her ekstra görevle birlikte iç kuyruk boyutunun büyüdüğünü gösterir - hiçbir
 * şey çağırana geri baskı (pushback) uygulamaz.
 *
 * <p>NASIL DÜZELTİLİR?
 * *Sınırlı* bir kuyruk ve kuyruk dolduğunda ne olacağını tanımlayan bir
 * {@link java.util.concurrent.RejectedExecutionHandler} ile açık bir {@link ThreadPoolExecutor}
 * oluştur - bkz. {@link com.interviewlab.executor.good.ProductionThreadPoolExecutorFactory}.
 */
public final class UnboundedQueueExecutorService {

    private UnboundedQueueExecutorService() {
    }

    public static ExecutorService newFixedPoolWithHiddenUnboundedQueue(int threadCount) {
        return Executors.newFixedThreadPool(threadCount);
    }
}
