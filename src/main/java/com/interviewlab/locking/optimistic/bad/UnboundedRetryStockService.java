package com.interviewlab.locking.optimistic.bad;

import com.interviewlab.locking.entity.Product;
import com.interviewlab.locking.entity.LockingProductRepository;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * NE YANLIŞ?
 * {@link ObjectOptimisticLockingFailureException} için, maksimum deneme sayısı ve denemeler
 * arasında backoff olmadan bir {@code while (true)} döngüsünde yeniden deneme yapılıyor.
 *
 * <p>NEDEN YANLIŞ?
 * Satır üzerindeki çekişme sürekliyse (bkz. docs/optimistic-locking.md'deki hot row notu),
 * bu döngü CPU/veritabanının izin verdiği kadar hızlı yeniden deneyerek süresiz dönebilir -
 * başarısız her deneme veritabanına tam bir gidiş-dönüştür ve bu da karşısında mücadele
 * ettiği çekişmeyi daha da ARTIRIR.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Sürekli eş zamanlı yazımlar altındaki tek bir hot row, bir çağıranın retry döngüsünü asla
 * geri dönmeyen ve veritabanını dövmeyi asla bırakmayan bir thread'e dönüştürebilir - bu, aynı
 * satır için yarışan diğer TÜM transaction'ları da kötüleştiren, kendi kendine yol açılmış,
 * lokal bir denial-of-service durumudur.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code OptimisticLockingTest.shouldRetryPastAnyReasonableBoundWhenUnbounded()}, durdurmadan
 * önce bir rakipten tam olarak 5 çakışan yazım zorlar ve bu servisin deneme sayısının makul
 * bir sınırı aştığını gösterir (rakip sonsuza kadar devam etseydi burada hiçbir şeyin onu
 * durdurmayacağını kanıtlar); oysa
 * {@link com.interviewlab.locking.optimistic.good.RetryingStockService} 3 denemeden sonra
 * temiz bir şekilde vazgeçer.
 *
 * <p>NASIL DÜZELTİLİR?
 * Maksimum bir deneme sayısı ve denemeler arasında bir backoff - bkz.
 * {@link com.interviewlab.locking.optimistic.good.RetryingStockService}.
 */
@Service
public class UnboundedRetryStockService {

    private static final Logger log = LoggerFactory.getLogger(UnboundedRetryStockService.class);

    private final LockingProductRepository productRepository;
    private final TransactionTemplate transactionTemplate;

    public UnboundedRetryStockService(LockingProductRepository productRepository, PlatformTransactionManager transactionManager) {
        this.productRepository = productRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public int decreaseStockRetryingForever(Long productId, int amount) {
        AtomicInteger attempts = new AtomicInteger();
        while (true) {
            attempts.incrementAndGet();
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    Product product = productRepository.findById(productId).orElseThrow();
                    product.decreaseStock(amount);
                });
                return attempts.get();
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("attempt {} conflicted - retrying immediately, no bound, no backoff", attempts.get());
                // sleep yok, maksimum yok: veritabanının izin verdiği kadar hızlı yeniden dener
            }
        }
    }
}
