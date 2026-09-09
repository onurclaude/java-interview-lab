package com.interviewlab.executor.good;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@code Executors.newXxx()} kolaylık factory'sinin arkasına gizlemek yerine her parametrenin
 * bilinçli olarak seçildiği, production şeklinde bir {@link ThreadPoolExecutor}.
 *
 * <p><b>corePoolSize / maximumPoolSize</b> - kalıcı (sustained) worker sayısına karşı ani
 * artış (burst) worker sayısı. {@code corePoolSize}'ı aşan thread'ler yalnızca kuyruk
 * dolduğunda oluşturulur ve {@code keepAliveTime}'a tabi olanlar da bunlardır.
 *
 * <p><b>keepAliveTime</b> - {@code corePoolSize}'ı aşan boşta bir thread'in geri alınmadan
 * önce ne kadar süre hayatta kaldığı. Bir ani artışın havuzu kalıcı olarak şişirmesini önler.
 *
 * <p><b>sınırlı {@link ArrayBlockingQueue}</b> - asıl backpressure (geri baskı) mekanizması:
 * {@code Executors.newFixedThreadPool}'da eksik olan tam olarak budur (bkz.
 * {@link com.interviewlab.executor.bad.UnboundedQueueExecutorService}). Sonlu bir kapasite,
 * sistemin sınırsız bir tampon yerine "kabul edilmiş ama henüz başlamamış iş" için açık,
 * boyutlandırılmış bir tampona sahip olduğu anlamına gelir.
 *
 * <p><b>isimlendirilmiş bir {@link ThreadFactory}</b> - böyle bir şey olmadan, her havuzun
 * thread'leri genel isimlerle adlandırılır ({@code pool-N-thread-M}), bu da bir thread
 * dump'ının takılı bir thread'in hangi havuza ait olduğunu söylemek için işe yaramaz hale
 * getirir. Özel bir factory ayrıca, çağıranın thread'inin ne olduğunu miras almak yerine
 * daemon durumunu ve önceliği bilinçli olarak ayarlamanıza olanak tanır.
 *
 * <p><b>{@link RejectedExecutionHandler}</b> - hem kuyruk hem de {@code maximumPoolSize}
 * tükendiğinde ne olacağı; en yaygın iki seçim ve aralarındaki backpressure ödünleşimi için
 * {@link #abortPolicyExecutor} ve {@link #callerRunsPolicyExecutor}'a bakın.
 */
public final class ProductionThreadPoolExecutorFactory {

    private ProductionThreadPoolExecutorFactory() {
    }

    /** AbortPolicy: {@link java.util.concurrent.RejectedExecutionException} ile reddeder - çağıranın aşırı yükü açıkça ele alması gerekir. */
    public static ThreadPoolExecutor abortPolicyExecutor(int corePoolSize, int maxPoolSize, int queueCapacity) {
        return build(corePoolSize, maxPoolSize, queueCapacity, new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * CallerRunsPolicy: exception fırlatmak yerine, görev ÇAĞIRAN thread üzerinde çalışır.
     * Bu, basit ve etkili bir backpressure (geri baskı) biçimidir - gönderen yavaşlatılır
     * (görev N+1'i, görev N'yi çalıştırmayı kendisi bitirene kadar gönderemez), bu da hiç iş
     * kaybetmeden geliş hızını doğal olarak kısar. Ödünleşim şudur: çağıran (ör. bir HTTP
     * istek thread'i) artık havuzun işini eşzamanlı olarak yapar, bu da bir üst katmanda
     * (ör. web sunucusunun kendi istek işleme havuzunda) istek gecikme sıçramalarına ya da
     * thread havuzu tükenmesine yol açabilir.
     */
    public static ThreadPoolExecutor callerRunsPolicyExecutor(int corePoolSize, int maxPoolSize, int queueCapacity) {
        return build(corePoolSize, maxPoolSize, queueCapacity, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    private static ThreadPoolExecutor build(int corePoolSize, int maxPoolSize, int queueCapacity, RejectedExecutionHandler handler) {
        AtomicInteger threadNumber = new AtomicInteger(1);
        ThreadFactory namedDaemonFactory = runnable -> {
            Thread thread = new Thread(runnable, "lab-executor-" + threadNumber.getAndIncrement());
            thread.setDaemon(true); // sadece demo/test kolaylığı - yaşam döngüsü için docs/executor-service.md'ye bakın
            return thread;
        };
        return new ThreadPoolExecutor(
                corePoolSize, maxPoolSize, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                namedDaemonFactory,
                handler);
    }

    /** Doğru shutdown yaşam döngüsü: yeni iş kabul etmeyi durdur, kuyruktaki işin bitmesine izin ver, bekle, sonra gerekirse zorla durdur. */
    public static boolean shutdownGracefully(ThreadPoolExecutor executor, long timeoutSeconds) throws InterruptedException {
        executor.shutdown(); // yeni görev kabul etmeyi durdur; kuyruktaki + çalışan görevler devam eder
        if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
            executor.shutdownNow(); // kuyruktaki görevleri iptal et, çalışanları kesintiye uğrat (interrupt)
            return executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS);
        }
        return true;
    }
}
