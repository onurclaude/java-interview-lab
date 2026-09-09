package com.interviewlab.collectionstopic.good;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * {@link com.interviewlab.collectionstopic.bad.CompoundCheckThenActCacheService}'in doğru
 * karşılığı: {@code computeIfAbsent}, "orada mı? değilse hesapla ve sakla" sırasını, map'in
 * bakış açısından tek bir atomik operasyon olarak gerçekleştirir - eşzamanlı ilk erişim
 * altında bile, anahtar başına sadece bir çağıranın hesaplama fonksiyonu gerçekten çalışır.
 *
 * <p><b>Bilinmesi gereken bir uyarı:</b> {@code computeIfAbsent}'in eşleme fonksiyonu yavaş
 * olmamalı veya aynı map'e dokunmamalıdır (o bucket üzerinde fiilen bir kilit tutulurken
 * çalışır) - gerçekten yavaş/bloklayan bir hesaplama için, hesaplamayı map dışında yapmayı ve
 * sonucu yayınlamak için {@code putIfAbsent} kullanmayı düşünün; bu, hesaplamanın ara sıra
 * birden fazla kez çalışmasını kabul etmek anlamına gelir (nadir bir tekrar hesaplamayı,
 * diğer anahtarları hiç bloklamama karşılığında takas etmek).
 */
public class AtomicComputeIfAbsentCacheService {

    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final AtomicInteger computationCount = new AtomicInteger();

    public String getOrCompute(String key, Supplier<String> expensiveComputation) {
        return cache.computeIfAbsent(key, k -> {
            computationCount.incrementAndGet();
            return expensiveComputation.get();
        });
    }

    public int computationCount() {
        return computationCount.get();
    }
}
