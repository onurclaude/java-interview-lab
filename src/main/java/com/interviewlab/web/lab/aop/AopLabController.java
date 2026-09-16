package com.interviewlab.web.lab.aop;

import com.interviewlab.aop.ExecutionTimeAspect;
import com.interviewlab.aop.bad.SelfInvocationTimingService;
import com.interviewlab.aop.good.OrderProcessingService;
import com.interviewlab.aop.good.SlowStepService;
import com.interviewlab.web.lab.LabLog;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.aop.support.AopUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PRIMARY interactive surface for this topic — bkz. docs/DEBUGGER_LABS.md "AOP SELF
 * INVOCATION" bölümü breakpoint sırası için. `AopSelfInvocationTest`, AYNI davranışın
 * otomatik regresyon KANITIdır — birincil öğrenme arayüzü BU controller'dır, test değil.
 *
 * <p>{@code aspectIntercepted}, HER İKİ senaryoda da gerçek {@link ExecutionTimeAspect}'in
 * çalışıp çalışmadığına bakılarak hesaplanır (sabit/uydurma bir değer DEĞİLDİR) - BAD
 * senaryoda aspect'in kayıt defterinde self-invocation çağrısı için HİÇBİR ZAMAN bir kayıt
 * oluşmaz, bu yüzden {@code aspectIntercepted} GERÇEKTEN false çıkar.
 */
@RestController
@RequestMapping("/api/labs/aop")
public class AopLabController {

    private static final String SELF_INVOCATION_SIGNATURE = "SelfInvocationTimingService.slowStep()";
    private static final String REAL_PROXY_SIGNATURE = "SlowStepService.slowStep()";

    private final SelfInvocationTimingService selfInvocationTimingService;
    private final OrderProcessingService orderProcessingService;
    private final SlowStepService slowStepService;
    private final ExecutionTimeAspect executionTimeAspect;

    public AopLabController(SelfInvocationTimingService selfInvocationTimingService,
                             OrderProcessingService orderProcessingService,
                             SlowStepService slowStepService,
                             ExecutionTimeAspect executionTimeAspect) {
        this.selfInvocationTimingService = selfInvocationTimingService;
        this.orderProcessingService = orderProcessingService;
        this.slowStepService = slowStepService;
        this.executionTimeAspect = executionTimeAspect;
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        executionTimeAspect.resetInterceptionCount(); // <- GERÇEK sayaç sıfırlanıyor, controller'da değil, aspect'in KENDİSİNDE
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "SPRING_AOP");
        body.put("action", "RESET");
        body.put("interceptionCount", executionTimeAspect.interceptionCount());
        body.put("breakpointHint", "IntelliJ'de SelfInvocationTimingService.processOrder(), "
                + ".slowStep() ve ExecutionTimeAspect.trackExecutionTime()'a breakpoint koy - bkz. docs/DEBUGGER_LABS.md");
        body.put("nextStep", "POST /api/labs/aop/self-invocation/bad");
        return body;
    }

    @PostMapping("/self-invocation/bad")
    public Map<String, Object> selfInvocationBad() {
        LabLog.banner("SPRING AOP", "SELF_INVOCATION — BAD (this.slowStep())");
        int interceptionCountBefore = executionTimeAspect.interceptionCount(); // <- GERÇEK sayaç, ExecutionTimeAspect'in KENDİSİNDEN okunuyor

        selfInvocationTimingService.processOrder(); // <- BREAKPOINT 1 burada; içeride .slowStep() <- BREAKPOINT 2

        int interceptionCountAfter = executionTimeAspect.interceptionCount();
        // aspectIntercepted, controller'ın "BAD olduğu için false olmalı" diye BİLDİĞİ bir
        // değer DEĞİLDİR - ExecutionTimeAspect.trackExecutionTime() GERÇEKTEN çalıştıysa
        // interceptionCount GERÇEKTEN artar; self-invocation'da advice'a HİÇ UĞRANMADIĞI
        // için sayaç GERÇEKTEN sabit kalır (delta=0).
        boolean aspectIntercepted = interceptionCountAfter > interceptionCountBefore;
        LabLog.line("this.slowStep() çağrıldı - interceptionCount: önce={}, sonra={} -> aspectIntercepted={}",
                interceptionCountBefore, interceptionCountAfter, aspectIntercepted);
        LabLog.lesson("@Around advice proxy tabanlıdır - this.slowStep() JVM seviyesinde sıradan bir metod "
                + "çağrısıdır, proxy'ye hiç uğramaz. ExecutionTimeAspect.trackExecutionTime() breakpoint'i HİÇ TETİKLENMEDİ.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scenario", "AOP_SELF_INVOCATION");
        body.put("implementation", "BAD");
        body.put("businessMethodExecuted", true);
        body.put("aspectIntercepted", aspectIntercepted);
        body.put("interceptionCountBefore", interceptionCountBefore);
        body.put("interceptionCountAfter", interceptionCountAfter);
        body.put("problem", "Method was invoked through this, bypassing Spring proxy");
        body.put("lesson", "Spring AOP advice is applied at proxy boundaries");
        body.put("expectedCallStack", "Controller -> SelfInvocationTimingService.processOrder() -> this.slowStep() -> slowStep() [ExecutionTimeAspect.trackExecutionTime() NOT hit]");
        body.put("nextStep", "POST /api/labs/aop/self-invocation/good");
        return body;
    }

    @PostMapping("/self-invocation/good")
    public Map<String, Object> selfInvocationGood() {
        LabLog.banner("SPRING AOP", "SELF_INVOCATION — GOOD (ayrı bean üzerinden gerçek proxy çağrısı)");
        int interceptionCountBefore = executionTimeAspect.interceptionCount();

        orderProcessingService.processOrder(); // <- BREAKPOINT: burada Spring proxy'ye giriyor, sonra ExecutionTimeAspect.trackExecutionTime() <- BREAKPOINT, sonra SlowStepService.slowStep() <- BREAKPOINT

        int interceptionCountAfter = executionTimeAspect.interceptionCount();
        boolean aspectIntercepted = interceptionCountAfter > interceptionCountBefore;
        Long executionTimeMs = executionTimeAspect.lastDurationMillis(REAL_PROXY_SIGNATURE);
        LabLog.line("orderProcessingService.processOrder() -> slowStepService.slowStep() (proxy üzerinden) - "
                + "interceptionCount: önce={}, sonra={} -> aspectIntercepted={}, kayıtlı süre={}ms",
                interceptionCountBefore, interceptionCountAfter, aspectIntercepted, executionTimeMs);
        LabLog.lesson("slowStepService.slowStep(), ayrı bir bean üzerinden çağrıldığı için gerçek proxy'den geçti - "
                + "ExecutionTimeAspect.trackExecutionTime() breakpoint'i GERÇEKTEN tetiklendi.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scenario", "AOP_SELF_INVOCATION");
        body.put("implementation", "GOOD");
        body.put("businessMethodExecuted", true);
        body.put("aspectIntercepted", aspectIntercepted);
        body.put("interceptionCountBefore", interceptionCountBefore);
        body.put("interceptionCountAfter", interceptionCountAfter);
        body.put("executionTimeMs", executionTimeMs);
        body.put("lesson", "Invocation crossed the Spring proxy boundary");
        body.put("expectedCallStack", "Controller -> OrderProcessingService.processOrder() -> SlowStepService proxy -> ExecutionTimeAspect.trackExecutionTime() -> SlowStepService.slowStep() (real target)");
        return body;
    }

    /**
     * {@code AopUtils} ile GERÇEK proxy türünü göster - bkz. debugger lab: bu endpoint'e
     * breakpoint koyup {@code selfInvocationTimingService}/{@code slowStepService}
     * değişkenlerinin {@code getClass()} çıktısını Variables panelinde incele.
     */
    @GetMapping("/proxy-info")
    public Map<String, Object> proxyInfo() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "SPRING_AOP");
        body.put("selfInvocationTimingService", describeProxy(selfInvocationTimingService));
        body.put("slowStepService", describeProxy(slowStepService));
        body.put("lesson", "SlowStepService bir CGLIB proxy'dir (isAopProxy=true, isCglibProxy=true) - "
                + "ExecutionTimeAspect bunu SARMALAR. SelfInvocationTimingService de proxy'lenmiştir (bu "
                + "sınıf da @TrackExecutionTime taşıyan bir metod içerir), ama this.slowStep() o proxy'ye HİÇ UĞRAMAZ.");
        return body;
    }

    private static Map<String, Object> describeProxy(Object bean) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("runtimeClass", bean.getClass().getName());
        info.put("isAopProxy", AopUtils.isAopProxy(bean));
        info.put("isJdkDynamicProxy", AopUtils.isJdkDynamicProxy(bean));
        info.put("isCglibProxy", AopUtils.isCglibProxy(bean));
        return info;
    }

    @GetMapping("/self-invocation/state")
    public Map<String, Object> state() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "SPRING_AOP");
        body.put("selfInvocationLastRecordedDuration", executionTimeAspect.lastDurationMillis(SELF_INVOCATION_SIGNATURE));
        body.put("realProxyLastRecordedDuration", executionTimeAspect.lastDurationMillis(REAL_PROXY_SIGNATURE));
        body.put("interceptionCount", executionTimeAspect.interceptionCount());
        return body;
    }
}
