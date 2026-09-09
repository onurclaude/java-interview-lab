package com.interviewlab.locking.optimistic.bad;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NE YANLIŞ?
 * {@link com.interviewlab.locking.optimistic.good.OptimisticStockService} ile aynı
 * yükle-değiştir-commit şekline sahip, ama {@link NoVersionProduct} üzerinde çalışıyor - yani
 * {@code @Version} alanı olmayan bir entity üzerinde.
 *
 * <p>NEDEN YANLIŞ?
 * Sırf JPA kullanıyor ve dirty checking yapıyor diye bunun "optimistic locking'e sahip
 * olduğuna" inanmak. {@code @Version} olmadan, UPDATE'in WHERE cümlesi sadece
 * {@code WHERE id = ?}'dir - satırın okunduğundan bu yana değişip değişmediği hiçbir zaman
 * kontrol edilmez.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Eş zamanlı iki azaltma işlemi sessizce bir LOST UPDATE üretir: hangi transaction ikinci
 * commit ederse, ilkinin değişikliğini kendi bayat veriye dayalı hesaplamasıyla ezer ve iki
 * azaltmanın birleşik etkisi asla uygulanmaz - ne istisna, ne log, sadece yanlış bir son
 * sayı.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code OptimisticLockingTest.shouldLoseUpdateWithoutVersionField()}, {@code @Version}
 * testindekiyle tamamen aynı T1/T2 senaryosunu çalıştırır ve bir istisna fırlatılması yerine
 * son stok değerinin YANLIŞ (olması gerekenden yüksek) olduğunu gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * {@code @Version} ekle - bkz. {@link com.interviewlab.locking.optimistic.good.OptimisticStockService}.
 */
@Service
public class NoVersionStockService {

    private final NoVersionProductRepository repository;

    public NoVersionStockService(NoVersionProductRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void loadSignalWaitThenDecrease(Long id, int amount, CountDownLatch afterLoadSignal, CountDownLatch beforeMutateWait) {
        NoVersionProduct product = repository.findById(id).orElseThrow();
        afterLoadSignal.countDown();
        awaitLatch(beforeMutateWait);
        product.decreaseStock(amount);
    }

    @Transactional
    public void decreaseImmediately(Long id, int amount) {
        NoVersionProduct product = repository.findById(id).orElseThrow();
        product.decreaseStock(amount);
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
