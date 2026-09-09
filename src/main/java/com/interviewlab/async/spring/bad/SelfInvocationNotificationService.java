package com.interviewlab.async.spring.bad;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * NE YANLIŞ?
 * {@link #notifyUser(String)}, {@code this.sendAsync(message)}'i doğrudan çağırıyor.
 *
 * <p>NEDEN YANLIŞ?
 * {@code @Transactional} self-invocation ile tamamen aynı kök neden (bkz.
 * {@code transaction.propagation.bad.SelfInvocationPaymentService}): {@code @Async} da bir
 * Spring AOP proxy'si aracılığıyla uygulanır. Bean'in içinden kendi metoduna yapılan bir
 * çağrı asla bu proxy'den geçmez, bu yüzden çağrıyı bir executor'a gönderip hemen dönecek olan
 * advice hiç çalışmaz - metot sadece, sıradan herhangi bir metot çağrısı gibi, çağıranın kendi
 * thread'inde senkron olarak çalışır.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * "Bu fire-and-forget'tir, arka planda çalışır" varsayımıyla yazılan kod (örneğin bir istek
 * tamamlandıktan sonra bildirim gönderme), bunun yerine inline çalışır ve tüm gecikmesini
 * çağırana ekler - normalde 10ms'de dönmesi gereken bir istek, artık beklemesi gerekmeyen
 * 500ms'lik bir e-posta/bildirim çağrısını bekler.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code AsyncSelfInvocationTest.shouldNotApplyAsyncDuringSelfInvocation()}, {@code notifyUser}'ı
 * çağırır ve "asenkron" işin çağıranla tamamen aynı thread'de çalıştığını gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * {@code @Async} metodunu farklı bir bean'e taşıyın ve onu enjekte edilmiş bir referans
 * üzerinden çağırın - bkz. {@link com.interviewlab.async.spring.good.NotificationService}.
 */
@Service
public class SelfInvocationNotificationService {

    private final AtomicReference<String> lastAsyncThreadName = new AtomicReference<>();

    public String notifyUser(String message) {
        this.sendAsync(message); // self-invocation: @Async proxy'sini tamamen atlar
        return Thread.currentThread().getName();
    }

    @Async("labAsyncExecutor")
    public void sendAsync(String message) {
        lastAsyncThreadName.set(Thread.currentThread().getName());
    }

    public String lastAsyncThreadName() {
        return lastAsyncThreadName.get();
    }
}
