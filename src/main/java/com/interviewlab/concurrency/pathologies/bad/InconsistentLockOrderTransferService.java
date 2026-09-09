package com.interviewlab.concurrency.pathologies.bad;

import com.interviewlab.concurrency.pathologies.Account;

/**
 * NE YANLIŞ?
 * {@link #transfer}, çağıranın verdiği sıraya göre önce {@code from}, sonra {@code to}
 * kilitler.
 *
 * <p>NEDEN YANLIŞ?
 * Thread T1 {@code transfer(A, B, ...)} çağırırken (önce A sonra B kilitlenir) aynı anda
 * thread T2 {@code transfer(B, A, ...)} çağırırsa (önce B sonra A kilitlenir), klasik bir
 * döngüsel bekleme (circular wait) mümkün hale gelir: T1, A'yı tutar ve B'yi ister; T2, B'yi
 * tutar ve A'yı ister. Hiçbiri ilerleyemez ve hiçbiri elindekini asla bırakmaz - bir deadlock
 * oluşur. Bunun için iş mantığında herhangi bir hataya gerek yoktur, sadece iki thread'in aynı
 * anda meşru şekilde zıt yönlerde transfer yapması yeterlidir.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * İki istek işleyen thread, uygulama kilitlerini tutarken kalıcı olarak asılı kalır; hiçbir
 * şey çökmez, hiçbir hata loglanmaz, thread'ler sadece asla geri dönmez - bu durum yalnızca
 * takılı kalan istek/thread sayısının artmasıyla ve nihayetinde thread havuzunun
 * (thread-pool) tükenmesiyle fark edilir.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code DeadlockLivelockStarvationTest.shouldDeadlockWithInconsistentLockOrder()}, tam
 * olarak bu A'dan-B'ye / B'den-A'ya iç içe geçmeyi (interleaving) eşzamanlı olarak çalıştırır
 * ve {@link java.lang.management.ThreadMXBean#findDeadlockedThreads()} üzerinden gerçek bir
 * deadlock olduğunu doğrular.
 *
 * <p>NASIL DÜZELTİLİR?
 * Çağrı yönünden bağımsız olarak kilitleri her zaman tek, global olarak tutarlı bir sırayla
 * al - bkz.
 * {@link com.interviewlab.concurrency.pathologies.good.DeterministicLockOrderTransferService}.
 */
public class InconsistentLockOrderTransferService {

    public void transfer(Account from, Account to, double amount) {
        synchronized (from) {
            sleepBriefly(); // deadlock'un güvenilir şekilde reproduce edilmesi için race penceresini genişletir
            synchronized (to) {
                from.balance -= amount;
                to.balance += amount;
            }
        }
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
