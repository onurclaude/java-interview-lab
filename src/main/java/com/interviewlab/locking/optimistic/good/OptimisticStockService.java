package com.interviewlab.locking.optimistic.good;

import com.interviewlab.locking.entity.Product;
import com.interviewlab.locking.entity.LockingProductRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Temel, doğru optimistic-locking akışı: yükle, değiştir, dirty checking ile commit sırasında
 * {@code @Version} tarafından korunan UPDATE'in ya başarılı olmasına ya da istisna
 * fırlatmasına izin ver. Burada bilinçli olarak retry mantığı yok - yeniden deneyip
 * denenmeyeceği/nasıl deneneceğine dair (ayrıca değerlendirilen) karar için bkz.
 * {@link RetryingStockService}.
 */
@Service
public class OptimisticStockService {

    private static final Logger log = LoggerFactory.getLogger(OptimisticStockService.class);

    private final LockingProductRepository productRepository;

    public OptimisticStockService(LockingProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public void decreaseStock(Long productId, int amount) {
        Product product = productRepository.findById(productId).orElseThrow();
        log.info("[{}] loaded Product id={} version={} stock={}",
                Thread.currentThread().getName(), product.getId(), product.getVersion(), product.getStock());
        product.decreaseStock(amount);
        // açıkça save()/flush() çağrısına gerek yok: dirty checking, commit sırasında
        // UPDATE ... SET stock=?, version=? WHERE id=? AND version=? sorgusunu üretir
    }

    /** Yalnızca test için hook: yükler, sinyal verir, bekler, SONRA değiştirir - bir testin kesin bir senaryoyu zorlamasını sağlar. */
    @Transactional
    public void loadSignalWaitThenDecrease(Long productId, int amount, CountDownLatch afterLoadSignal, CountDownLatch beforeMutateWait) {
        Product product = productRepository.findById(productId).orElseThrow();
        log.info("[{}] loaded Product id={} version={} stock={}",
                Thread.currentThread().getName(), product.getId(), product.getVersion(), product.getStock());
        afterLoadSignal.countDown();
        awaitLatch(beforeMutateWait);
        product.decreaseStock(amount);
        log.info("[{}] about to commit stock={} (in-memory version={})",
                Thread.currentThread().getName(), product.getStock(), product.getVersion());
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for the other transaction");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
