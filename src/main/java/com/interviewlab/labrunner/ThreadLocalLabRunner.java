package com.interviewlab.labrunner;

import com.interviewlab.concurrency.threadlocal.bad.LeakyCorrelationIdService;
import com.interviewlab.concurrency.threadlocal.good.CleanCorrelationIdService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** IntelliJ'de sağ tık -> Run/Debug. Postman/HTTP GEREKMEZ - Spring de GEREKMEZ (saf POJO). */
public final class ThreadLocalLabRunner {

    public static void main(String[] args) throws Exception {
        LabRunnerPrint.banner("THREADLOCAL — havuzlanmış thread'e sızıntı");

        LeakyCorrelationIdService bad = new LeakyCorrelationIdService();
        ExecutorService singleThreadPoolBad = Executors.newFixedThreadPool(1);
        try {
            Future<String> task1 = singleThreadPoolBad.submit(() -> {
                bad.setCorrelationId("request-A-correlation-id"); // <- BREAKPOINT 1
                return bad.getCorrelationId();
            });
            task1.get();
            Future<String> task2 = singleThreadPoolBad.submit(bad::getCorrelationId); // <- BREAKPOINT 2: HİÇ set() ÇAĞRILMADI
            String task2Value = task2.get();
            LabRunnerPrint.fact("BAD task2ReadValue (sızmış olmalı)", task2Value);
            LabRunnerPrint.fact("BAD leaked", "request-A-correlation-id".equals(task2Value));
        } finally {
            singleThreadPoolBad.shutdown();
        }

        CleanCorrelationIdService good = new CleanCorrelationIdService();
        ExecutorService singleThreadPoolGood = Executors.newFixedThreadPool(1);
        try {
            Future<String> task1 = singleThreadPoolGood.submit(() ->
                    good.runWithCorrelationId("request-A-correlation-id", good::getCorrelationId)); // <- BREAKPOINT 3: finally'de remove()
            task1.get();
            Future<String> task2 = singleThreadPoolGood.submit(() -> String.valueOf(good.getCorrelationId()));
            String task2Value = task2.get();
            LabRunnerPrint.fact("GOOD task2ReadValue (temiz olmalı)", task2Value);
            LabRunnerPrint.fact("GOOD leaked", "request-A-correlation-id".equals(task2Value));
        } finally {
            singleThreadPoolGood.shutdown();
        }

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("ThreadLocal değeri, onu ayarlayan GÖREV değil, Thread NESNESİNİN KENDİSİ yaşadığı sürece");
        LabRunnerPrint.line("yaşar. Havuzlanmış (tek thread'li executor GARANTİ eder) aynı thread bir sonraki,");
        LabRunnerPrint.line("alakasız görevi işlediğinde bayat değeri devralır - remove() bunu önler.");

        LabRunnerPrint.section("TRY");
        LabRunnerPrint.line("CleanCorrelationIdService.runWithCorrelationId() içindeki finally bloğuna breakpoint koy,");
        LabRunnerPrint.line("remove() çağrısını KALDIRIP tekrar çalıştır - GOOD'un da sızdırdığını gör.");
    }
}
