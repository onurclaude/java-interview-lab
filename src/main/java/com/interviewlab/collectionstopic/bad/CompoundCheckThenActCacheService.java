package com.interviewlab.collectionstopic.bad;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * NE YANLIŞ?
 * Bir {@link ConcurrentHashMap} üzerinde
 * {@code if (!map.containsKey(key)) map.put(key, computeValue());}.
 *
 * <p>NEDEN YANLIŞ?
 * {@link ConcurrentHashMap}, her BİREYSEL metot çağrısını ({@code containsKey}, {@code put})
 * thread-safe yapar - iki çağrının bir SIRA olarak birlikte atomik olmasını SAĞLAMAZ. İki
 * thread aynı anda {@code containsKey(key)}'i çağırabilir, ikisi de {@code false} görür
 * (kayıt henüz orada değildir) ve ikisi de değeri hesaplayıp {@code put()} çağırmaya devam
 * eder - ikinci {@code put()}, birincisinin üzerine yazar ve - asıl maliyet - pahalı
 * hesaplama gereksiz yere iki kez çalışır. Bu, {@code concurrency.race.bad.UnsafeBankAccount}
 * ile aynı "check-then-act" yarış deseni, sadece düz bir alan yerine thread-safe bir
 * koleksiyon üzerinde.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Her anahtar için pahalı bir değeri (bir DB sorgusu, uzak bir çağrı) tam olarak bir kez
 * hesaplaması gereken bir cache, eşzamanlı ilk erişim altında bunun yerine birden fazla kez
 * hesaplar ve hesaplamanın bağlı olduğu her ne ise onun üzerindeki yükü, tam da en az
 * istenen anda katlar (bir cache miss fırtınası).
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code CollectionsConcurrencyTest.shouldComputeValueMultipleTimesWithCheckThenActPattern()}
 * AYNI anahtar için birçok eşzamanlı ilk erişim tetikler ve pahalı supplier'ın birden fazla
 * kez çalıştığını gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * Map'in kendi atomik bileşik operasyonlarını kullanın - {@code putIfAbsent} ya da daha iyisi,
 * {@code computeIfAbsent} - bkz.
 * {@link com.interviewlab.collectionstopic.good.AtomicComputeIfAbsentCacheService}.
 */
public class CompoundCheckThenActCacheService {

    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final AtomicInteger computationCount = new AtomicInteger();

    public String getOrCompute(String key, Supplier<String> expensiveComputation) {
        if (!cache.containsKey(key)) {
            String value = expensiveComputation.get();
            computationCount.incrementAndGet();
            cache.put(key, value);
        }
        return cache.get(key);
    }

    public int computationCount() {
        return computationCount.get();
    }
}
