package com.interviewlab.async;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewlab.async.completablefuture.bad.BlockingGetAggregationService;
import com.interviewlab.async.completablefuture.bad.DefaultCommonPoolService;
import com.interviewlab.async.completablefuture.good.ComposedAggregationService;
import com.interviewlab.async.completablefuture.good.ExceptionPropagationDemo;
import com.interviewlab.async.completablefuture.good.ThenApplyVsThenComposeDemo;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Yazı ve mülakat cevapları için docs/completable-future.md dosyasına bakın. */
class CompletableFutureTest {

    private static final long CALL_DURATION_MILLIS = 200;

    @Test
    void shouldTakeSumOfDurationsWhenBlockingOnEachFutureImmediately() throws ExecutionException, InterruptedException {
        Executor executor = Executors.newFixedThreadPool(3);
        BlockingGetAggregationService service = new BlockingGetAggregationService(executor);

        long start = System.nanoTime();
        service.aggregateSequentiallyByBlocking(
                slowCall("orders"), slowCall("payments"), slowCall("recommendations"));
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(elapsedMillis)
                .as("bir sonrakini başlatmadan önce her future'da bloklamak, üç çağrının da sıralı çalışmasına neden olur")
                .isGreaterThanOrEqualTo(CALL_DURATION_MILLIS * 3 - 50);
    }

    @Test
    void shouldTakeMaxDurationWhenComposingFuturesConcurrently() {
        Executor executor = Executors.newFixedThreadPool(3);
        ComposedAggregationService service = new ComposedAggregationService(executor);

        long start = System.nanoTime();
        String result = service.aggregateConcurrently(
                slowCall("orders"), slowCall("payments"), slowCall("recommendations")).join();
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(result).isEqualTo("orders|payments|recommendations");
        assertThat(elapsedMillis)
                .as("bloklamadan önce üçünü de başlatmak, toplam sürenin toplam yerine TEK bir çağrının süresine yakın olması anlamına gelir")
                .isLessThan(CALL_DURATION_MILLIS * 2);
    }

    @Test
    void shouldStarveCommonPoolWhenUsingDefaultExecutorForBlockingWork() throws InterruptedException {
        int commonPoolParallelism = ForkJoinPool.getCommonPoolParallelism();
        CountDownLatch release = new CountDownLatch(1);

        // Tam olarak DefaultCommonPoolService'in yavaş/bloklayan bir iş verildiğinde yaptığı gibi,
        // her common-pool worker'ını bloklayan bir görevle doyur.
        for (int i = 0; i < commonPoolParallelism; i++) {
            DefaultCommonPoolService.runOnCommonPool(() -> {
                awaitUninterruptibly(release);
                return "blocked";
            });
        }

        long start = System.nanoTime();
        // İlgisiz bir parallelStream() işlemi de (artık tamamen dolu olan) common pool'a ihtiyaç duyar.
        CompletableFuture<Integer> parallelStreamWork = CompletableFuture.supplyAsync(() ->
                IntStream.range(0, commonPoolParallelism + 1).parallel().map(x -> x * 2).sum());

        // Common pool tamamen bloklanmışken bu işlem imkansız şekilde tamamlanamaz.
        boolean finishedWhileStillBlocked = false;
        try {
            parallelStreamWork.get(300, TimeUnit.MILLISECONDS);
            finishedWhileStillBlocked = true;
        } catch (java.util.concurrent.TimeoutException | ExecutionException expected) {
            // beklenen: common pool'da bunu çalıştıracak boş bir worker yok
        } finally {
            release.countDown();
        }

        assertThat(finishedWhileStillBlocked)
                .as("bu kod, paylaşılan common pool'u bloklayan işle doyurmuşken, ilgisiz bir parallelStream() "
                        + "görevi ilerleme kaydedemiyor olmalıdır")
                .isFalse();

        parallelStreamWork.join(); // release artık geri sayıldığına göre, işi bitirip temizlenmesine izin ver
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isNotNull();
    }

    @Test
    void shouldProduceNestedFutureWithThenApplyAndFlatFutureWithThenCompose() {
        CompletableFuture<String> initial = CompletableFuture.completedFuture("id-1");

        CompletableFuture<CompletableFuture<String>> nested =
                ThenApplyVsThenComposeDemo.nestedWithThenApply(initial, id -> CompletableFuture.completedFuture("fetched-" + id));
        assertThat(nested.join().join())
                .as("thenApply, iç future'ı açmak için İKİNCİ bir join()'e ihtiyaç duyar")
                .isEqualTo("fetched-id-1");

        CompletableFuture<String> flattened =
                ThenApplyVsThenComposeDemo.flattenedWithThenCompose(initial, id -> CompletableFuture.completedFuture("fetched-" + id));
        assertThat(flattened.join())
                .as("thenCompose otomatik olarak düzleştirir - sadece bir join() yeterlidir")
                .isEqualTo("fetched-id-1");
    }

    @Test
    void shouldRecoverWithExceptionally() {
        String result = ExceptionPropagationDemo.withExceptionally(
                CompletableFutureTest::alwaysFails, "fallback").join();
        assertThat(result).isEqualTo("fallback");
    }

    @Test
    void shouldRecoverWithHandleOnBothPaths() {
        String failurePath = ExceptionPropagationDemo.withHandle(CompletableFutureTest::alwaysFails, "fallback").join();
        String successPath = ExceptionPropagationDemo.withHandle(() -> "ok", "fallback").join();
        assertThat(failurePath).isEqualTo("fallback");
        assertThat(successPath).isEqualTo("ok");
    }

    @Test
    void shouldObserveButNotSuppressFailureWithWhenComplete() {
        AtomicReference<Throwable> observed = new AtomicReference<>();
        CompletableFuture<String> future = ExceptionPropagationDemo.withWhenCompleteObserving(
                CompletableFutureTest::alwaysFails, observed);

        org.assertj.core.api.Assertions.assertThatThrownBy(future::join)
                .as("whenComplete exception'ı YUTMAMALIDIR - future hâlâ başarısız durumdadır")
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(observed.get()).as("ama whenComplete yine de bunu gözlemlemiş olmalıdır").isNotNull();
    }

    private static String alwaysFails() {
        throw new IllegalStateException("simulated failure");
    }

    private static java.util.function.Supplier<String> slowCall(String name) {
        return () -> {
            try {
                Thread.sleep(CALL_DURATION_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return name;
        };
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
