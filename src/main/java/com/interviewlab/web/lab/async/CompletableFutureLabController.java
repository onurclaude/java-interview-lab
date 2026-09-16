package com.interviewlab.web.lab.async;

import com.interviewlab.async.completablefuture.bad.BlockingGetAggregationService;
import com.interviewlab.async.completablefuture.good.ComposedAggregationService;
import com.interviewlab.web.lab.LabLog;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/INTERACTIVE_LABS.md — LAB 08. Art arda {@code supplyAsync(...).get()} çağırmanın
 * paralellikten hiçbir kazanç sağlamadığını (süre TOPLAM), önce başlatıp sonra birleştirmenin
 * ise gerçekten eşzamanlı çalıştığını (süre MAKSİMUM) gerçek duvar-saati süresiyle gösterir -
 * bkz. docs/completable-future.md.
 */
@RestController
@RequestMapping("/api/labs/completable-future")
public class CompletableFutureLabController {

    private static final long SIMULATED_CALL_MILLIS = 300;

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "COMPLETABLE_FUTURE");
        body.put("action", "RESET");
        body.put("simulatedCallMillisEach", SIMULATED_CALL_MILLIS);
        body.put("nextStep", "POST /api/labs/completable-future/sequential");
        return body;
    }

    @PostMapping("/sequential")
    public Map<String, Object> sequential() throws Exception {
        LabLog.banner("COMPLETABLE FUTURE", "SEQUENTIAL (bad - art arda .get())");
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            BlockingGetAggregationService bad = new BlockingGetAggregationService(executor);
            List<String> threadNames = new CopyOnWriteArrayList<>();
            Instant start = Instant.now();
            String result = bad.aggregateSequentiallyByBlocking(
                    () -> slowCall("orders", threadNames), () -> slowCall("payments", threadNames),
                    () -> slowCall("recommendations", threadNames));
            long elapsedMillis = Duration.between(start, Instant.now()).toMillis();
            LabLog.line("Sonuç: {} - {}ms sürdü (3 x {}ms'nin TOPLAMINA yakın), threadNames={}", result, elapsedMillis, SIMULATED_CALL_MILLIS, threadNames);
            LabLog.lesson("supplyAsync(...).get() art arda çağrıldığında, her görev bir öncekinin tamamen "
                    + "bitmesini bekler - üç bağımsız çağrının paralelliğinden hiçbir kazanç elde edilmez, "
                    + "süre TOPLAM'a yakın çıkar.");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("lab", "COMPLETABLE_FUTURE");
            body.put("mode", "SEQUENTIAL");
            body.put("result", result);
            body.put("durationMillis", elapsedMillis);
            body.put("expectedApprox", "sum (~" + (SIMULATED_CALL_MILLIS * 3) + "ms)");
            body.put("threadNames", threadNames);
            body.put("distinctThreadCount", threadNames.stream().distinct().count());
            body.put("problem", "3 bağımsız supplyAsync().get() art arda çağrıldı - hiçbir örtüşme yok, süre TOPLAM.");
            body.put("nextStep", "POST /api/labs/completable-future/parallel");
            return body;
        } finally {
            executor.shutdown();
        }
    }

    @PostMapping("/parallel")
    public Map<String, Object> parallel() throws Exception {
        LabLog.banner("COMPLETABLE FUTURE", "PARALLEL (good - önce başlat, sonra birleştir)");
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            ComposedAggregationService good = new ComposedAggregationService(executor);
            List<String> threadNames = new CopyOnWriteArrayList<>();
            Instant start = Instant.now();
            String result = good.aggregateConcurrently(
                    () -> slowCall("orders", threadNames), () -> slowCall("payments", threadNames),
                    () -> slowCall("recommendations", threadNames)).join();
            long elapsedMillis = Duration.between(start, Instant.now()).toMillis();
            LabLog.line("Sonuç: {} - {}ms sürdü ({}ms'lik TEK bir çağrının süresine yakın), threadNames={}", result, elapsedMillis, SIMULATED_CALL_MILLIS, threadNames);
            LabLog.lesson("Üç supplyAsync() de bloklanmadan ÖNCE başlatıldı - hepsi eşzamanlı çalıştı, allOf sadece "
                    + "en son biteni bekledi. Süre, üçünün toplamı değil, en yavaşının (MAKSİMUM) süresine yakın.");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("lab", "COMPLETABLE_FUTURE");
            body.put("mode", "PARALLEL");
            body.put("result", result);
            body.put("durationMillis", elapsedMillis);
            body.put("expectedApprox", "max (~" + SIMULATED_CALL_MILLIS + "ms)");
            body.put("threadNames", threadNames);
            body.put("distinctThreadCount", threadNames.stream().distinct().count());
            body.put("lesson", "3 görev de bloklanmadan önce başlatıldı - gerçekten eşzamanlı çalıştılar, süre MAKSİMUM'a yakın.");
            return body;
        } finally {
            executor.shutdown();
        }
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "COMPLETABLE_FUTURE");
        body.put("note", "Bu lab'ın durumu yok - her çağrı bağımsız bir duvar-saati ölçümüdür.");
        return body;
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
