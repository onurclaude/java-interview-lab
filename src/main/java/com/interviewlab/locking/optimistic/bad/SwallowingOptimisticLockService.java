package com.interviewlab.locking.optimistic.bad;

import com.interviewlab.locking.entity.Product;
import com.interviewlab.locking.entity.LockingProductRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * NE YANLIŞ?
 * Version kontrollü güncellemenin etrafında {@code catch (Exception e) { }}.
 *
 * <p>NEDEN YANLIŞ?
 * {@link ObjectOptimisticLockingFailureException}, Hibernate/Spring'in çağırana doğru bir
 * şekilde "bu güncelleme GERÇEKLEŞMEDİ - başka biri satırı önce değiştirdi" dediği durumdur.
 * Bu istisnayı sessizce yutmak, gürültülü ve doğru bir hatayı sessiz ve yanlış bir başarıya
 * dönüştürür: çağıranın, istediği değişikliğin hiç uygulanmadığını bilmesinin hiçbir yolu
 * kalmaz.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Bir stok azaltma işlemi "başarılı olur" (çağırana hiçbir istisna ulaşmaz) ama aslında hiç
 * gerçekleşmemiştir - envanter gerçeklikle uyumsuz hale gelir sürüklenir ve nedenini
 * araştırırken işaret edilebilecek hiçbir hata yoktur.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code OptimisticLockingTest.shouldSilentlyDoNothingWhenSwallowingOptimisticLockException()}
 * bir çakışmayı zorlar ve hiçbir istisna yayılmadan, kaybeden tarafın amaçladığı azaltmadan
 * stokun değişmediğini gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * En azından logla ve çağıranın üzerinde işlem yapabileceği bir şey olarak yeniden fırlat;
 * daha iyisi, sınırlı sayıda yeniden dene - bkz.
 * {@link com.interviewlab.locking.optimistic.good.RetryingStockService}.
 */
@Service
public class SwallowingOptimisticLockService {

    private final LockingProductRepository productRepository;
    private final TransactionTemplate transactionTemplate;

    public SwallowingOptimisticLockService(LockingProductRepository productRepository, PlatformTransactionManager transactionManager) {
        this.productRepository = productRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** Yalnızca test için: OptimisticStockService'inkini yansıtan, kesin bir T1/T2 senaryosunu zorlayan hook. */
    public void loadSignalWaitThenDecreaseSwallowingConflicts(Long productId, int amount,
                                                               CountDownLatch afterLoadSignal, CountDownLatch beforeMutateWait) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Product product = productRepository.findById(productId).orElseThrow();
                afterLoadSignal.countDown();
                awaitLatch(beforeMutateWait);
                product.decreaseStock(amount);
            });
        } catch (ObjectOptimisticLockingFailureException e) {
            // Yutuldu: log yok, yeniden fırlatma yok. Çağıran bunun başarılı olduğuna inanır.
        }
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

    public void decreaseStockSwallowingConflicts(Long productId, int amount) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Product product = productRepository.findById(productId).orElseThrow();
                product.decreaseStock(amount);
            });
        } catch (Exception e) {
            // Yutuldu: log yok, yeniden fırlatma yok. Çağıran bunun başarılı olduğuna inanır.
        }
    }
}
