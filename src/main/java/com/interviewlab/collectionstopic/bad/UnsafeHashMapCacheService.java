package com.interviewlab.collectionstopic.bad;

import java.util.HashMap;
import java.util.Map;

/**
 * NE YANLIŞ?
 * Herhangi bir senkronizasyon olmadan birden fazla thread'den okunan ve yazılan düz bir
 * {@link HashMap}.
 *
 * <p>NEDEN YANLIŞ?
 * {@code HashMap} hiçbir thread-safety garantisi vermez. Bir başka thread iterate ederken
 * veya sadece okurken meydana gelen eşzamanlı yapısal değişiklik ({@code put()}'un tetiklediği
 * dahili bir resize/rehash), dahili bucket/node yapısını bozabilir. Sık sık örnek verilen
 * belirti, map'in altında değiştiğini algılayan bir iterator'dan fırlatılan
 * {@link java.util.ConcurrentModificationException}'dır - ama daha tehlikeli olan hata modu
 * (çoğunlukla Java 8 öncesinde görülür, ama altta yatan garanti eksikliği bugün de geçerlidir)
 * sessiz bozulmadır: kaybolan kayıtlar, ya da - tarihsel olarak - bir thread'i sonsuza kadar
 * döndüren döngü oluşturan bozuk bir bucket. "Genelde çökmüyor" ile "güvenli" aynı şey
 * değildir.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * İstek thread'leri arasında paylaşılan düz bir {@code HashMap} olarak uygulanan bir cache,
 * yük altında ara sıra {@code ConcurrentModificationException} fırlatır, ya da - daha kötüsü -
 * hiçbir istisna olmadan kayıtları sessizce kaybeder veya bozar, bu da hatayı talep üzerine
 * neredeyse reprodüklenemez hale getirir.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code CollectionsConcurrencyTest.shouldThrowConcurrentModificationExceptionWithPlainHashMap()}
 * bir thread'in iterate etmesini sağlarken diğerlerinin eşzamanlı olarak ekleme yapmasını
 * sağlar ve bir {@code ConcurrentModificationException}'ın (ya da bozuk dahili durumdan
 * kaynaklanan başka bir istisnanın) fırlatıldığını gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * {@link java.util.concurrent.ConcurrentHashMap} kullanın - bkz.
 * {@link com.interviewlab.collectionstopic.good.ConcurrentHashMapCacheService}. Bunun
 * sadece yapısal thread-safety'yi düzelttiğini unutmayın; bileşik operasyonlar hâlâ ekstra
 * özen gerektirir - bkz.
 * {@link com.interviewlab.collectionstopic.bad.CompoundCheckThenActCacheService}.
 */
public class UnsafeHashMapCacheService {

    private final Map<String, String> cache = new HashMap<>();

    public void put(String key, String value) {
        cache.put(key, value);
    }

    public Map<String, String> rawMap() {
        return cache;
    }
}
