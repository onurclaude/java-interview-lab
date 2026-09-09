package com.interviewlab.aop.bad;

import com.interviewlab.aop.TrackExecutionTime;
import org.springframework.stereotype.Service;

/**
 * NE YANLIŞ?
 * {@link #processOrder()}, {@code this.slowStep()}'i doğrudan çağırır ve
 * {@link #slowStep()} üzerindeki {@link TrackExecutionTime}'ın süresini loglamasını bekler.
 *
 * <p>NEDEN YANLIŞ?
 * Bu projedeki diğer tüm self-invocation hatalarıyla aynı mekanizma: Spring AOP advice'ı
 * (bu aspect dahil) bean'i saran bir proxy üzerinden uygulanır. Bean'in içinden kendi
 * metoduna yapılan bir çağrı bu proxy'yi tamamen atlar, bu yüzden {@code @Around} advice'ı
 * hiç çalışmaz - {@code slowStep()} sadece sıradan, zamanlanmamış bir metot çağrısı olarak
 * çalışır.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Her yavaş çağrıyı yakalaması gereken bir monitoring/timing aspect'i, tam olarak aynı
 * sınıf içinden yapılan çağrıları - genellikle en business-kritik olanları - sessizce
 * kaçırır ve bir metodun aslında öyle olmadığı halde hızlı olduğu (ya da hiç çağrılmadığı)
 * gibi yanlış bir izlenim verir.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code AopSelfInvocationTest.shouldNotTrackExecutionTimeDuringSelfInvocation()},
 * {@code processOrder()}'ı çağırır ve aspect'in {@code slowStep()} için hiçbir zaman bir
 * süre kaydetmediğini gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * Zamanlanan metodu farklı bir bean'e taşı ve onu inject edilmiş bir referans üzerinden
 * çağır - bkz. {@link com.interviewlab.aop.good.OrderProcessingService} /
 * {@link com.interviewlab.aop.good.SlowStepService}.
 */
@Service
public class SelfInvocationTimingService {

    public void processOrder() {
        this.slowStep(); // self-invocation: aspect'in proxy'sini atlar
    }

    @TrackExecutionTime
    public void slowStep() {
        sleep(20);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
