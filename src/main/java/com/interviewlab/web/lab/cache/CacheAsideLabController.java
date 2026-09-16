package com.interviewlab.web.lab.cache;

import com.interviewlab.javacore.cache.CacheAsideProductService;
import com.interviewlab.javacore.cache.NoCacheProductService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PRIMARY interactive surface — bkz. docs/DEBUGGER_LABS.md "CACHE-ASIDE" bölümü.
 * {@code CacheTest}, AYNI davranışın otomatik regresyon KANITIdır - birincil öğrenme arayüzü
 * BU controller'dır, test değil. {@code databaseReadCount}, GERÇEK bir {@code AtomicInteger}
 * ile ölçülür (sahte/sabit değil) - BAD'de HER read() çağrısında artar, GOOD'da SADECE cache
 * miss'te artar.
 */
@RestController
@RequestMapping("/api/labs/cache")
public class CacheAsideLabController {

    private static final Long PRODUCT_ID = 1L;

    private final NoCacheProductService noCacheProductService = new NoCacheProductService();
    private final CacheAsideProductService cacheAsideProductService = new CacheAsideProductService();

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        noCacheProductService.seed(PRODUCT_ID, "Widget");
        cacheAsideProductService.seed(PRODUCT_ID, "Widget");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "CACHE_ASIDE");
        body.put("action", "RESET");
        body.put("productId", PRODUCT_ID);
        body.put("breakpointHint", "CacheAsideProductService.read() içine breakpoint koy - cache.get(id) "
                + "null dönerse (miss) DB'ye gidildiğini, ikinci çağrıda cache.get(id)'in dolu döndüğünü izle.");
        body.put("nextStep", "GET /api/labs/cache/bad/read?id=" + PRODUCT_ID + " (birkaç kez çağır)");
        return body;
    }

    @GetMapping("/bad/read")
    public Map<String, Object> badRead(@RequestParam(defaultValue = "1") Long id) {
        String value = noCacheProductService.read(id); // <- BREAKPOINT: cache YOK - HER çağrıda buraya girilir
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "CACHE_ASIDE");
        body.put("implementation", "BAD_NO_CACHE");
        body.put("value", value);
        body.put("databaseReadCount", noCacheProductService.databaseReadCount());
        body.put("lesson", "Her read() çağrısı DB'ye gitti - databaseReadCount, çağrı sayısıyla BİREBİR artıyor.");
        return body;
    }

    @GetMapping("/good/read")
    public Map<String, Object> goodRead(@RequestParam(defaultValue = "1") Long id) {
        boolean wasCachedBefore = cacheAsideProductService.isCached(id);
        String value = cacheAsideProductService.read(id); // <- BREAKPOINT: cache.get(id) burada - miss ise DB'ye iner
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "CACHE_ASIDE");
        body.put("implementation", "GOOD_CACHE_ASIDE");
        body.put("value", value);
        body.put("wasCacheHit", wasCachedBefore);
        body.put("databaseReadCount", cacheAsideProductService.databaseReadCount());
        body.put("isCachedNow", cacheAsideProductService.isCached(id));
        body.put("lesson", wasCachedBefore
                ? "Cache HIT - DB'ye HİÇ gidilmedi, databaseReadCount DEĞİŞMEDİ."
                : "Cache MISS - DB'ye gidildi VE sonuç cache'e yazıldı, SONRAKİ okuma artık hit olacak.");
        return body;
    }

    @PostMapping("/good/write")
    public Map<String, Object> goodWrite(@RequestParam(defaultValue = "1") Long id, @RequestParam String name) {
        cacheAsideProductService.write(id, name); // <- BREAKPOINT: DB'ye yaz, SONRA cache.remove(id) (invalidate)
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "CACHE_ASIDE");
        body.put("action", "WRITE_THEN_INVALIDATE");
        body.put("isCachedImmediatelyAfterWrite", cacheAsideProductService.isCached(id));
        body.put("lesson", "write(), cache'i GÜNCELLEMEK yerine SİLDİ (invalidate) - bir sonraki read() DB'den "
                + "taze veriyi çekip cache'i yeniden dolduracak.");
        return body;
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "CACHE_ASIDE");
        body.put("bad", Map.of("databaseReadCount", noCacheProductService.databaseReadCount()));
        body.put("good", Map.of(
                "databaseReadCount", cacheAsideProductService.databaseReadCount(),
                "isCached", cacheAsideProductService.isCached(PRODUCT_ID)));
        return body;
    }
}
