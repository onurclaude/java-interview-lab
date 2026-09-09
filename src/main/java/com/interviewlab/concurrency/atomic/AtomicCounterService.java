package com.interviewlab.concurrency.atomic;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link AtomicInteger}, {@link com.interviewlab.concurrency.volatiletopic.bad.VolatileCounter}
 * (sıradan {@code volatile int}) doğru olmadığı halde NEDEN doğrudur?
 *
 * <p>{@code AtomicInteger.incrementAndGet()} "volatile artı bir şey" değildir - donanım
 * seviyesinde bir <b>Compare-And-Set (CAS)</b> komutuyla uygulanır: mevcut değeri oku, yeni
 * değeri hesapla, ardından <em>yalnızca</em> araya başka bir thread girip değeri değiştirmemişse
 * bunu atomik olarak geri yaz; başka bir thread önce davranmışsa, tüm oku-hesapla-yaz işlemini
 * baştan tekrar dene. İşte bu yeniden deneme döngüsü, tüm oku-değiştir-yaz işlemini tek bir
 * bütün olarak atomik hale getirir - sade bir {@code volatile} alanın asla sağlayamayacağı bir
 * şey, çünkü görünürlük (visibility) tek başına "benim okumam ile yazmam arasında başka biri
 * buna dokundu mu?" sorusuna hiçbir şey söylemez.
 *
 * <p><b>synchronized mi, Atomic mi?</b> {@code synchronized}, kaybeden thread'i bloklar (thread
 * park edilir ve sırasını bekler - bu da bir OS context switch'e yol açabilir); CAS tabanlı
 * atomikler ise kaybeden thread'in hiç bloklanmadan CPU üzerinde dönmeye (spin) ve yeniden
 * denemeye devam etmesine izin verir. Düşük ile orta düzey çekişme (contention) altında bu
 * önemli ölçüde daha ucuzdur. <b>Çok yüksek çekişme altında</b> (aynı atomiğe çok sayıda
 * thread'in yoğun şekilde saldırdığı durumlarda), CAS yeniden denemelerinin kendisi pahalı hale
 * gelebilir - her thread sürekli başarısız olup yeniden okur - bu noktada dilimlenmiş/parçalara
 * ayrılmış bir sayaç (ör. {@link java.util.concurrent.atomic.LongAdder}) genellikle tek bir
 * {@link AtomicInteger}'dan daha iyi performans gösterir.
 */
public class AtomicCounterService {

    private final AtomicInteger counter = new AtomicInteger();

    public void increment() {
        counter.incrementAndGet();
    }

    public int get() {
        return counter.get();
    }
}
