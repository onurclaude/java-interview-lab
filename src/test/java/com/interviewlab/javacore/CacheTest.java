package com.interviewlab.javacore;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewlab.javacore.cache.CacheAsideProductService;
import com.interviewlab.javacore.cache.LruCache;
import org.junit.jupiter.api.Test;

/** LRU eviction ve Cache-Aside deseni için docs/NOTES_CORRECTIONS.md dosyasına bakın. */
class CacheTest {

    @Test
    void shouldEvictLeastRecentlyUsedEntryWhenCapacityExceeded() {
        LruCache<String, String> cache = new LruCache<>(3);
        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");

        cache.get("a"); // "a"ya eriş - onu en-yeni-kullanılan yap, "b" artık en eski

        cache.put("d", "4"); // kapasite (3) aşıldı - LRU olan "b" atılmalı

        assertThat(cache.containsKey("a")).as("son erişilen 'a' hayatta kalmalı").isTrue();
        assertThat(cache.containsKey("b")).as("en az kullanılan 'b' atılmalı").isFalse();
        assertThat(cache.containsKey("c")).isTrue();
        assertThat(cache.containsKey("d")).isTrue();
        assertThat(cache).hasSize(3);
    }

    @Test
    void shouldOnlyHitDatabaseOnceThenServeFromCache() {
        CacheAsideProductService service = new CacheAsideProductService();
        service.seed(1L, "Widget");

        String first = service.read(1L);
        String second = service.read(1L);
        String third = service.read(1L);

        assertThat(first).isEqualTo("Widget");
        assertThat(second).isEqualTo("Widget");
        assertThat(third).isEqualTo("Widget");
        assertThat(service.databaseReadCount())
                .as("ilk okuma DB'ye gitmeli (cache miss), sonraki ikisi cache'den gelmeli (cache hit)")
                .isEqualTo(1);
    }

    @Test
    void shouldInvalidateCacheOnWriteSoNextReadSeesFreshValue() {
        CacheAsideProductService service = new CacheAsideProductService();
        service.seed(1L, "Widget");
        service.read(1L); // cache'e doldur
        assertThat(service.isCached(1L)).isTrue();

        service.write(1L, "Widget v2");

        assertThat(service.isCached(1L))
                .as("write sonrası cache invalidate edilmeli - stale veri tutmamalı")
                .isFalse();
        assertThat(service.read(1L)).isEqualTo("Widget v2");
        assertThat(service.databaseReadCount())
                .as("invalidate sonrası bir sonraki okuma tekrar DB'ye gitmeli")
                .isEqualTo(2);
    }
}
