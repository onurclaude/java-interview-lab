package com.interviewlab.locking.pessimistic.good;

import com.interviewlab.locking.entity.Product;
import com.interviewlab.locking.entity.LockingProductRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code findByIdForUpdate} ({@link LockingProductRepository} üzerinde
 * {@code @Lock(LockModeType.PESSIMISTIC_WRITE)} ile tanımlanmıştır), PostgreSQL'de gerçek bir
 * {@code SELECT ... FOR UPDATE} çalıştırır - bu, uygulama seviyesinde bir kontrol değil,
 * veritabanının kendisinin aldığı gerçek bir satır kilididir. Aynı kilidi talep eden ikinci
 * bir transaction, birincisi commit ya da rollback edip (kilidi serbest bırakana) kadar
 * veritabanı seviyesinde fiziksel olarak bloke olur - bu,
 * {@code PessimisticLockingTest.shouldBlockSecondTransactionWithPessimisticWriteLock()}
 * tarafından kanıtlanmıştır; bu test T2'nin gerçekte ne kadar süre beklediğini ölçer.
 */
@Service
public class PessimisticStockService {

    private static final Logger log = LoggerFactory.getLogger(PessimisticStockService.class);

    private final LockingProductRepository productRepository;

    public PessimisticStockService(LockingProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public void lockHoldThenDecrease(Long productId, int amount, CountDownLatch afterLockAcquiredSignal, CountDownLatch releaseSignal) {
        Product product = productRepository.findByIdForUpdate(productId).orElseThrow();
        log.info("[{}] acquired FOR UPDATE lock on Product id={}", Thread.currentThread().getName(), productId);
        afterLockAcquiredSignal.countDown();
        awaitLatch(releaseSignal);
        product.decreaseStock(amount);
        log.info("[{}] committing, will release the lock", Thread.currentThread().getName());
    }

    @Transactional
    public void lockThenDecreaseImmediately(Long productId, int amount) {
        Product product = productRepository.findByIdForUpdate(productId).orElseThrow();
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
