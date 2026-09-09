package com.interviewlab.concurrency.synchronization.good;

import java.util.concurrent.locks.StampedLock;

/**
 * {@link StampedLock}, okuma/yazmanın üzerine üçüncü bir mod ekler: <b>iyimser okuma
 * (optimistic read)</b>. Bu modda hiç kilit alınmaz - sadece bir damga (stamp) hatırlanır,
 * alanlar okunur ve ardından {@code validate(stamp)} ile bu sırada bir yazma olup olmadığı
 * kontrol edilir. Hiçbir şey değişmemişse, (kilitsiz, çok ucuz) okuma geçerli sayılır; bir
 * şey değiştiyse, bu kod gerçek bir okuma kilidine geri döner. Bu, yazmalar nadirse neredeyse
 * sıfır maliyetli okumalar karşılığında küçük bir yeniden okuma ihtimalini kabul eder - okuma
 * ağırlıklı, kısa kritik bölümlü kullanım durumları için {@link ReadWriteLockCache}'in bir
 * üst seviyesidir. {@link java.util.concurrent.locks.ReentrantReadWriteLock}'in doğrudan
 * yerine geçmez: StampedLock reentrant değildir ve {@code Condition}'ları desteklemez, bu
 * yüzden sadece buradaki gibi basit değer tutucuları için kullanılmalıdır.
 */
public class StampedLockPoint {

    private final StampedLock stampedLock = new StampedLock();
    private double x;
    private double y;

    public void move(double deltaX, double deltaY) {
        long stamp = stampedLock.writeLock();
        try {
            x += deltaX;
            y += deltaY;
        } finally {
            stampedLock.unlockWrite(stamp);
        }
    }

    /** İyimser okuma (optimistic read): yalnızca eşzamanlı bir yazma tespit edilirse gerçek bir okuma kilidine döner. */
    public double distanceFromOrigin() {
        long stamp = stampedLock.tryOptimisticRead();
        double currentX = x;
        double currentY = y;
        if (!stampedLock.validate(stamp)) {
            stamp = stampedLock.readLock();
            try {
                currentX = x;
                currentY = y;
            } finally {
                stampedLock.unlockRead(stamp);
            }
        }
        return Math.sqrt(currentX * currentX + currentY * currentY);
    }
}
