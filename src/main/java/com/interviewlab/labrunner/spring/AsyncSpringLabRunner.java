package com.interviewlab.labrunner.spring;

import com.interviewlab.async.spring.bad.SelfInvocationNotificationService;
import com.interviewlab.async.spring.good.NotificationService;
import com.interviewlab.labrunner.LabRunnerPrint;

/** IntelliJ'de sağ tık -> Run/Debug. Postman/HTTP GEREKMEZ. `docker compose up -d` GEREKİR. */
public final class AsyncSpringLabRunner {

    public static void main(String[] args) {
        SpringLabRunnerSupport.run(ctx -> {
            LabRunnerPrint.banner("SPRING @ASYNC — self-invocation");

            SelfInvocationNotificationService bad = ctx.getBean(SelfInvocationNotificationService.class);
            NotificationService good = ctx.getBean(NotificationService.class);

            String callerThread = bad.notifyUser("hello"); // <- BREAKPOINT 1: içeride this.sendAsync() - SENKRON çalışır
            String badWorkThread = bad.lastAsyncThreadName();
            LabRunnerPrint.fact("BAD callerThread/workRanOnThread (AYNI olmalı)", callerThread + " / " + badWorkThread);

            good.notifyUser("hello"); // <- BREAKPOINT 2: asyncSender.sendAsync() proxy üzerinden, AYRI thread'de çalışır
            sleepQuietly(200); // async işin bitmesini bekle (fire-and-forget)
            com.interviewlab.async.spring.good.AsyncSender asyncSender = ctx.getBean(com.interviewlab.async.spring.good.AsyncSender.class);
            LabRunnerPrint.fact("GOOD callerThread/workRanOnThread (FARKLI olmalı)", callerThread + " / " + asyncSender.lastAsyncThreadName());

            LabRunnerPrint.section("WHY");
            LabRunnerPrint.line("@Async, AOP proxy'sine dayanır - self-invocation onu SESSİZCE senkron yapar.");
            LabRunnerPrint.line("Ayrı bean üzerinden çağrıldığında iş GERÇEKTEN labAsyncExecutor havuzunda,");
            LabRunnerPrint.line("FARKLI bir thread'de çalışır.");
        });
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
