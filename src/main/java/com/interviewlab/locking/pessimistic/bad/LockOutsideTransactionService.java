package com.interviewlab.locking.pessimistic.bad;

import com.interviewlab.locking.entity.LockingProductRepository;
import org.springframework.stereotype.Service;

/**
 * NE YANLIŞ?
 * Yine de pessimistic bir kilit almaya çalışan {@link #lockWithoutTransaction} üzerinde
 * {@code @Transactional} yok.
 *
 * <p>NEDEN YANLIŞ?
 * Bir satır kilidi, ancak onu tutan veritabanı transaction'ının ömrü boyunca anlamlıdır -
 * kilidi tutacak bir transaction olmadan "bu satırı kilitle" demek bir çelişkidir. Spring
 * Data JPA'nın repository metotları, kendi örtük transaction'larında yalnızca o TEK çağrının
 * SÜRESİ boyunca çalışır; bir repository çağrısı içinde alınıp bırakılan bir kilit hiçbir
 * şeyi korumaz (çağıran onun üzerinde işlem yapabilmeden önce kilit zaten gitmiştir) - JPA'nın
 * sessizce işe yaramaz bir şey yapmak yerine doğrudan reddetmesinin nedeni tam olarak budur.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Çağrı derhal {@link jakarta.persistence.TransactionRequiredException} ile başarısız olur -
 * burada bu gürültülü ve anında gerçekleşir, ama bu bir çağrı zincirinin derinliklerindeyse,
 * bir repository metoduna {@code @Lock} ekleyen ama ÇAĞIRANIN da transactional olması
 * gerektiğini fark etmeyen biri için kafa karıştırıcı bir hata olabilir. Bir ek katman daha
 * var: çağrı bir Spring Data repository'sinden geçtiği için, Spring'in exception translation'ı
 * ham {@code TransactionRequiredException}'ı otomatik olarak
 * {@link org.springframework.dao.InvalidDataAccessApiUsageException}'a sarar - stack trace'te
 * gördüğünüz üst seviye tip budur, JPA'nın kendi tipi değil.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code PessimisticLockingTest.shouldThrowWhenAcquiringPessimisticLockOutsideTransaction()}
 * bunu çevresinde hiçbir transaction olmadan çağırır ve istisnayı gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * Çağıran metoda {@code @Transactional} ekle - bkz.
 * {@link com.interviewlab.locking.pessimistic.good.PessimisticStockService}.
 */
@Service
public class LockOutsideTransactionService {

    private final LockingProductRepository productRepository;

    public LockOutsideTransactionService(LockingProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /** Burada @Transactional yok - hata budur. */
    public void lockWithoutTransaction(Long productId) {
        productRepository.findByIdForUpdate(productId);
    }
}
