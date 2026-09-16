package com.interviewlab.executor;

import com.interviewlab.labrunner.LabRunnerPrint;

import com.interviewlab.executor.bad.UnboundedQueueExecutorService;
import com.interviewlab.executor.good.ProductionThreadPoolExecutorFactory;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** IntelliJ'de sağ tık -> Run/Debug. Postman/HTTP GEREKMEZ - Spring de GEREKMEZ (saf POJO). */
public final class ExecutorLabRunner {

    public static void main(String[] args) throws InterruptedException {
        LabRunnerPrint.banner("EXECUTOR SERVICE — gizli sınırsız kuyruk vs sınırlı kuyruk+policy");

        ExecutorService bad = UnboundedQueueExecutorService.newFixedPoolWithHiddenUnboundedQueue(2);
        ThreadPoolExecutor badTpe = (ThreadPoolExecutor) bad;
        CountDownLatch releaseBad = new CountDownLatch(1);
        for (int i = 0; i < 20; i++) {
            bad.submit(() -> awaitLatch(releaseBad));
        }
        int badQueueSize = badTpe.getQueue().size(); // <- BREAKPOINT 1: hiçbiri reddedilmedi, kuyruk büyüdü
        LabRunnerPrint.fact("BAD queueSizeWhileAllWorkersBusy", badQueueSize);
        releaseBad.countDown();
        bad.shutdown();
        bad.awaitTermination(5, TimeUnit.SECONDS);

        ThreadPoolExecutor good = ProductionThreadPoolExecutorFactory.abortPolicyExecutor(2, 2, 3);
        CountDownLatch releaseGood = new CountDownLatch(1);
        int accepted = 0, rejected = 0;
        for (int i = 0; i < 10; i++) {
            try {
                good.submit(() -> awaitLatch(releaseGood));
                accepted++;
            } catch (RejectedExecutionException e) { // <- BREAKPOINT 2: kapasite (2+3=5) dolunca
                rejected++;
            }
        }
        LabRunnerPrint.fact("GOOD accepted/rejected", accepted + "/" + rejected);
        releaseGood.countDown();
        ProductionThreadPoolExecutorFactory.shutdownGracefully(good, 5);

        List<Integer> discardExecuted = runDiscardDemo(ProductionThreadPoolExecutorFactory.discardPolicyExecutor(1, 1, 1));
        LabRunnerPrint.fact("DiscardPolicy executedTaskIds (3 asla)", discardExecuted);

        List<Integer> discardOldestExecuted = runDiscardDemo(ProductionThreadPoolExecutorFactory.discardOldestPolicyExecutor(1, 1, 1));
        LabRunnerPrint.fact("DiscardOldestPolicy executedTaskIds (2 asla)", discardOldestExecuted);

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("'2 thread'lik havuz' ile 'kuyruk boyutu' TAMAMEN AYRI şeylerdir. Executors.newFixedThreadPool,");
        LabRunnerPrint.line("arkasında SINIRSIZ bir LinkedBlockingQueue kullanır - hiçbir görev asla reddedilmez.");

        LabRunnerPrint.section("TRY");
        LabRunnerPrint.line("BREAKPOINT 1'de badTpe.workQueue'yu Variables panelinde genişlet - 18 Runnable gör.");
    }

    private static List<Integer> runDiscardDemo(ThreadPoolExecutor executor) throws InterruptedException {
        List<Integer> executedTaskIds = new CopyOnWriteArrayList<>();
        CountDownLatch releaseWorker = new CountDownLatch(1);
        try {
            executor.submit(() -> { awaitLatch(releaseWorker); executedTaskIds.add(1); });
            Thread.sleep(50);
            executor.submit(() -> executedTaskIds.add(2));
            Thread.sleep(50);
            executor.submit(() -> executedTaskIds.add(3)); // <- BREAKPOINT 3: JDK DiscardPolicy/DiscardOldestPolicy.rejectedExecution()
            Thread.sleep(50);
            releaseWorker.countDown();
            ProductionThreadPoolExecutorFactory.shutdownGracefully(executor, 5);
            return executedTaskIds;
        } finally {
            executor.shutdownNow();
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
