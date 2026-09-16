package com.interviewlab.labrunner.spring;

import com.interviewlab.labrunner.LabRunnerPrint;
import com.interviewlab.scopes.lifecycle.BeanLifecycleDemoBean;
import com.interviewlab.scopes.prototype.bad.SingletonWithDirectPrototypeInjection;
import com.interviewlab.scopes.prototype.good.SingletonWithObjectProvider;
import com.interviewlab.scopes.prototype.good.SingletonWithScopedProxy;
import com.interviewlab.scopes.singleton.bad.MutableSingletonPriceService;
import com.interviewlab.scopes.singleton.good.StatelessPriceService;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IntelliJ'de sağ tık -> Run/Debug. Postman/HTTP GEREKMEZ. `docker compose up -d` GEREKİR.
 *
 * <p><b>Request/Session scope BURADA DEĞİL:</b> bu iki scope, GERÇEK bir HTTP request'in
 * (ServletRequestAttributes) thread'e bağlı olmasını gerektirir - bir main() metodunda
 * bunu sahte bir Mock request OLMADAN simüle etmenin dürüst bir yolu yoktur (ve
 * `spring-test`'in {@code MockHttpServletRequest}'i bilinçli olarak SADECE test scope'unda,
 * production kodda DEĞİL). Bu ikisi için gerçek Postman/HTTP + gerçek Cookie Jar KULLANMAK
 * ZORUNLUDUR - bkz. `docs/DEBUGGER_LABS.md` #12-13, bu SAHTE bir kısıtlama değil, Spring web
 * scope'larının kendi doğasıdır.
 */
public final class BeanScopesSpringLabRunner {

    public static void main(String[] args) throws Exception {
        SpringLabRunnerSupport.run(ctx -> {
            try {
                run(ctx);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void run(org.springframework.context.ConfigurableApplicationContext ctx) throws Exception {
        LabRunnerPrint.banner("BEAN SCOPES — singleton/prototype/lifecycle (request/session Postman gerektirir)");

        LabRunnerPrint.section("BEAN LIFECYCLE (uygulama BAŞLARKEN zaten gerçekleşti)");
        LabRunnerPrint.fact("observedEventsInOrder", BeanLifecycleDemoBean.events()); // <- BREAKPOINT 0: BeanLifecycleDemoBean constructor'ına/init()'ine koy, UYGULAMA BAŞLAMADAN ÖNCE

        MutableSingletonPriceService mutableSingletonPriceService = ctx.getBean(MutableSingletonPriceService.class);
        StatelessPriceService statelessPriceService = ctx.getBean(StatelessPriceService.class);
        LabRunnerPrint.section("SINGLETON — mutable field race");
        LabRunnerPrint.fact("BAD callsThatGotWrongResult (30 eşzamanlı)", runConcurrentPriceCalls(mutableSingletonPriceService::calculateDiscountedPrice)); // <- BREAKPOINT 1
        LabRunnerPrint.fact("GOOD callsThatGotWrongResult (30 eşzamanlı)", runConcurrentPriceCalls(statelessPriceService::calculateDiscountedPrice)); // <- BREAKPOINT 2

        SingletonWithDirectPrototypeInjection directInjection = ctx.getBean(SingletonWithDirectPrototypeInjection.class);
        SingletonWithObjectProvider objectProvider = ctx.getBean(SingletonWithObjectProvider.class);
        SingletonWithScopedProxy scopedProxy = ctx.getBean(SingletonWithScopedProxy.class);
        LabRunnerPrint.section("PROTOTYPE — doğrudan injection vs ObjectProvider/ScopedProxy");
        String firstId = directInjection.getWorkerId(); // <- BREAKPOINT 3
        String secondId = directInjection.getWorkerId();
        LabRunnerPrint.fact("BAD sameInstanceBothTimes", firstId.equals(secondId));
        LabRunnerPrint.fact("GOOD objectProvider farklı id'ler", !objectProvider.getWorkerId().equals(objectProvider.getWorkerId())); // <- BREAKPOINT 4
        LabRunnerPrint.fact("GOOD scopedProxy farklı id'ler", !scopedProxy.getWorkerId().equals(scopedProxy.getWorkerId()));

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("@Service varsayılan olarak singleton'dur - instance alanına yazmak eşzamanlı isteklerde");
        LabRunnerPrint.line("data race yaratır. Prototype bean, singleton'a normal injection ile SADECE BİR KEZ");
        LabRunnerPrint.line("alınır; ObjectProvider/ScopedProxy her erişimde GERÇEKTEN yeni instance ister.");
    }

    private static int runConcurrentPriceCalls(java.util.function.BiFunction<BigDecimal, BigDecimal, BigDecimal> calc) throws InterruptedException {
        int concurrentCalls = 30;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentCalls);
        try {
            AtomicInteger corrupted = new AtomicInteger();
            List<Future<Boolean>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < concurrentCalls; i++) {
                BigDecimal basePrice = BigDecimal.valueOf(100 + i);
                futures.add(executor.submit(() -> {
                    BigDecimal result = calc.apply(basePrice, new BigDecimal("0.10"));
                    BigDecimal expected = basePrice.subtract(basePrice.multiply(new BigDecimal("0.10")));
                    return result.compareTo(expected) != 0;
                }));
            }
            for (Future<Boolean> f : futures) {
                if (f.get(5, TimeUnit.SECONDS)) {
                    corrupted.incrementAndGet();
                }
            }
            return corrupted.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            executor.shutdown();
        }
    }
}
