package com.interviewlab.javacore.cache;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link LinkedHashMap}'in "access-order" modu (üçüncü constructor argümanı {@code true}),
 * bir elemana her {@code get()}/{@code put()} yapıldığında onu iterasyon sırasında EN SONA
 * taşır - bu da en-az-kullanılanı (least-recently-used) her zaman iterasyonun BAŞINDA
 * bırakır. {@link #removeEldestEntry} override'ı, {@code put()} sonrası boyut sınırı
 * aşıldığında en eskiyi (iterasyondaki ilk eleman = en az kullanılan) otomatik olarak siler -
 * bu, sıfırdan bir LRU algoritması yazmadan, JDK'nın kendi collection'ıyla LRU cache elde
 * etmenin standart yoludur.
 *
 * <p><b>Production için:</b> bu basit implementasyon thread-safe DEĞİLDİR (ham
 * {@code LinkedHashMap} gibi) - gerçek bir production cache için Caffeine/Guava Cache gibi
 * hazır, thread-safe, TTL/weight-based eviction destekleyen bir kütüphane tercih edilmelidir.
 * Bu sınıf sadece MEKANİZMAYI (access-order + removeEldestEntry) göstermek içindir.
 */
public class LruCache<K, V> extends LinkedHashMap<K, V> {

    private final int maxSize;

    public LruCache(int maxSize) {
        super(16, 0.75f, true); // accessOrder=true: her erişimde eleman sona taşınır
        this.maxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxSize; // put() sonrası boyut sınırı aşıldıysa en eskiyi (LRU) sil
    }
}
