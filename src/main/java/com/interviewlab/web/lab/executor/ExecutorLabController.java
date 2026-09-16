package com.interviewlab.web.lab.executor;

import com.interviewlab.executor.bad.UnboundedQueueExecutorService;
import com.interviewlab.executor.good.ProductionThreadPoolExecutorFactory;
import com.interviewlab.web.lab.LabLog;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/INTERACTIVE_LABS.md — LAB 07. {@code Executors.newFixedThreadPool(n)}'in kuyruğunun
 * gerçekten sınırsız büyüdüğünü, ve bounded bir {@link ThreadPoolExecutor} + rejection
 * policy'nin bunu nasıl gözlemlenebilir backpressure'a çevirdiğini gösterir - bkz.
 * docs/executor-service.md.
 */
@RestController
@RequestMapping("/api/labs/executor")
public class ExecutorLabController {

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "EXECUTOR_SERVICE");
        body.put("action", "RESET");
        body.put("nextStep", "POST /api/labs/executor/bad");
        return body;
    }

    @PostMapping("/bad")
    public Map<String, Object> bad() throws InterruptedException {
        LabLog.banner("EXECUTOR SERVICE", "BAD (newFixedThreadPool - gizli sınırsız kuyruk)");
        ExecutorService executor = UnboundedQueueExecutorService.newFixedPoolWithHiddenUnboundedQueue(2);
        ThreadPoolExecutor tpe = (ThreadPoolExecutor) executor;
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        int tasksToSubmit = 20;

        for (int i = 0; i < tasksToSubmit; i++) {
            executor.submit(() -> awaitLatch(releaseWorkers));
        }
        int queueSizeWhileBlocked = tpe.getQueue().size();
        int activeCountWhileBlocked = tpe.getActiveCount();
        int poolSizeWhileBlocked = tpe.getPoolSize();
        LabLog.line("2 thread'lik havuza {} bloklayan görev gönderildi - hiçbiri reddedilmedi. Kuyruk boyutu: {}",
                tasksToSubmit, queueSizeWhileBlocked);
        releaseWorkers.countDown();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        LabLog.lesson("newFixedThreadPool(2), '2 thread' der ama arkasında sınırsız bir LinkedBlockingQueue vardır - "
                + "hiçbir görev asla reddedilmez, sadece kuyrukta sınırsız birikir. Gerçek production'da bu, heap "
                + "tükenene kadar süren bir OutOfMemoryError'dur.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "EXECUTOR_SERVICE");
        body.put("mode", "BAD");
        body.put("corePoolSize", 2);
        body.put("maximumPoolSize", 2);
        body.put("queueCapacity", "UNBOUNDED (Integer.MAX_VALUE)");
        body.put("poolSize", poolSizeWhileBlocked);
        body.put("activeCount", activeCountWhileBlocked);
        body.put("tasksSubmitted", tasksToSubmit);
        body.put("tasksRejected", 0);
        body.put("queueSizeWhileAllWorkersBusy", queueSizeWhileBlocked);
        body.put("problem", "Hiçbir görev reddedilmedi - kuyruk sınırsız büyüdü, hiçbir backpressure uygulanmadı.");
        body.put("nextStep", "POST /api/labs/executor/good ile sınırlı kuyruk + AbortPolicy'nin gerçek reddi gör");
        return body;
    }

    @PostMapping("/good")
    public Map<String, Object> good() throws InterruptedException {
        LabLog.banner("EXECUTOR SERVICE", "GOOD (sınırlı kuyruk + AbortPolicy)");
        ThreadPoolExecutor executor = ProductionThreadPoolExecutorFactory.abortPolicyExecutor(2, 2, 3);
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        int accepted = 0;
        int rejected = 0;
        int attempted = 10;

        for (int i = 0; i < attempted; i++) {
            try {
                executor.submit(() -> awaitLatch(releaseWorkers));
                accepted++;
            } catch (RejectedExecutionException e) {
                rejected++;
            }
        }
        int queueSizeBeforeRelease = executor.getQueue().size();
        int activeCountBeforeRelease = executor.getActiveCount();
        int poolSizeBeforeRelease = executor.getPoolSize();
        LabLog.line("{} görev denendi: {} kabul edildi (2 çalışan + 3 kuyruk kapasitesi = 5), {} RejectedExecutionException ile reddedildi",
                attempted, accepted, rejected);
        releaseWorkers.countDown();
        ProductionThreadPoolExecutorFactory.shutdownGracefully(executor, 5);
        LabLog.lesson("Sınırlı bir ArrayBlockingQueue + AbortPolicy, sınırsız büyümeyi ÇAĞIRANIN karşılamak "
                + "zorunda olduğu açık, anında bir RejectedExecutionException'a çevirir - sessiz bellek şişmesi yerine.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "EXECUTOR_SERVICE");
        body.put("mode", "GOOD");
        body.put("corePoolSize", 2);
        body.put("maximumPoolSize", 2);
        body.put("queueCapacity", 3);
        body.put("poolSize", poolSizeBeforeRelease);
        body.put("activeCount", activeCountBeforeRelease);
        body.put("queueSizeBeforeRelease", queueSizeBeforeRelease);
        body.put("tasksAttempted", attempted);
        body.put("tasksAccepted", accepted);
        body.put("tasksRejected", rejected);
        body.put("lesson", "kapasite (2+3=5) dolunca RejectedExecutionException fırlatıldı - backpressure açık ve gözlemlenebilir.");
        return body;
    }

    @PostMapping("/discard")
    public Map<String, Object> discard() throws InterruptedException {
        LabLog.banner("EXECUTOR SERVICE", "DiscardPolicy (reddedilen görev SESSİZCE atılır)");
        return runDiscardDemo(ProductionThreadPoolExecutorFactory.discardPolicyExecutor(1, 1, 1), "DISCARD",
                "DiscardPolicy, kapasiteyi aşan görevi (id=3) SESSİZCE attı - ne exception fırladı ne de çağıran "
                        + "bunu çalıştırdı. executedTaskIds içinde 3 asla GÖRÜNMEZ.");
    }

    @PostMapping("/discard-oldest")
    public Map<String, Object> discardOldest() throws InterruptedException {
        LabLog.banner("EXECUTOR SERVICE", "DiscardOldestPolicy (kuyruktaki EN ESKİ görev atılır)");
        return runDiscardDemo(ProductionThreadPoolExecutorFactory.discardOldestPolicyExecutor(1, 1, 1), "DISCARD_OLDEST",
                "DiscardOldestPolicy, YENİ görevi (id=3) kabul etmek için kuyruktaki EN ESKİ görevi (id=2) attı - "
                        + "executedTaskIds içinde 1 VE 3 var ama 2 YOK (2, 3'e yer açmak için sessizce feda edildi).");
    }

    private Map<String, Object> runDiscardDemo(ThreadPoolExecutor executor, String mode, String lesson) throws InterruptedException {
        List<Integer> executedTaskIds = new CopyOnWriteArrayList<>();
        CountDownLatch releaseWorker = new CountDownLatch(1);
        try {
            executor.submit(() -> { // id=1: TEK worker'ı işgal eder (corePoolSize=1)
                awaitLatch(releaseWorker);
                executedTaskIds.add(1);
            });
            Thread.sleep(50); // task 1'in worker'ı GERÇEKTEN işgal etmesini garantiye al
            executor.submit(() -> executedTaskIds.add(2)); // id=2: kuyruğu doldurur (capacity=1)
            Thread.sleep(50);
            executor.submit(() -> executedTaskIds.add(3)); // id=3: havuz+kuyruk DOLU - RejectedExecutionHandler devreye girer
            Thread.sleep(50);
            int queueSizeAfterRejection = executor.getQueue().size();

            releaseWorker.countDown();
            ProductionThreadPoolExecutorFactory.shutdownGracefully(executor, 5);

            LabLog.line("executedTaskIds={}, queueSizeAfterRejection={}", executedTaskIds, queueSizeAfterRejection);
            LabLog.lesson(lesson);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("lab", "EXECUTOR_SERVICE");
            body.put("mode", mode);
            body.put("corePoolSize", 1);
            body.put("maximumPoolSize", 1);
            body.put("queueCapacity", 1);
            body.put("submittedTaskIds", List.of(1, 2, 3));
            body.put("executedTaskIds", executedTaskIds);
            body.put("queueSizeAfterThirdSubmission", queueSizeAfterRejection);
            body.put("lesson", lesson);
            return body;
        } finally {
            executor.shutdownNow();
        }
    }

    @PostMapping("/caller-runs")
    public Map<String, Object> callerRuns() throws InterruptedException {
        LabLog.banner("EXECUTOR SERVICE", "CallerRunsPolicy");
        ThreadPoolExecutor executor = ProductionThreadPoolExecutorFactory.callerRunsPolicyExecutor(1, 1, 1);
        CountDownLatch releaseWorkers = new CountDownLatch(1);

        executor.submit(() -> awaitLatch(releaseWorkers)); // 1 worker'ı işgal eder
        executor.submit(() -> awaitLatch(releaseWorkers)); // 1 kapasiteli kuyruğu doldurur

        String httpThreadName = Thread.currentThread().getName();
        AtomicReference<String> executedOnThreadName = new AtomicReference<>();
        executor.execute(() -> executedOnThreadName.set(Thread.currentThread().getName()));
        boolean ranSynchronouslyOnCallingThread = httpThreadName.equals(executedOnThreadName.get());
        LabLog.line("Havuz+kuyruk dolu iken gönderilen 3. görev, ÇAĞIRAN thread'de ({}) senkron olarak çalıştı - executedOn={}",
                httpThreadName, executedOnThreadName.get());

        releaseWorkers.countDown();
        ProductionThreadPoolExecutorFactory.shutdownGracefully(executor, 5);
        LabLog.lesson("CallerRunsPolicy exception fırlatmaz - reddedilen görevi ÇAĞIRAN thread'de senkron olarak "
                + "çalıştırır. Bu doğal bir backpressure biçimidir (gönderen yavaşlar), ama çağıran thread artık "
                + "havuzun işini yapıyor demektir.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "EXECUTOR_SERVICE");
        body.put("mode", "CALLER_RUNS");
        body.put("httpRequestThread", httpThreadName);
        body.put("thirdTaskExecutedOnThread", executedOnThreadName.get());
        body.put("ranSynchronouslyOnCallingThread", ranSynchronouslyOnCallingThread);
        body.put("lesson", "CallerRunsPolicy, reddedilen görevi HTTP request thread'inin kendisinde çalıştırdı - exception yok, ama çağıran bloklandı.");
        return body;
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "EXECUTOR_SERVICE");
        body.put("note", "Bu lab durumu DB'de değil, her çağrıda taze oluşturulan executor'larda tutar - state yalnızca response'ların kendisidir.");
        return body;
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
