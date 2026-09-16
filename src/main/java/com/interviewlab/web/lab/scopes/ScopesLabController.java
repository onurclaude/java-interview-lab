package com.interviewlab.web.lab.scopes;

import com.interviewlab.scopes.lifecycle.BeanLifecycleDemoBean;
import com.interviewlab.scopes.prototype.bad.SingletonWithDirectPrototypeInjection;
import com.interviewlab.scopes.prototype.good.SingletonWithObjectProvider;
import com.interviewlab.scopes.prototype.good.SingletonWithScopedProxy;
import com.interviewlab.scopes.singleton.bad.MutableSingletonPriceService;
import com.interviewlab.scopes.singleton.good.StatelessPriceService;
import com.interviewlab.web.lab.LabLog;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/INTERACTIVE_LABS.md — LAB 11. Bean scope'ları (singleton/prototype) ve bean lifecycle
 * sırasını gerçek eşzamanlı çağrılar ve gerçek bean instance kimlikleriyle gösterir - bkz.
 * docs/bean-scopes.md.
 */
@RestController
@RequestMapping("/api/labs/scopes")
public class ScopesLabController {

    private final MutableSingletonPriceService mutableSingletonPriceService;
    private final StatelessPriceService statelessPriceService;
    private final SingletonWithDirectPrototypeInjection singletonWithDirectPrototypeInjection;
    private final SingletonWithObjectProvider singletonWithObjectProvider;
    private final SingletonWithScopedProxy singletonWithScopedProxy;

    public ScopesLabController(MutableSingletonPriceService mutableSingletonPriceService,
                                StatelessPriceService statelessPriceService,
                                SingletonWithDirectPrototypeInjection singletonWithDirectPrototypeInjection,
                                SingletonWithObjectProvider singletonWithObjectProvider,
                                SingletonWithScopedProxy singletonWithScopedProxy) {
        this.mutableSingletonPriceService = mutableSingletonPriceService;
        this.statelessPriceService = statelessPriceService;
        this.singletonWithDirectPrototypeInjection = singletonWithDirectPrototypeInjection;
        this.singletonWithObjectProvider = singletonWithObjectProvider;
        this.singletonWithScopedProxy = singletonWithScopedProxy;
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "BEAN_SCOPES");
        body.put("action", "RESET");
        body.put("beanLifecycleEventsSoFar", BeanLifecycleDemoBean.events());
        body.put("nextStep", "POST /api/labs/scopes/singleton/bad");
        return body;
    }

    @PostMapping("/singleton/bad")
    public Map<String, Object> singletonBad() throws Exception {
        LabLog.banner("BEAN SCOPES", "SINGLETON — BAD (mutable instance field)");
        int concurrentCalls = 30;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentCalls);
        try {
            AtomicInteger corrupted = new AtomicInteger();
            List<Future<Boolean>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < concurrentCalls; i++) {
                BigDecimal basePrice = BigDecimal.valueOf(100 + i);
                futures.add(executor.submit(() -> {
                    BigDecimal result = mutableSingletonPriceService.calculateDiscountedPrice(basePrice, new BigDecimal("0.10"));
                    BigDecimal expected = basePrice.subtract(basePrice.multiply(new BigDecimal("0.10")));
                    return result.compareTo(expected) != 0;
                }));
            }
            for (Future<Boolean> f : futures) {
                if (f.get(5, TimeUnit.SECONDS)) {
                    corrupted.incrementAndGet();
                }
            }
            LabLog.line("{} eşzamanlı çağrıdan {} tanesi, KENDİ base price'ına karşılık gelmeyen bir sonuç aldı.",
                    concurrentCalls, corrupted.get());
            LabLog.lesson("@Service varsayılan olarak bir singletondur - tüm eşzamanlı request'ler AYNI instance'ı "
                    + "paylaşır. Bir instance alanına çağrı-başına veri yazmak, sıradan istek işlemeyi bir data "
                    + "race'e çevirir.");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("lab", "BEAN_SCOPES");
            body.put("mode", "SINGLETON_BAD");
            body.put("concurrentCalls", concurrentCalls);
            body.put("callsThatGotWrongResult", corrupted.get());
            body.put("dataRaceObserved", corrupted.get() > 0);
            body.put("problem", "Paylaşılan mutable instance alanı, eşzamanlı çağrılar arasında sonuçları karıştırdı.");
            body.put("nextStep", "POST /api/labs/scopes/singleton/good");
            return body;
        } finally {
            executor.shutdown();
        }
    }

    @PostMapping("/singleton/good")
    public Map<String, Object> singletonGood() throws Exception {
        LabLog.banner("BEAN SCOPES", "SINGLETON — GOOD (yerel değişkenler)");
        int concurrentCalls = 30;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentCalls);
        try {
            AtomicInteger corrupted = new AtomicInteger();
            List<Future<Boolean>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < concurrentCalls; i++) {
                BigDecimal basePrice = BigDecimal.valueOf(100 + i);
                futures.add(executor.submit(() -> {
                    BigDecimal result = statelessPriceService.calculateDiscountedPrice(basePrice, new BigDecimal("0.10"));
                    BigDecimal expected = basePrice.subtract(basePrice.multiply(new BigDecimal("0.10")));
                    return result.compareTo(expected) != 0;
                }));
            }
            for (Future<Boolean> f : futures) {
                if (f.get(5, TimeUnit.SECONDS)) {
                    corrupted.incrementAndGet();
                }
            }
            LabLog.lesson("Aynı singleton, ama tüm state yerel değişkenlerde/parametrelerde yaşıyor - her thread'in "
                    + "kendi stack'i var, paylaşılan hiçbir şey yok. Hiçbir çağrı bir diğerinin sonucunu görmedi.");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("lab", "BEAN_SCOPES");
            body.put("mode", "SINGLETON_GOOD");
            body.put("concurrentCalls", concurrentCalls);
            body.put("callsThatGotWrongResult", corrupted.get());
            body.put("dataRaceObserved", corrupted.get() > 0);
            body.put("lesson", "Aynı singleton bean, ama state stack'te (yerel değişkenlerde) yaşadığı için race yok.");
            return body;
        } finally {
            executor.shutdown();
        }
    }

    @PostMapping("/prototype/bad")
    public Map<String, Object> prototypeBad() {
        LabLog.banner("BEAN SCOPES", "PROTOTYPE — BAD (singleton'a doğrudan inject edilmiş)");
        String firstId = singletonWithDirectPrototypeInjection.getWorkerId();
        String secondId = singletonWithDirectPrototypeInjection.getWorkerId();
        LabLog.lesson("Prototype worker, singleton'ın constructor'ına SADECE BİR KEZ inject edildi - o andan "
                + "sonra sıradan bir Java alan referansı, sonsuza dek AYNI instance'a işaret ediyor.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "BEAN_SCOPES");
        body.put("mode", "PROTOTYPE_BAD_DIRECT_INJECTION");
        body.put("firstInstanceId", firstId);
        body.put("secondInstanceId", secondId);
        body.put("sameInstanceBothTimes", firstId.equals(secondId));
        body.put("problem", "Prototype bean, singleton'a normal constructor injection ile inject edildi - tek seferlik injection, taze instance'lar ELDE ETMEZ.");
        body.put("nextStep", "POST /api/labs/scopes/prototype/good");
        return body;
    }

    @PostMapping("/prototype/good")
    public Map<String, Object> prototypeGood() {
        LabLog.banner("BEAN SCOPES", "PROTOTYPE — GOOD (ObjectProvider ve scoped proxy)");
        String objectProviderFirst = singletonWithObjectProvider.getWorkerId();
        String objectProviderSecond = singletonWithObjectProvider.getWorkerId();
        String scopedProxyFirst = singletonWithScopedProxy.getWorkerId();
        String scopedProxySecond = singletonWithScopedProxy.getWorkerId();
        LabLog.lesson("ObjectProvider#getObject() ve scoped proxy'nin her metod çağrısı, container'dan GERÇEKTEN "
                + "taze bir prototype instance ister - iki yaklaşım da her çağrıda farklı bir id üretir.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "BEAN_SCOPES");
        body.put("mode", "PROTOTYPE_GOOD");
        body.put("objectProviderFirstId", objectProviderFirst);
        body.put("objectProviderSecondId", objectProviderSecond);
        body.put("objectProviderProducedDifferentInstances", !objectProviderFirst.equals(objectProviderSecond));
        body.put("scopedProxyFirstId", scopedProxyFirst);
        body.put("scopedProxySecondId", scopedProxySecond);
        body.put("scopedProxyProducedDifferentInstances", !scopedProxyFirst.equals(scopedProxySecond));
        body.put("lesson", "Her iki teknik de her çağrıda gerçekten taze bir prototype instance üretti.");
        return body;
    }

    @GetMapping("/lifecycle")
    public Map<String, Object> lifecycle() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "BEAN_SCOPES");
        body.put("mode", "BEAN_LIFECYCLE");
        body.put("observedEventsInOrder", BeanLifecycleDemoBean.events());
        body.put("explanation", Map.of(
                "1_CONSTRUCTOR", "Constructor (+ constructor injection) her zaman ilk çalışır",
                "2_BEAN_POST_PROCESSOR_BEFORE_INIT", "BeanPostProcessor.postProcessBeforeInitialization",
                "3_POST_CONSTRUCT", "@PostConstruct - container'ın CommonAnnotationBeanPostProcessor'ı tarafından çağrılır",
                "3_5_BEAN_POST_PROCESSOR_AFTER_INIT", "BeanPostProcessor.postProcessAfterInitialization",
                "4_PRE_DESTROY", "@PreDestroy - yalnızca uygulama kapanırken (konsol logunda görülür, bu response'ta değil)"));
        return body;
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "BEAN_SCOPES");
        body.put("beanLifecycleEvents", BeanLifecycleDemoBean.events());
        body.put("otherEndpoints", Map.of(
                "requestScope", "GET /lab/scopes/request",
                "sessionScope", "GET /lab/scopes/session",
                "applicationScope", "GET /lab/scopes/application"));
        return body;
    }
}
