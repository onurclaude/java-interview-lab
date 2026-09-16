package com.interviewlab.async.completablefuture;

import com.interviewlab.labrunner.LabRunnerPrint;

import com.interviewlab.async.completablefuture.bad.BlockingGetAggregationService;
import com.interviewlab.async.completablefuture.good.ComposedAggregationService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** IntelliJ'de sağ tık -> Run/Debug. Postman/HTTP GEREKMEZ - Spring de GEREKMEZ (saf POJO). */
public final class CompletableFutureLabRunner {

    private static final long SIMULATED_CALL_MILLIS = 300;

    public static void main(String[] args) throws Exception {
        LabRunnerPrint.banner("COMPLETABLE FUTURE — art arda .get() vs önce başlat sonra birleştir");

        ExecutorService badExecutor = Executors.newFixedThreadPool(4);
        try {
            BlockingGetAggregationService bad = new BlockingGetAggregationService(badExecutor);
            List<String> badThreadNames = new CopyOnWriteArrayList<>();
            Instant start = Instant.now();
            String result = bad.aggregateSequentiallyByBlocking( // <- BREAKPOINT 1
                    () -> slowCall("orders", badThreadNames), () -> slowCall("payments", badThreadNames), () -> slowCall("recommendations", badThreadNames));
            long badMillis = Duration.between(start, Instant.now()).toMillis();
            LabRunnerPrint.fact("SEQUENTIAL result", result);
            LabRunnerPrint.fact("SEQUENTIAL durationMillis (~sum, 900ms)", badMillis);
        } finally {
            badExecutor.shutdown();
        }

        ExecutorService goodExecutor = Executors.newFixedThreadPool(4);
        try {
            ComposedAggregationService good = new ComposedAggregationService(goodExecutor);
            List<String> goodThreadNames = new CopyOnWriteArrayList<>();
            Instant start = Instant.now();
            String result = good.aggregateConcurrently( // <- BREAKPOINT 2: CompletableFuture.allOf() - üçü de ZATEN başladı
                    () -> slowCall("orders", goodThreadNames), () -> slowCall("payments", goodThreadNames), () -> slowCall("recommendations", goodThreadNames)).join();
            long goodMillis = Duration.between(start, Instant.now()).toMillis();
            LabRunnerPrint.fact("PARALLEL result", result);
            LabRunnerPrint.fact("PARALLEL durationMillis (~max, 300ms)", goodMillis);
        } finally {
            goodExecutor.shutdown();
        }

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("supplyAsync() ANINDA döner - art arda .get() çağırmak paralelliği YOK EDER (süre TOPLAM'a");
        LabRunnerPrint.line("yakın); önce ÜÇÜNÜ DE başlatıp sonra allOf() ile beklemek gerçek paralellik sağlar (süre MAKSİMUM'a yakın).");

        LabRunnerPrint.section("TRY");
        LabRunnerPrint.line("BREAKPOINT 2'ye gelmeden ÖNCE 3 supplyAsync çağrısının hepsi ZATEN yapılmış olmalı -");
        LabRunnerPrint.line("Threads panelinde birden fazla worker thread'in AYNI ANDA RUNNABLE olduğunu say.");
    }

    private static String slowCall(String name, List<String> threadNameSink) {
        threadNameSink.add(Thread.currentThread().getName());
        try {
            TimeUnit.MILLISECONDS.sleep(SIMULATED_CALL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return name;
    }
}
