package com.interviewlab.javacore.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cache-Aside (Lazy Loading) deseninin canlı bir örneği: uygulama kodu cache'i AÇIKÇA
 * yönetir (cache kütüphanesi "DB'den nasıl okunur" bilmez, sadece bir key-value deposudur).
 *
 * <p>Okuma akışı: önce cache'e bak → varsa döndür (cache hit) → yoksa "DB"den oku, cache'e
 * yaz, döndür (cache miss).
 * <p>Yazma akışı: "DB"ye yaz → cache'i INVALIDATE et (silmek, güncellemekten daha güvenlidir
 * - bir sonraki okuma DB'den taze veriyi çeker; cache'i doğrudan güncellemeye çalışmak,
 * eşzamanlı bir başka yazmayla yarışıp bayat veriyi cache'e "kilitleyebilir").
 *
 * <p><b>Failure senaryosu:</b> DB yazması BAŞARILI olur ama invalidate ÇAĞRISI (ağ
 * sorunu, cache node çökmesi) başarısız olursa, cache BAYAT veriyi TUTMAYA DEVAM EDER - bu
 * Cache-Aside'ın bilinen bir tutarlılık riskidir (genelde kısa bir TTL ile azaltılır, tam
 * çözülmez).
 */
public class CacheAsideProductService {

    private final Map<Long, String> database = new ConcurrentHashMap<>(); // yavaş "DB"nin yerini tutar
    private final LruCache<Long, String> cache = new LruCache<>(100);
    private final AtomicInteger databaseReadCount = new AtomicInteger();

    public void seed(Long id, String name) {
        database.put(id, name);
    }

    public String read(Long id) {
        String cached = cache.get(id);
        if (cached != null) {
            return cached; // cache hit - DB'ye hiç gidilmedi
        }
        databaseReadCount.incrementAndGet();
        String fromDb = database.get(id); // cache miss - DB'den oku
        if (fromDb != null) {
            cache.put(id, fromDb);
        }
        return fromDb;
    }

    public void write(Long id, String name) {
        database.put(id, name); // önce DB'ye yaz
        cache.remove(id); // sonra cache'i invalidate et (güncelleme değil, silme)
    }

    public int databaseReadCount() {
        return databaseReadCount.get();
    }

    public boolean isCached(Long id) {
        return cache.containsKey(id);
    }
}
