package com.interviewlab.concurrency.volatiletopic.bad;

/**
 * NE YANLIŞ?
 * {@link #increment()}, {@code volatile} bir alan üzerinde {@code counter++} yapar ve alan
 * {@code volatile} olduğu için thread-safe olduğu varsayılır.
 *
 * <p>NEDEN YANLIŞ?
 * {@code volatile}, <b>görünürlüğü (visibility)</b> garanti eder (her thread en son yazılan
 * değeri okur ve JIT/CPU bunun etrafında yeniden sıralama yapamaz) - <b>atomikliği</b> garanti
 * ETMEZ. {@code counter++}, oku-değiştir-yaz işlemidir: mevcut değeri oku, artır, geri yaz.
 * İki thread, herhangi biri yazmadan önce aynı değeri okuyabilir ve bir artırma işlemi
 * kaybolur - tam olarak banka hesabı race condition'ıyla aynı şekilde bir hata, sadece
 * bunu düzeltmiş gibi görünen bir alan anahtar kelimesiyle.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * "volatile olduğu için thread-safe" bir sayaç (hit sayaçları, devam eden istek sayaçları,
 * metrikler) yük altında sessizce eksik sayar ve bu fark eşzamanlılıkla birlikte büyür - asla
 * exception fırlatmadığı, sadece sessizce gerçekte olduğundan daha küçük bir sayı ürettiği
 * için gözden kaçırılması çok kolay bir hatadır.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code VolatileAndAtomicTest.shouldLoseIncrementOperationsWithVolatileCounter()}, birçok
 * eşzamanlı artırma işlemi çalıştırır ve son sayının gerçekleştirilen artırma işlemi
 * sayısından daha az olduğunu gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * {@link java.util.concurrent.atomic.AtomicInteger} kullan (bkz.
 * {@link com.interviewlab.concurrency.atomic.AtomicCounterService}) ya da
 * {@code synchronized} kullan - {@code volatile} tek başına bileşik (compound) bir işlemi
 * düzeltemez.
 */
public class VolatileCounter {

    private volatile int counter;

    public void increment() {
        counter++;
    }

    public int get() {
        return counter;
    }
}
