package com.interviewlab.aop;

import com.interviewlab.labrunner.spring.SpringLabRunnerSupport;

import com.interviewlab.aop.ExecutionTimeAspect;
import com.interviewlab.aop.bad.SelfInvocationTimingService;
import com.interviewlab.aop.good.OrderProcessingService;
import com.interviewlab.aop.good.SlowStepService;
import com.interviewlab.labrunner.LabRunnerPrint;
import org.springframework.aop.support.AopUtils;

/**
 * IntelliJ'de sağ tık -> Run/Debug — Postman/HTTP GEREKMEZ. Gerçek Spring context'i
 * ({@code WebApplicationType.NONE}) başlatır, gerçek CGLIB proxy'lerle self-invocation
 * problemini bizzat tetikler. {@code docker compose up -d} ile Postgres ayakta olmalı (bu
 * lab'ın kendisi DB kullanmaz ama uygulama context'i JPA/DataSource bean'lerini başlatır).
 */
public final class AopSelfInvocationSpringLabRunner {

    public static void main(String[] args) {
        SpringLabRunnerSupport.run(ctx -> {
            LabRunnerPrint.banner("AOP SELF-INVOCATION — gerçek Spring context, HTTP YOK");

            SelfInvocationTimingService selfInvocationTimingService = ctx.getBean(SelfInvocationTimingService.class);
            OrderProcessingService orderProcessingService = ctx.getBean(OrderProcessingService.class);
            SlowStepService slowStepService = ctx.getBean(SlowStepService.class);
            ExecutionTimeAspect aspect = ctx.getBean(ExecutionTimeAspect.class);

            LabRunnerPrint.section("PROXY INFO (AopUtils)");
            LabRunnerPrint.fact("selfInvocationTimingService.getClass()", selfInvocationTimingService.getClass().getName());
            LabRunnerPrint.fact("selfInvocationTimingService isCglibProxy", AopUtils.isCglibProxy(selfInvocationTimingService));
            LabRunnerPrint.fact("slowStepService.getClass()", slowStepService.getClass().getName());
            LabRunnerPrint.fact("slowStepService isCglibProxy", AopUtils.isCglibProxy(slowStepService));

            LabRunnerPrint.section("BAD — self-invocation (this.slowStep())");
            aspect.resetInterceptionCount();
            int beforeBad = aspect.interceptionCount();
            selfInvocationTimingService.processOrder(); // <- BREAKPOINT 1: içeride this.slowStep() <- BREAKPOINT 2
            int afterBad = aspect.interceptionCount();
            boolean aspectInterceptedBad = afterBad > beforeBad;
            LabRunnerPrint.fact("interceptionCount before->after", beforeBad + " -> " + afterBad);
            LabRunnerPrint.fact("aspectIntercepted (BAD)", aspectInterceptedBad);

            LabRunnerPrint.section("GOOD — ayrı bean üzerinden gerçek proxy çağrısı");
            int beforeGood = aspect.interceptionCount();
            orderProcessingService.processOrder(); // <- BREAKPOINT 3: burada gerçek proxy'ye giriyor, ExecutionTimeAspect.trackExecutionTime() <- BREAKPOINT 4, sonra SlowStepService.slowStep() <- BREAKPOINT 5
            int afterGood = aspect.interceptionCount();
            boolean aspectInterceptedGood = afterGood > beforeGood;
            LabRunnerPrint.fact("interceptionCount before->after", beforeGood + " -> " + afterGood);
            LabRunnerPrint.fact("aspectIntercepted (GOOD)", aspectInterceptedGood);

            LabRunnerPrint.section("WHY");
            LabRunnerPrint.line("Spring AOP advice proxy sınırında uygulanır. this.slowStep(), JVM seviyesinde");
            LabRunnerPrint.line("sıradan bir metod çağrısıdır - proxy'ye HİÇ uğramaz, bu yüzden ExecutionTimeAspect");
            LabRunnerPrint.line("BAD'de HİÇ çalışmadı (interceptionCount SABİT kaldı). GOOD'da slowStepService alanı");
            LabRunnerPrint.line("GERÇEK bir proxy olduğu için (yukarıdaki isCglibProxy=true) çağrı proxy'den geçti.");

            LabRunnerPrint.section("BREAKPOINT");
            LabRunnerPrint.line("AopSelfInvocationSpringLabRunner.java: selfInvocationTimingService.processOrder() ve");
            LabRunnerPrint.line("orderProcessingService.processOrder() satırlarına koy. Ayrıca ExecutionTimeAspect.trackExecutionTime()'a");
            LabRunnerPrint.line("koy - BAD çağrısında HİÇ TETİKLENMEDİĞİNİ, GOOD çağrısında TETİKLENDİĞİNİ gör.");

            LabRunnerPrint.section("TRY");
            LabRunnerPrint.line("selfInvocationTimingService yerine ctx.getBean(SelfInvocationTimingService.class) ile");
            LabRunnerPrint.line("ALINAN bean'in KENDİSİNE (this.getClass()) bak - CGLIB soneki YOK, çünkü self-invocation");
            LabRunnerPrint.line("hattı zaten proxy'ye hiç girmiyor. slowStepService ile karşılaştır.");
        });
    }
}
