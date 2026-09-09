package com.interviewlab.concurrency.synchronization.bad;

import java.util.concurrent.locks.ReentrantLock;

/**
 * NE YANLIŞ?
 * {@link #incrementThenMaybeThrow(boolean)}, {@code lock.lock()} ve {@code lock.unlock()}'u
 * hiçbir {@code try/finally} olmadan, sıradan ardışık ifadeler olarak çağırır.
 *
 * <p>NEDEN YANLIŞ?
 * {@code synchronized}'in aksine, bir {@link ReentrantLock} JVM tarafından asla otomatik
 * olarak serbest bırakılmaz - "kilit alındı" ile "kilit serbest bırakıldı" arasını
 * bağlayan dil seviyesinde bir garanti yoktur. {@code lock()} ile {@code unlock()} arasında
 * herhangi bir şey exception fırlatırsa (bir hata, null bir değer, alt katmandaki bir arıza),
 * çalışma doğrudan {@code unlock()} çağrısını atlar ve kilit sonsuza kadar tutulmuş olur.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Başarısız olan tek bir istek kilidi kalıcı olarak sıkıştırır. Sonraki her çağıran
 * {@code lock()} üzerinde sonsuza kadar bloke olur (ya da birisi {@code tryLock}
 * kullanmışsa zaman aşımına uğrar) - tek bir exception, process'in ömrü boyunca tüm bir kod
 * yolunu devre dışı bırakmıştır ve düzeltmek için yeniden başlatma gerekir.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code SynchronizationTest.shouldLeaveLockPermanentlyHeldWhenExceptionSkipsUnlock()}, bu
 * metodu kritik bölümün ortasında bir exception zorlayan bir argümanla çağırır, ardından
 * ikinci bir thread'in {@code tryLock()} çağrısının kilit hiç serbest bırakılmadığı için
 * başarısız olduğunu gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * {@code lock()}'u her zaman {@code try { ... } finally { lock.unlock(); } } ile eşleştir -
 * bkz. {@link com.interviewlab.concurrency.synchronization.good.ReentrantLockCounter}.
 */
public class LockWithoutFinallyService {

    private final ReentrantLock lock = new ReentrantLock();
    private int count;

    public void incrementThenMaybeThrow(boolean shouldThrow) {
        lock.lock();
        if (shouldThrow) {
            throw new IllegalStateException("simulated failure mid-critical-section - unlock() below is skipped");
        }
        count++;
        lock.unlock();
    }

    public ReentrantLock lock() {
        return lock;
    }

    public int getCount() {
        return count;
    }
}
