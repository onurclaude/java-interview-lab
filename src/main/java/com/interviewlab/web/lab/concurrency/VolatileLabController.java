package com.interviewlab.web.lab.concurrency;

import com.interviewlab.concurrency.volatiletopic.bad.VolatileCounter;
import com.interviewlab.concurrency.volatiletopic.good.ShutdownFlagWorker;
import com.interviewlab.web.lab.LabLog;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PRIMARY interactive surface — volatile'ın NE sağladığını (visibility) ve NE
 * SAĞLAMADIĞINI (atomicity) iki AYRI, gerçek deneyle gösterir. "volatile eklersem thread-safe
 * olur" varsayımının neden yanlış olduğunu {@code /misconception-check} kanıtlar.
 */
@RestController
@RequestMapping("/api/labs/concurrency/volatile")
public class VolatileLabController {

    /**
     * YAYGIN YANLIŞ VARSAYIM: "volatile int counter, count++'ı thread-safe yapar." Bu
     * endpoint bunu ÇÜRÜTÜYOR: {@code counter} volatile OLMASINA RAĞMEN, {@code increment()}
     * hâlâ oku-artır-yaz'dır - visibility garantisi atomicity garantisi VERMEZ.
     */
    @PostMapping("/misconception-check")
    public Map<String, Object> misconceptionCheck(@RequestParam(defaultValue = "20") int threads,
                                                    @RequestParam(defaultValue = "2000") int increments) throws InterruptedException {
        LabLog.banner("VOLATILE", "YANLIŞ VARSAYIM KONTROLÜ — volatile tek başına thread-safe yapar mı?");
        VolatileCounter counter = new VolatileCounter(); // alan volatile'dır - ama increment() hâlâ count++
        RaceConditionLabController.runConcurrently(threads, () -> {
            for (int i = 0; i < increments; i++) {
                counter.increment(); // <- BREAKPOINT: counter++ hâlâ 3 bytecode adımıdır (getfield/iadd/putfield)
            }
        });
        int expected = threads * increments;
        int actual = counter.get();
        boolean stillRaced = actual != expected;
        LabLog.line("volatile int counter ile bile: beklenen={}, gerçek={}, race hâlâ oldu={}", expected, actual, stillRaced);
        LabLog.lesson("volatile SADECE görünürlük garanti eder (bir yazma, DİĞER thread'lerce hemen görülür) - "
                + "ATOMİKLİK garanti ETMEZ. count++ hâlâ oku-artır-yaz'dır, volatile bunu TEK bir adıma indirmez.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scenario", "VOLATILE_MISCONCEPTION_CHECK");
        body.put("commonWrongAnswer", "volatile bir sayaç thread-safe'dir");
        body.put("threads", threads);
        body.put("incrementsPerThread", increments);
        body.put("expected", expected);
        body.put("actual", actual);
        body.put("stillRaced", stillRaced);
        body.put("lesson", "volatile sadece visibility sağlar, atomicity DEĞİL - compound (oku-değiştir-yaz) "
                + "operasyonlar için hâlâ AtomicInteger/synchronized/ReentrantLock gerekir.");
        return body;
    }

    /**
     * volatile'ın GERÇEKTEN çözdüğü senaryo: TEK BİR ATAMA (compound değil). Worker,
     * {@code stopRequested} volatile OLMASAYDI, kendi CPU register/cache'inde bayat
     * {@code false} değerini SONSUZA KADAR görebilir ve {@code requestStop()}'u ASLA
     * fark etmeyebilirdi.
     */
    @PostMapping("/correct-usage")
    public Map<String, Object> correctUsage() throws InterruptedException {
        LabLog.banner("VOLATILE", "DOĞRU KULLANIM — shutdown flag");
        ShutdownFlagWorker worker = new ShutdownFlagWorker();
        Thread workerThread = new Thread(worker::run, "lab-volatile-worker");
        workerThread.start();

        Thread.sleep(50); // worker'ın gerçekten döngüye girmesine izin ver
        worker.requestStop(); // <- BREAKPOINT: tek bir volatile ATAMA (compound değil)
        workerThread.join(2000);
        boolean stoppedInTime = !workerThread.isAlive();

        LabLog.line("Worker durduruldu={}, tamamlanan iterasyon={}", stoppedInTime, worker.getIterationsCompleted());
        LabLog.lesson("stopRequested volatile OLDUĞU İÇİN worker thread'i requestStop()'u NEREDEYSE ANINDA gördü - "
                + "burada compound bir operasyon yok (sadece atama+okuma), bu yüzden visibility TEK BAŞINA yeterliydi.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scenario", "VOLATILE_CORRECT_USAGE");
        body.put("workerStoppedWithinTimeout", stoppedInTime);
        body.put("iterationsCompletedBeforeStop", worker.getIterationsCompleted());
        body.put("lesson", "volatile, TEK bir atama/okuma için (compound operasyon değil) visibility'nin "
                + "tek başına yeterli olduğu klasik doğru kullanım örneğidir.");
        return body;
    }
}
