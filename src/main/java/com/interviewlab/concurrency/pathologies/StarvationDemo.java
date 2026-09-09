package com.interviewlab.concurrency.pathologies;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * Starvation (kaynak açlığı): bir thread'in, meşru şekilde beklediği bir kaynağa tekrar tekrar
 * erişiminin engellenmesidir. RİSKİ göstermenin en temiz, deterministik yolu bir zamanlama
 * yarışı değildir (bir şeyin "genellikle" ne kadar beklediği makineler/JDK'lar arasında
 * doğası gereği kararsızdır); bunun yerine
 * {@link java.util.concurrent.locks.ReentrantLock}'in adillik (fairness) sözleşmesinin
 * doğrudan bir sonucudur:
 *
 * <ul>
 *   <li>Bir <b>adil (fair)</b> kilit, kilit için zaten sıraya girmiş bir thread'in daha sonra
 *       gelen herhangi bir thread'den önce kilidi alacağını GARANTİ EDER - yapısı gereği
 *       starvation mümkün değildir.</li>
 *   <li>Bir <b>adil olmayan (non-fair)</b> kilit BÖYLE BİR SÖZ VERMEZ: yepyeni bir çağıran,
 *       çok daha uzun süredir bekleyen bir thread'in önüne "dalabilir" (barge), çünkü yeni
 *       gelen bir thread önce kuyruğu kontrol etmeden kilidi hemen almayı dener. Sürekli yeni
 *       gelenler olduğu sürece, zaten bekleyen bir thread teorik olarak süresiz bekleyebilir.</li>
 * </ul>
 *
 * <p>{@link #victimServedBeforeLatecomer} tam olarak bu sırayı test eder: bir "kurban"
 * (victim) önce sıraya girer, bir "geç kalan" (latecomer) kurbanın sıraya girdiği
 * doğrulandıktan sonra gelir ve metot kilidin ilk hangisine verildiğini raporlar. Adil bir
 * kilide karşı çalıştırıldığında, bu her çalıştırmada TRUE'dur - kesin bir garanti. Adil
 * olmayan bir kilide karşı çalıştırıldığında, JDK hiçbir yönde garanti vermez - asıl mesele
 * de budur: starvation, gerçek ve izin verilen bir sonuçtur ve adil (fair) mod özellikle bunu
 * engellemek için var olur.
 */
public final class StarvationDemo {

    private StarvationDemo() {
    }

    public static boolean victimServedBeforeLatecomer(Lock lock) throws InterruptedException {
        List<String> acquisitionOrder = new CopyOnWriteArrayList<>();
        lock.lock(); // ana thread önce kilidi tutar, hem kurbanı hem geç kalanı sıraya girmeye/beklemeye zorlar
        try {
            Thread victim = new Thread(() -> runAndRecord(lock, acquisitionOrder, "victim"), "starvation-victim");
            victim.setDaemon(true);
            victim.start();

            // Kurbanın sadece başlamış olması değil, kilit için gerçekten park edilmiş (AQS park -> WAITING/TIMED_WAITING)
            // olması beklenir, böylece aşağıdaki geç kalandan önce "sıraya girmiş" olduğu net şekilde belli olur.
            long deadline = System.currentTimeMillis() + 2000;
            while (victim.getState() != Thread.State.WAITING && victim.getState() != Thread.State.TIMED_WAITING
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(5);
            }

            Thread latecomer = new Thread(() -> runAndRecord(lock, acquisitionOrder, "latecomer"), "starvation-latecomer");
            latecomer.setDaemon(true);
            latecomer.start();
            Thread.sleep(100); // biz serbest bırakmadan önce geç kalanın da sıraya girmesi için gerçek bir şans tanır

            CountDownLatch bothDone = new CountDownLatch(1);
            Thread waiter = new Thread(() -> {
                try {
                    victim.join(TimeUnit.SECONDS.toMillis(5));
                    latecomer.join(TimeUnit.SECONDS.toMillis(5));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    bothDone.countDown();
                }
            });
            waiter.setDaemon(true);
            waiter.start();

            lock.unlock(); // serbest bırak: sıradaki kimin alacağı tamamen kilidin adillik (fairness) politikasına bağlıdır
            bothDone.await(6, TimeUnit.SECONDS);
        } finally {
            if (((java.util.concurrent.locks.ReentrantLock) lock).isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        return acquisitionOrder.equals(List.of("victim", "latecomer"));
    }

    private static void runAndRecord(Lock lock, List<String> order, String name) {
        lock.lock();
        try {
            order.add(name);
        } finally {
            lock.unlock();
        }
    }
}
