package com.interviewlab.scopes.webscopes;

import com.interviewlab.scopes.prototype.good.SingletonWithObjectProvider;
import com.interviewlab.scopes.prototype.good.SingletonWithScopedProxy;
import com.interviewlab.scopes.singleton.bad.MutableSingletonPriceService;
import com.interviewlab.scopes.singleton.good.StatelessPriceService;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bean-scope lab'ları için manuel/Postman dostu giriş noktaları. Her davranışın güvenilir,
 * assert edilebilir kanıtı {@code BeanScopesTest} içinde bulunur - bu controller, aynı
 * davranışın interaktif olarak dürtülebilmesi için var.
 */
@RestController
public class ScopesController {

    private final RequestScopedIdHolder requestScopedIdHolder;
    private final SessionScopedIdHolder sessionScopedIdHolder;
    private final ApplicationScopedIdHolder applicationScopedIdHolder;
    private final MutableSingletonPriceService mutableSingletonPriceService;
    private final StatelessPriceService statelessPriceService;
    private final SingletonWithObjectProvider singletonWithObjectProvider;
    private final SingletonWithScopedProxy singletonWithScopedProxy;

    public ScopesController(RequestScopedIdHolder requestScopedIdHolder,
                             SessionScopedIdHolder sessionScopedIdHolder,
                             ApplicationScopedIdHolder applicationScopedIdHolder,
                             MutableSingletonPriceService mutableSingletonPriceService,
                             StatelessPriceService statelessPriceService,
                             SingletonWithObjectProvider singletonWithObjectProvider,
                             SingletonWithScopedProxy singletonWithScopedProxy) {
        this.requestScopedIdHolder = requestScopedIdHolder;
        this.sessionScopedIdHolder = sessionScopedIdHolder;
        this.applicationScopedIdHolder = applicationScopedIdHolder;
        this.mutableSingletonPriceService = mutableSingletonPriceService;
        this.statelessPriceService = statelessPriceService;
        this.singletonWithObjectProvider = singletonWithObjectProvider;
        this.singletonWithScopedProxy = singletonWithScopedProxy;
    }

    /** Aynı Postman request çalıştırmasından iki kez çağır: id'ler çağrı başına farklıdır (her seferinde yeni bir HTTP request). */
    @GetMapping("/lab/scopes/request")
    public Map<String, String> requestScope() {
        // Bunu bu tek request/metot İÇİNDE iki kez okumak yine de aynı id'yi döndürür -
        // "request başına bir instance" kısmını kanıtlar - bu endpoint'i iki kez çağırmaktan farklı olarak.
        return Map.of("firstRead", requestScopedIdHolder.getId(), "secondReadSameRequest", requestScopedIdHolder.getId());
    }

    /** Kararlı bir id görmek için çağrılar arasında aynı session'ı (tarayıcı/cookie) yeniden kullan; yeni bir session yenisini alır. */
    @GetMapping("/lab/scopes/session")
    public Map<String, String> sessionScope() {
        return Map.of("sessionScopedId", sessionScopedIdHolder.getId());
    }

    @GetMapping("/lab/scopes/application")
    public Map<String, String> applicationScope() {
        return Map.of("applicationScopedId", applicationScopedIdHolder.getId());
    }

    @PostMapping("/lab/scopes/singleton/race")
    public Map<String, String> singletonRace(@RequestParam BigDecimal basePrice, @RequestParam BigDecimal discountPercentage) {
        BigDecimal bad = mutableSingletonPriceService.calculateDiscountedPrice(basePrice, discountPercentage);
        BigDecimal good = statelessPriceService.calculateDiscountedPrice(basePrice, discountPercentage);
        return Map.of("mutableSingletonResult", bad.toPlainString(), "statelessResult", good.toPlainString());
    }

    @GetMapping("/lab/scopes/prototype")
    public Map<String, String> prototypeScope() {
        return Map.of(
                "objectProviderFirstId", singletonWithObjectProvider.getWorkerId(),
                "objectProviderSecondId", singletonWithObjectProvider.getWorkerId(),
                "scopedProxyFirstId", singletonWithScopedProxy.getWorkerId(),
                "scopedProxySecondId", singletonWithScopedProxy.getWorkerId());
    }
}
