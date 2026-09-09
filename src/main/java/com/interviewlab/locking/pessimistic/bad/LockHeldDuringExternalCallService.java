package com.interviewlab.locking.pessimistic.bad;

import com.interviewlab.locking.entity.Product;
import com.interviewlab.locking.entity.LockingProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NE YANLIŞ?
 * Pessimistic bir satır kilidi alınıyor ve ardından transaction (dolayısıyla kilit)
 * serbest bırakılmadan ÖNCE yavaş bir "harici çağrı" ({@code Thread.sleep} ile simüle
 * edilmiştir, gerçek bir HTTP/API çağrısının yerini tutar) gerçekleşiyor.
 *
 * <p>NEDEN YANLIŞ?
 * Bir veritabanı satır kilidi, transaction onu tutarken ne yapıyor olursa olsun -
 * veritabanıyla hiçbir ilgisi olmayan bir şeyi beklemek dahil - transaction'ın TÜM süresi
 * boyunca tutulur. O satırı isteyen her diğer transaction, yalnızca lokal hesaplamanın değil,
 * bu kodun kontrol etmediği harici bir sistemin tüm gecikmesinin (ve olası timeout riskinin)
 * de arkasında sıraya girer.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Yavaş veya bozulmuş bir downstream API, normalde hızlı olan bir satır kilidini
 * çok saniyelik (ya da daha uzun) bir kilide dönüştürür ve aynı satıra dokunan her eş zamanlı
 * istek bunun arkasında sıraya girer - tek bir yavaş bağımlılık çağrısı, aslında ilgisiz olan
 * bir kod yolu için veritabanı seviyesinde bir darboğaza dönüşür.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code PessimisticLockingTest.shouldHoldLockForFullDurationOfExternalCall()}, ikinci bir
 * kilit talep edicisinin ne kadar beklediğini ölçer ve bunun simüle edilen çağrının tüm
 * gecikmesini içerdiğini gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * Harici çağrıyı kilidi almadan ÖNCE yap, ya da (daha iyisi) tamamen herhangi bir
 * transaction'ın dışında yap ve kilidi gerçekten tutan transaction'ı olabildiğince kısa tut -
 * ideal olarak sadece read-modify-write işleminin kendisi kadar.
 */
@Service
public class LockHeldDuringExternalCallService {

    private final LockingProductRepository productRepository;

    public LockHeldDuringExternalCallService(LockingProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public void lockThenCallExternalServiceThenDecrease(Long productId, int amount, long simulatedExternalCallMillis) {
        Product product = productRepository.findByIdForUpdate(productId).orElseThrow();
        simulateSlowExternalCall(simulatedExternalCallMillis); // kilit bunun tamamı boyunca tutulur
        product.decreaseStock(amount);
    }

    private static void simulateSlowExternalCall(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
