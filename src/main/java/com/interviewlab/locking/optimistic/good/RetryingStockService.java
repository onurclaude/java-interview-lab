package com.interviewlab.locking.optimistic.good;

import com.interviewlab.locking.entity.Product;
import com.interviewlab.locking.entity.LockingProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Optimistic-lock çakışması için backoff'lu, sınırlı sayıda yeniden deneme.
 *
 * <p><b>Neden private/protected bir yardımcı metotta {@code @Transactional} yerine
 * {@link TransactionTemplate}?</b> AYNI sınıf içinden {@code this} üzerinde
 * {@code @Transactional} bir metodu çağırmak, bu projenin sürekli işaret ettiği self-invocation
 * hatasıdır (bkz. docs/transactions.md) - bu, her seferinde düzgün şekilde commit/rollback
 * edilen bir denemeyi yeniden denemek yerine, hiç transaction olmadan sessizce çalışırdı.
 * {@code TransactionTemplate}'i programatik olarak kullanmak proxy'yi tamamen devre dışı
 * bırakır: {@code execute(...)}'e yapılan her çağrı gerçek, bağımsız bir transaction'dır ve
 * bu kısımda annotation tabanlı bir proxy söz konusu olmadığından self-invocation mümkün
 * değildir.
 *
 * <p><b>Yeniden denemek her zaman doğru mudur?</b> Hayır. Burada yeniden denemek güvenlidir
 * çünkü {@code decreaseStock} doğası gereği amaç bakımından idempotent'tir: güncel stoku
 * yeniden okuyup "N çıkar" işlemini yeniden uygulamak, çakışma durumunda tam olarak olması
 * gerekendir. Bu, aynı version kontrolüyle korunmayan harici bir yan etkiye sahip işlemler
 * için genel olarak GÜVENLİ DEĞİLDİR - örneğin bir optimistic-lock hatasında körlemesine
 * "bu kredi kartına para çek" işlemini yeniden denemek, hata çekim başarılı olduktan sonra
 * ama lokal commit'ten önce gerçekleşmişse müşteriden çift ücret alınmasına yol açabilir.
 * Yalnızca LOKAL, version korumalı yazımı yeniden dene; idempotent olmayan harici çağrılar
 * içeren bütün bir iş operasyonunu asla körlemesine yeniden deneme.
 */
@Service
public class RetryingStockService {

    private static final Logger log = LoggerFactory.getLogger(RetryingStockService.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MILLIS = 20;

    private final LockingProductRepository productRepository;
    private final TransactionTemplate transactionTemplate;

    public RetryingStockService(LockingProductRepository productRepository, PlatformTransactionManager transactionManager) {
        this.productRepository = productRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public int decreaseStockWithRetry(Long productId, int amount) {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    Product product = productRepository.findById(productId).orElseThrow();
                    product.decreaseStock(amount);
                });
                return attempts;
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempts >= MAX_ATTEMPTS) {
                    throw new IllegalStateException(
                            "gave up after " + attempts + " optimistic-lock conflicts on product " + productId, e);
                }
                long backoff = BASE_BACKOFF_MILLIS * attempts; // basit doğrusal backoff
                log.info("optimistic lock conflict on attempt {}, backing off {}ms before retry", attempts, backoff);
                sleep(backoff);
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
