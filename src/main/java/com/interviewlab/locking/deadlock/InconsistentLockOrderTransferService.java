package com.interviewlab.locking.deadlock;

import com.interviewlab.locking.entity.Product;
import com.interviewlab.locking.entity.LockingProductRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NE YANLIŞ?
 * {@link #transferStock}, çağıranın parametreleri hangi sırayla verdiğinden bağımsız olarak
 * her zaman önce {@code fromId}'yi, sonra {@code toId}'yi kilitler - bu,
 * {@code concurrency.pathologies.bad.InconsistentLockOrderTransferService}'in JVM içi
 * deadlock'unun {@code synchronized} yerine {@code SELECT ... FOR UPDATE} kullanan GERÇEK,
 * veritabanı seviyesindeki bir versiyonudur.
 *
 * <p>NEDEN YANLIŞ?
 * T1, {@code transferStock(A, B, ...)} çağırırken (önce A'yı kilitler, sonra B'yi ister) T2
 * eş zamanlı olarak {@code transferStock(B, A, ...)} çağırırsa (önce B'yi kilitler, sonra
 * A'yı ister), her biri diğerinin ihtiyaç duyduğunu elinde tutar. Bu sefer döngüyü fark edip
 * onu kırmak için iki transaction'dan birini öldüren, JVM'in {@code ThreadMXBean}'i değil,
 * PostgreSQL'in kendi deadlock dedektörüdür.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Aynı iki satır arasında zıt yönlerde yapılan iki meşru transfer veritabanı seviyesinde
 * deadlock'a girebilir; bu, uygulamanın yakalayıp yeniden denemeye hazır olması gereken bir
 * veritabanı hatasıyla ikisinden birini iptal eder - JVM içi bir deadlock'tan farklı olarak
 * bu sonsuza kadar takılı kalmaz; Postgres bunu zorla çözer, ama yalnızca gerçek (genelde
 * kısa olsa da) bir tespit gecikmesinden sonra.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code PessimisticLockingTest.shouldDeadlockAtDatabaseLevelWithInconsistentLockOrder()}
 * tam olarak bu A'dan B'ye / B'den A'ya senaryosunu eş zamanlı çalıştırır ve taraflardan
 * birinin bir veritabanı deadlock hatasıyla başarısız olduğunu gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * Kilitleri her zaman tek, global olarak tutarlı bir sırayla al - bkz.
 * {@link DeterministicOrderTransferService}.
 */
@Service
public class InconsistentLockOrderTransferService {

    private final LockingProductRepository productRepository;

    public InconsistentLockOrderTransferService(LockingProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public void transferStock(Long fromId, Long toId, int amount, CountDownLatch afterFirstLockSignal, CountDownLatch proceedSignal) {
        Product from = productRepository.findByIdForUpdate(fromId).orElseThrow();
        afterFirstLockSignal.countDown();
        awaitLatch(proceedSignal);
        Product to = productRepository.findByIdForUpdate(toId).orElseThrow(); // burada bloklanabilir/deadlock oluşabilir
        from.decreaseStock(amount);
        to.setStock(to.getStock() + amount);
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
