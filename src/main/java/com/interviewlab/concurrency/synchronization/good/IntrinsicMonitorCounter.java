package com.interviewlab.concurrency.synchronization.good;

/**
 * İki {@code synchronized} sözdizimini yan yana gösterir; ikisi de {@code this} üzerinde
 * kilitlendiği için kanıtlanabilir şekilde aynı kilittir: {@code synchronized} bir instance
 * metodu, tüm gövdesini {@code synchronized (this) { ... }} içine sarmakla tamamen
 * eşdeğerdir. {@link #incrementViaMethod()} içindeki bir thread, {@link #incrementViaBlock()}'a
 * girmeye çalışan başka bir thread'i bloklar - ve tersi de geçerlidir; bu durum
 * {@code SynchronizationTest.shouldTreatSynchronizedMethodAndBlockAsTheSameMonitor()} ile
 * kanıtlanmıştır.
 *
 * <p><b>Gerçek kodda neden özel (private) bir kilit nesnesi tercih edilmeli?</b> {@code this}
 * üzerinde kilitlemek, bu nesneye referansı olan herhangi bir dış, ilgisiz kodun da (kazara
 * ya da kötü niyetli şekilde) bu nesne üzerinde senkronize olabilmesi anlamına gelir - bu da
 * bu sınıfın kontrol edemediği şekillerde çekişmeyi (contention) artırır. Sınıf dışına açık
 * olmayan, özel (private), final bir kilit nesnesi genellikle daha güvenli varsayılan
 * seçimdir - burada iki sözdizimi arasındaki eşdeğerliği kolayca kanıtlamak amacıyla salt
 * {@code this} kullanılmıştır.
 */
public class IntrinsicMonitorCounter {

    private int count;

    public synchronized void incrementViaMethod() {
        count++;
    }

    public void incrementViaBlock() {
        synchronized (this) {
            count++;
        }
    }

    public synchronized int get() {
        return count;
    }
}
