package com.interviewlab.labrunner;

import com.interviewlab.patterns.adapter.ExternalPaymentProviderAdapter;
import com.interviewlab.patterns.adapter.ExternalPaymentProviderSdk;
import com.interviewlab.patterns.builder.OrderRequest;
import com.interviewlab.patterns.proxy.Greeter;
import com.interviewlab.patterns.proxy.GreeterProxyFactory;
import java.util.ArrayList;
import java.util.List;

/** IntelliJ'de sağ tık -> Run/Debug. Postman/HTTP GEREKMEZ - Spring de GEREKMEZ (saf POJO). */
public final class DesignPatternsBuilderProxyAdapterLabRunner {

    public static void main(String[] args) {
        builderDemo();
        proxyDemo();
        adapterDemo();
    }

    private static void builderDemo() {
        LabRunnerPrint.banner("DESIGN PATTERN — BUILDER");
        OrderRequest valid = OrderRequest.builder("cust-1") // <- BREAKPOINT 1: builder() zincirini adım adım izle
                .items(List.of("item-a", "item-b"))
                .giftWrap(true)
                .build(); // <- BREAKPOINT 2: burada doğrular VE immutable nesneyi üretir
        LabRunnerPrint.fact("valid.itemIds()", valid.itemIds());
        LabRunnerPrint.fact("valid.giftWrap()", valid.giftWrap());

        boolean built;
        String validationError = null;
        try {
            OrderRequest.builder("cust-1").build(); // <- BREAKPOINT 3: itemIds boş - IllegalStateException FIRLAR
            built = true;
        } catch (IllegalStateException e) {
            built = false;
            validationError = e.getMessage();
        }
        LabRunnerPrint.fact("invalid built", built);
        LabRunnerPrint.fact("invalid validationError", validationError);
    }

    private static void proxyDemo() {
        LabRunnerPrint.banner("DESIGN PATTERN — PROXY (JDK dynamic proxy)");
        Greeter real = name -> "Hello, " + name;
        String withoutProxy = real.greet("Ada"); // <- BREAKPOINT 4: hiçbir intercept YOK
        LabRunnerPrint.fact("withoutProxy result", withoutProxy);

        List<String> interceptionLog = new ArrayList<>();
        Greeter proxied = GreeterProxyFactory.withLogging(real, interceptionLog::add);
        String withProxy = proxied.greet("Ada"); // <- BREAKPOINT 5: InvocationHandler.invoke() burada devreye girer
        LabRunnerPrint.fact("withProxy result", withProxy);
        LabRunnerPrint.fact("interceptionLog", interceptionLog);
    }

    private static void adapterDemo() {
        LabRunnerPrint.banner("DESIGN PATTERN — ADAPTER");
        ExternalPaymentProviderSdk sdk = new ExternalPaymentProviderSdk();

        double amount = 50.0;
        long amountInCents = Math.round(amount * 100); // <- BREAKPOINT 6: BAD - dönüşüm mantığı ÇAĞIRAN KODDA
        int rawStatusCode = sdk.submitPayment("lab-merchant-1", amountInCents);
        LabRunnerPrint.fact("BAD rawStatusCode (sızıyor)", rawStatusCode);

        ExternalPaymentProviderAdapter adapter = new ExternalPaymentProviderAdapter(sdk);
        String result = adapter.pay(amount); // <- BREAKPOINT 7: GOOD - dönüşüm TEK bir yerde, adapter İÇİNDE
        LabRunnerPrint.fact("GOOD result (temiz)", result);

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("Builder: nesne SADECE build() başarıyla dönerse var olur, hiçbir zaman yarım/geçersiz");
        LabRunnerPrint.line("halde gözlemlenemez. Proxy: gerçek nesneye ULAŞMADAN ÖNCE InvocationHandler araya girer -");
        LabRunnerPrint.line("Spring'in @Transactional/@Async/@Aspect'i AYNI mekanizmanın framework versiyonudur.");
        LabRunnerPrint.line("Adapter: iki uyumsuz arayüz arasındaki çeviriyi TEK bir sınıfa hapseder.");
    }
}
