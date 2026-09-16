package com.interviewlab.javacore.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link CacheAsideProductService}'in BAD karşılaştırması: HİÇBİR cache katmanı yok - her
 * {@link #read(Long)} çağrısı, aynı id tekrar tekrar okunsa BİLE, "DB"ye gider. Cache-Aside'ın
 * çözdüğü problem TAM OLARAK budur: sık okunan, seyrek değişen veri için gereksiz tekrarlanan
 * DB yükü.
 */
public class NoCacheProductService {

    private final Map<Long, String> database = new ConcurrentHashMap<>();
    private final AtomicInteger databaseReadCount = new AtomicInteger();

    public void seed(Long id, String name) {
        database.put(id, name);
    }

    public String read(Long id) {
        databaseReadCount.incrementAndGet(); // HER çağrıda - cache YOK
        return database.get(id);
    }

    public int databaseReadCount() {
        return databaseReadCount.get();
    }
}
