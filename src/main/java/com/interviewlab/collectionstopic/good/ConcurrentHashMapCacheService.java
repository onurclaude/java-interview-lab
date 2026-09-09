package com.interviewlab.collectionstopic.good;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link com.interviewlab.collectionstopic.bad.UnsafeHashMapCacheService}'in doğru karşılığı:
 * {@link ConcurrentHashMap}, güvenli eşzamanlı okuma ve yazmalara izin verir ve
 * iterator'ları fail-fast yerine zayıf tutarlıdır (fırlatmak yerine eşzamanlı değişikliğe
 * tolerans gösterirler).
 */
public class ConcurrentHashMapCacheService {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public void put(String key, String value) {
        cache.put(key, value);
    }

    public Map<String, String> rawMap() {
        return cache;
    }
}
