package com.interviewlab.web.lab.threadlocal;

import com.interviewlab.concurrency.threadlocal.bad.LeakyCorrelationIdService;
import com.interviewlab.concurrency.threadlocal.good.CleanCorrelationIdService;
import com.interviewlab.web.lab.LabLog;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/INTERACTIVE_LABS.md — LAB 10. Havuzlanmış (tek thread'li, kesin aynı OS thread'i garanti
 * eden) bir executor'da görev 1'in bıraktığı bir {@code ThreadLocal} değerinin görev 2'ye
 * (temizlenmezse) sızdığını gösterir.
 */
@RestController
@RequestMapping("/api/labs/threadlocal")
public class ThreadLocalLabController {

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "THREAD_LOCAL");
        body.put("action", "RESET");
        body.put("nextStep", "POST /api/labs/threadlocal/bad");
        return body;
    }

    @PostMapping("/bad")
    public Map<String, Object> bad() throws Exception {
        LabLog.banner("THREAD LOCAL", "BAD (remove() yok)");
        LeakyCorrelationIdService service = new LeakyCorrelationIdService();
        ExecutorService singleThreadPool = Executors.newFixedThreadPool(1); // aynı OS thread'ini garanti eder
        try {
            Future<String> task1 = singleThreadPool.submit(() -> {
                service.setCorrelationId("request-A-correlation-id");
                return service.getCorrelationId();
            });
            String task1Value = task1.get();

            Future<String> task2 = singleThreadPool.submit(() -> service.getCorrelationId());
            String task2Value = task2.get(); // task2 HİÇBİR ŞEY set etmedi

            boolean leaked = "request-A-correlation-id".equals(task2Value);
            LabLog.line("Görev 1 correlation-id ayarladı ve remove() çağırmadı. Görev 2 (aynı thread'de, hiçbir şey "
                    + "ayarlamadan) okudu: {}", task2Value);
            LabLog.lesson("ThreadLocal değeri, onu ayarlayan görev değil, Thread nesnesinin kendisi yaşadığı sürece "
                    + "yaşar. Havuzlanmış aynı thread bir sonraki, alakasız görevi işlediğinde, bayat değeri devralır.");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("lab", "THREAD_LOCAL");
            body.put("mode", "BAD");
            body.put("task1SetValue", "request-A-correlation-id");
            body.put("task1ReadBack", task1Value);
            body.put("task2NeverSetAnything", true);
            body.put("task2ReadValue", task2Value);
            body.put("leaked", leaked);
            body.put("problem", "Görev 2, hiçbir şey ayarlamamasına rağmen Görev 1'in correlation-id'sini gördü - bir thread-local sızıntısı.");
            body.put("nextStep", "POST /api/labs/threadlocal/good");
            return body;
        } finally {
            singleThreadPool.shutdown();
        }
    }

    @PostMapping("/good")
    public Map<String, Object> good() throws Exception {
        LabLog.banner("THREAD LOCAL", "GOOD (try/finally + remove())");
        CleanCorrelationIdService service = new CleanCorrelationIdService();
        ExecutorService singleThreadPool = Executors.newFixedThreadPool(1);
        try {
            Future<String> task1 = singleThreadPool.submit(() ->
                    service.runWithCorrelationId("request-A-correlation-id", service::getCorrelationId));
            String task1Value = task1.get();

            Future<String> task2 = singleThreadPool.submit(() -> String.valueOf(service.getCorrelationId()));
            String task2Value = task2.get();

            boolean leaked = "request-A-correlation-id".equals(task2Value);
            LabLog.line("Görev 1, finally bloğunda remove() çağırdı. Görev 2 (aynı thread'de) okudu: {}", task2Value);
            LabLog.lesson("set()'i her zaman try/finally + remove() ile eşleştirmek, havuzlanmış bir thread'in "
                    + "asla bir sonraki göreve bayat state taşımamasını garanti eder.");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("lab", "THREAD_LOCAL");
            body.put("mode", "GOOD");
            body.put("task1SetValue", "request-A-correlation-id");
            body.put("task1ReadBack", task1Value);
            body.put("task2ReadValue", task2Value);
            body.put("leaked", leaked);
            body.put("lesson", "remove() ile Görev 2, 'null' okudu - sızıntı yok.");
            return body;
        } finally {
            singleThreadPool.shutdown();
        }
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "THREAD_LOCAL");
        body.put("note", "Bu lab'ın kalıcı durumu yok - her çağrı kendi tek-thread'li executor'ını oluşturup kapatır.");
        return body;
    }
}
