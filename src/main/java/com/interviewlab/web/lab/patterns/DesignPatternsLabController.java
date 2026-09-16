package com.interviewlab.web.lab.patterns;

import com.interviewlab.patterns.adapter.ExternalPaymentProviderAdapter;
import com.interviewlab.patterns.adapter.ExternalPaymentProviderSdk;
import com.interviewlab.patterns.builder.OrderRequest;
import com.interviewlab.patterns.observer.EmailNotificationListener;
import com.interviewlab.patterns.observer.InventoryReservationListener;
import com.interviewlab.patterns.observer.OrderPlacementService;
import com.interviewlab.patterns.proxy.Greeter;
import com.interviewlab.patterns.proxy.GreeterProxyFactory;
import com.interviewlab.patterns.strategy.bad.IfElsePaymentProcessor;
import com.interviewlab.patterns.strategy.good.PaymentProcessor;
import com.interviewlab.web.lab.LabLog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/INTERACTIVE_LABS.md — LAB 15. Strategy+Factory (PaymentStrategyFactory), Adapter
 * (ExternalPaymentProviderAdapter, gerçek bir uyumsuz "üçüncü taraf SDK" sarmalanıyor),
 * Observer (OrderPlacementService -> Spring ApplicationEvent -> InventoryReservationListener +
 * EmailNotificationListener, gerçek event multicast), Builder (OrderRequest, immutable +
 * doğrulanan domain nesnesi) ve Proxy (GreeterProxyFactory, JDK dynamic proxy - AOP proxy-info
 * ile karşılaştırma için bkz. GET /api/labs/aop/proxy-info) - hepsi backend-gerçekçi
 * senaryolar, toy Dog/Cat/Shape örnekleri DEĞİL. Template Method/Decorator/Facade/Singleton/
 * Chain of Responsibility, aynı checkout/payment domain'inde `DesignPatternsTest` ile en iyi
 * gösterilen yapısal kavramlardır.
 */
@RestController
@RequestMapping("/api/labs/patterns")
public class DesignPatternsLabController {

    private final IfElsePaymentProcessor ifElsePaymentProcessor;
    private final PaymentProcessor paymentProcessor;
    private final ExternalPaymentProviderAdapter externalPaymentProviderAdapter;
    private final ExternalPaymentProviderSdk externalPaymentProviderSdk;
    private final OrderPlacementService orderPlacementService;
    private final InventoryReservationListener inventoryReservationListener;
    private final EmailNotificationListener emailNotificationListener;

    public DesignPatternsLabController(IfElsePaymentProcessor ifElsePaymentProcessor,
                                        PaymentProcessor paymentProcessor,
                                        ExternalPaymentProviderAdapter externalPaymentProviderAdapter,
                                        ExternalPaymentProviderSdk externalPaymentProviderSdk,
                                        OrderPlacementService orderPlacementService,
                                        InventoryReservationListener inventoryReservationListener,
                                        EmailNotificationListener emailNotificationListener) {
        this.ifElsePaymentProcessor = ifElsePaymentProcessor;
        this.paymentProcessor = paymentProcessor;
        this.externalPaymentProviderAdapter = externalPaymentProviderAdapter;
        this.externalPaymentProviderSdk = externalPaymentProviderSdk;
        this.orderPlacementService = orderPlacementService;
        this.inventoryReservationListener = inventoryReservationListener;
        this.emailNotificationListener = emailNotificationListener;
    }

    @PostMapping("/strategy/bad")
    public Map<String, Object> strategyBad(@RequestParam String paymentType, @RequestParam(defaultValue = "100.0") double amount) {
        LabLog.banner("DESIGN PATTERNS — STRATEGY", "BAD (if/else zinciri)");
        String result = ifElsePaymentProcessor.process(paymentType, amount);
        LabLog.line("IfElsePaymentProcessor.process({}) -> {}", paymentType, result);
        LabLog.lesson("Yeni bir ödeme türü eklemek bu TEK metodun düzenlenmesini gerektirir - "
                + "open/closed prensibi ihlal edilir, cyclomatic complexity sınırsız büyür.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "DESIGN_PATTERNS_STRATEGY");
        body.put("mode", "BAD_IF_ELSE_CHAIN");
        body.put("paymentType", paymentType);
        body.put("implementationUsed", "IfElsePaymentProcessor (tek metod, if/else dallanma)");
        body.put("result", result);
        body.put("problem", "Yeni bir ödeme türü, bu metodun DEĞİŞTİRİLMESİNİ gerektirir - polimorfizm yok.");
        body.put("nextStep", "POST /api/labs/patterns/strategy/good?paymentType=" + paymentType);
        return body;
    }

    @PostMapping("/strategy/good")
    public Map<String, Object> strategyGood(@RequestParam String paymentType, @RequestParam(defaultValue = "100.0") double amount) {
        LabLog.banner("DESIGN PATTERNS — STRATEGY", "GOOD (polimorfizm + factory)");
        String result = paymentProcessor.process(paymentType, amount);
        String implClass = switch (paymentType) {
            case "CREDIT_CARD" -> "CreditCardPaymentStrategy";
            case "WALLET" -> "WalletPaymentStrategy";
            case "BANK_TRANSFER" -> "BankTransferPaymentStrategy";
            default -> "bilinmiyor";
        };
        LabLog.line("PaymentStrategyFactory.getStrategy({}) -> {}.pay({}) -> {}", paymentType, implClass, amount, result);
        LabLog.lesson("PaymentProcessor hiçbir zaman if/else dallanmıyor - PaymentStrategyFactory, context'teki "
                + "PaymentStrategy bean'lerinden doğru olanı seçiyor. Yeni bir tür eklemek sadece yeni bir "
                + "@Component eklemek demek - mevcut hiçbir sınıf değişmiyor.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "DESIGN_PATTERNS_STRATEGY");
        body.put("mode", "GOOD_STRATEGY_PLUS_FACTORY");
        body.put("paymentType", paymentType);
        body.put("implementationUsed", implClass + " (PaymentStrategyFactory üzerinden seçildi)");
        body.put("result", result);
        body.put("lesson", "Hangi implementasyonun çalıştığı, PaymentStrategyFactory'nin Map lookup'ından geldi - dallanma yok.");
        return body;
    }

    // ---------- Factory: PaymentStrategyFactory - EXTERNAL_PROVIDER de dahil, aynı lookup ----------

    @PostMapping("/factory/resolve")
    public Map<String, Object> factoryResolve(@RequestParam String paymentType, @RequestParam(defaultValue = "100.0") double amount) {
        LabLog.banner("DESIGN PATTERNS — FACTORY", "PaymentStrategyFactory.getStrategy(" + paymentType + ")");
        String result = paymentProcessor.process(paymentType, amount);
        LabLog.lesson("PaymentStrategyFactory, context'teki TÜM PaymentStrategy bean'lerini (Strategy VE Adapter "
                + "implementasyonları dahil) otomatik keşfeder - EXTERNAL_PROVIDER (bir Adapter) ile CREDIT_CARD "
                + "(bir Strategy) AYNI factory lookup'ından geçer, çağıran ikisi arasındaki farkı bilmez.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "DESIGN_PATTERNS_FACTORY");
        body.put("paymentType", paymentType);
        body.put("result", result);
        body.put("lesson", "Factory, yeni bir @Component PaymentStrategy eklendiğinde HİÇBİR DEĞİŞİKLİK gerektirmez.");
        return body;
    }

    // ---------- Adapter: gerçek, uyumsuz bir "üçüncü taraf SDK" tek bir yerde sarmalanıyor ----------

    @PostMapping("/adapter/bad")
    public Map<String, Object> adapterBad(@RequestParam(defaultValue = "100.0") double amount) {
        LabLog.banner("DESIGN PATTERNS — ADAPTER", "BAD (SDK'nın cent/status-kod kontratı ÇAĞIRAN KODA sızıyor)");
        long amountInCents = Math.round(amount * 100); // <- BU DÖNÜŞÜM MANTIĞI HER ÇAĞRI NOKTASINDA TEKRARLANIR
        int statusCode = externalPaymentProviderSdk.submitPayment("lab-merchant-1", amountInCents);
        boolean success = statusCode == 0;
        LabLog.lesson("Çağıran kod, externalSdk'nın cent-tabanlı tutar VE tamsayı status kodu kuralını DOĞRUDAN "
                + "bilmek zorunda kaldı - SDK'yı ödeme alması gereken HER yeni yerde bu dönüşüm KOPYALANIR.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "DESIGN_PATTERNS_ADAPTER");
        body.put("mode", "BAD_DIRECT_SDK_CALL");
        body.put("amountInCents", amountInCents);
        body.put("rawStatusCode", statusCode);
        body.put("success", success);
        body.put("problem", "Çağıran, SDK'nın cent/status-kod kontratını DOĞRUDAN biliyor ve tekrar tekrar dönüştürüyor.");
        body.put("nextStep", "POST /api/labs/patterns/adapter/good?amount=" + amount);
        return body;
    }

    @PostMapping("/adapter/good")
    public Map<String, Object> adapterGood(@RequestParam(defaultValue = "100.0") double amount) {
        LabLog.banner("DESIGN PATTERNS — ADAPTER", "GOOD (ExternalPaymentProviderAdapter, tek çeviri noktası)");
        String result = externalPaymentProviderAdapter.pay(amount); // <- BREAKPOINT: PaymentStrategy kontratı - cent/status kodu YOK
        LabLog.lesson("Çağıran SADECE PaymentStrategy.pay(double)'ı biliyor - cent dönüşümü VE status kodu kontrolü "
                + "ExternalPaymentProviderAdapter İÇİNE, TEK bir yere hapsedildi.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "DESIGN_PATTERNS_ADAPTER");
        body.put("mode", "GOOD_ADAPTER");
        body.put("paymentType", externalPaymentProviderAdapter.paymentType());
        body.put("result", result);
        body.put("lesson", "Adapter, iki uyumsuz arayüz arasındaki çeviriyi TEK bir sınıfa hapsetti.");
        return body;
    }

    // ---------- Observer: gerçek Spring ApplicationEvent multicast, birden fazla bağımsız listener ----------

    @PostMapping("/observer/place-order")
    public Map<String, Object> observerPlaceOrder(@RequestParam String orderId, @RequestParam(defaultValue = "100.0") double amount) {
        LabLog.banner("DESIGN PATTERNS — OBSERVER", "OrderPlacementService.placeOrder() -> Spring ApplicationEvent");
        orderPlacementService.placeOrder(orderId, amount); // <- BREAKPOINT: publishEvent burada - kimin dinlediğini BİLMİYOR
        LabLog.line("reservedOrderIds={}, notifiedOrderIds={}", inventoryReservationListener.reservedOrderIds(), emailNotificationListener.notifiedOrderIds());
        LabLog.lesson("OrderPlacementService (subject), InventoryReservationListener VE EmailNotificationListener'ın "
                + "(iki BAĞIMSIZ observer) var olduğundan bile haberdar değil - Spring'in ApplicationEventPublisher'ı "
                + "@EventListener taşıyan HER bean'e event'i multicast eder.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "DESIGN_PATTERNS_OBSERVER");
        body.put("orderId", orderId);
        body.put("reservedByInventoryListener", inventoryReservationListener.reservedOrderIds().contains(orderId));
        body.put("notifiedByEmailListener", emailNotificationListener.notifiedOrderIds().contains(orderId));
        body.put("lesson", "TEK bir publishEvent() çağrısı, BİRDEN FAZLA bağımsız listener'ı TETİKLEDİ - subject bunların hiçbirini bilmiyor.");
        return body;
    }

    @GetMapping("/observer/state")
    public Map<String, Object> observerState() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "DESIGN_PATTERNS_OBSERVER");
        body.put("reservedOrderIds", inventoryReservationListener.reservedOrderIds());
        body.put("notifiedOrderIds", emailNotificationListener.notifiedOrderIds());
        return body;
    }

    // ---------- Builder: immutable, doğrulanmış domain nesnesi ----------

    @PostMapping("/builder/build")
    public Map<String, Object> builderBuild(@RequestParam String customerId,
                                             @RequestParam List<String> items,
                                             @RequestParam(required = false) String couponCode,
                                             @RequestParam(defaultValue = "false") boolean giftWrap,
                                             @RequestParam(required = false) String deliveryNote) {
        LabLog.banner("DESIGN PATTERNS — BUILDER", "OrderRequest.builder(...).items(...).build()");
        OrderRequest request = OrderRequest.builder(customerId) // <- BREAKPOINT: builder() zincirini adım adım izle
                .items(items)
                .couponCode(couponCode)
                .giftWrap(giftWrap)
                .deliveryNote(deliveryNote)
                .build(); // <- BREAKPOINT: build() burada doğrular VE immutable nesneyi üretir
        LabLog.lesson("OrderRequest'in KENDİSİ hiçbir zaman yarım yapılandırılmış/mutable durumda gözlemlenemez - "
                + "sadece build() BAŞARIYLA dönerse bir instance var olur, ve o andan sonra TÜM alanları immutable'dır.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "DESIGN_PATTERNS_BUILDER");
        body.put("customerId", request.customerId());
        body.put("itemIds", request.itemIds());
        body.put("couponCode", request.couponCode());
        body.put("giftWrap", request.giftWrap());
        body.put("deliveryNote", request.deliveryNote());
        return body;
    }

    @PostMapping("/builder/build-invalid")
    public Map<String, Object> builderBuildInvalid(@RequestParam String customerId) {
        LabLog.banner("DESIGN PATTERNS — BUILDER", "build() doğrulaması — items OLMADAN");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "DESIGN_PATTERNS_BUILDER");
        try {
            OrderRequest.builder(customerId).build(); // <- BREAKPOINT: itemIds.isEmpty() kontrolü burada FIRLATIR
            body.put("built", true);
        } catch (IllegalStateException e) {
            LabLog.lesson("build(), yarım/geçersiz bir OrderRequest'in VAR OLMASINA hiç izin vermedi - hata build() "
                    + "ANINDA fırlatıldı, nesne kullanılmaya başladıktan SONRA değil.");
            body.put("built", false);
            body.put("validationError", e.getMessage());
        }
        return body;
    }

    // ---------- Proxy: JDK dynamic proxy - Spring'in @Transactional/@Async/@Aspect'inin minimal versiyonu ----------

    @PostMapping("/proxy/without-logging")
    public Map<String, Object> proxyWithoutLogging(@RequestParam(defaultValue = "Ada") String name) {
        LabLog.banner("DESIGN PATTERNS — PROXY", "gerçek nesne, proxy YOK");
        Greeter real = target -> "Hello, " + target;
        String result = real.greet(name); // <- BREAKPOINT: hiçbir intercept YOK, doğrudan lambda çalışır

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "DESIGN_PATTERNS_PROXY");
        body.put("mode", "WITHOUT_PROXY");
        body.put("result", result);
        body.put("interceptionLog", List.of());
        return body;
    }

    @PostMapping("/proxy/with-logging")
    public Map<String, Object> proxyWithLogging(@RequestParam(defaultValue = "Ada") String name) {
        LabLog.banner("DESIGN PATTERNS — PROXY", "JDK dynamic proxy - her çağrı intercept edilir");
        List<String> interceptionLog = new ArrayList<>();
        Greeter real = target -> "Hello, " + target;
        Greeter proxied = GreeterProxyFactory.withLogging(real, interceptionLog::add);
        String result = proxied.greet(name); // <- BREAKPOINT: InvocationHandler.invoke() burada devreye girer, SONRA real.greet()
        LabLog.line("interceptionLog={}", interceptionLog);
        LabLog.lesson("proxied.greet(...), gerçek nesneye ULAŞMADAN ÖNCE InvocationHandler.invoke()'a girdi - "
                + "Spring'in @Transactional/@Async/@Aspect'i AYNI mekanizmanın (JDK dynamic proxy ya da CGLIB) "
                + "framework tarafından üretilmiş halidir. Karşılaştırma için: GET /api/labs/aop/proxy-info.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "DESIGN_PATTERNS_PROXY");
        body.put("mode", "WITH_PROXY");
        body.put("result", result);
        body.put("interceptionLog", interceptionLog);
        body.put("lesson", "before:greet ve after:greet, gerçek nesnenin ÇEVRESİNDE proxy tarafından eklendi.");
        body.put("compareWith", "GET /api/labs/aop/proxy-info (gerçek Spring CGLIB proxy'si için)");
        return body;
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "DESIGN_PATTERNS");
        body.put("availablePaymentTypes", java.util.List.of("CREDIT_CARD", "WALLET", "BANK_TRANSFER", "EXTERNAL_PROVIDER"));
        body.put("httpLabs", java.util.List.of("strategy", "factory", "adapter", "observer", "builder", "proxy"));
        body.put("note", "Template Method/Decorator/Facade/Singleton/Chain of Responsibility için bkz. "
                + "DesignPatternsTest + docs/design-patterns.md - aynı checkout/payment domain'inde kod-okuma + "
                + "test ile en iyi gösterilen yapısal kavramlardır.");
        return body;
    }
}
