package com.interviewlab.labrunner.spring;

import com.interviewlab.labrunner.LabRunnerPrint;
import com.interviewlab.locking.entity.LockingProductRepository;
import com.interviewlab.locking.entity.Product;
import com.interviewlab.locking.optimistic.bad.NoVersionProduct;
import com.interviewlab.locking.optimistic.bad.NoVersionProductRepository;
import com.interviewlab.locking.optimistic.bad.NoVersionStockService;
import com.interviewlab.locking.optimistic.good.OptimisticStockService;
import com.interviewlab.locking.pessimistic.good.PessimisticStockService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/** IntelliJ'de sağ tık -> Run/Debug. Postman/HTTP GEREKMEZ. `docker compose up -d` GEREKİR. */
public final class OptimisticPessimisticSpringLabRunner {

    private static final int INITIAL_STOCK = 10;

    public static void main(String[] args) {
        SpringLabRunnerSupport.run(ctx -> {
            try {
                optimisticDemo(ctx);
                pessimisticDemo(ctx);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void optimisticDemo(org.springframework.context.ConfigurableApplicationContext ctx) throws Exception {
        LabRunnerPrint.banner("OPTIMISTIC LOCKING — @Version yok vs @Version var");
        NoVersionProductRepository noVersionRepo = ctx.getBean(NoVersionProductRepository.class);
        NoVersionStockService noVersionService = ctx.getBean(NoVersionStockService.class);
        LockingProductRepository lockingRepo = ctx.getBean(LockingProductRepository.class);
        OptimisticStockService optimisticService = ctx.getBean(OptimisticStockService.class);

        NoVersionProduct badProduct = noVersionRepo.save(new NoVersionProduct("Widget", INITIAL_STOCK));
        ExecutorService executor = Executors.newFixedThreadPool(1);
        CountDownLatch t2Loaded = new CountDownLatch(1);
        CountDownLatch t1Committed = new CountDownLatch(1);
        try {
            Future<?> t2 = executor.submit(() -> noVersionService.loadSignalWaitThenDecrease(badProduct.getId(), 3, t2Loaded, t1Committed)); // <- BREAKPOINT 1 (T2 thread)
            t2Loaded.await(5, TimeUnit.SECONDS);
            noVersionService.decreaseImmediately(badProduct.getId(), 2); // <- BREAKPOINT 2 (T1, ana thread)
            t1Committed.countDown();
            try { t2.get(5, TimeUnit.SECONDS); } catch (Exception ignored) { }
        } finally {
            executor.shutdown();
        }
        NoVersionProduct badReloaded = noVersionRepo.findById(badProduct.getId()).orElseThrow();
        LabRunnerPrint.fact("BAD finalStock (beklenen 5, lost update varsa 7)", badReloaded.getStock());

        Product goodProduct = lockingRepo.save(new Product("Widget", INITIAL_STOCK));
        ExecutorService executor2 = Executors.newFixedThreadPool(1);
        CountDownLatch t2Loaded2 = new CountDownLatch(1);
        CountDownLatch t1Committed2 = new CountDownLatch(1);
        String t2Result;
        try {
            Future<?> t2 = executor2.submit(() -> optimisticService.loadSignalWaitThenDecrease(goodProduct.getId(), 3, t2Loaded2, t1Committed2)); // <- BREAKPOINT 3
            t2Loaded2.await(5, TimeUnit.SECONDS);
            optimisticService.decreaseStock(goodProduct.getId(), 2); // <- BREAKPOINT 4
            t1Committed2.countDown();
            try {
                t2.get(5, TimeUnit.SECONDS);
                t2Result = "COMMITTED (BEKLENMEDİK)";
            } catch (Exception e) {
                t2Result = e.getCause() instanceof ObjectOptimisticLockingFailureException
                        ? "ObjectOptimisticLockingFailureException (BEKLENEN)" : "OTHER: " + e.getCause();
            }
        } finally {
            executor2.shutdown();
        }
        Product goodReloaded = lockingRepo.findById(goodProduct.getId()).orElseThrow();
        LabRunnerPrint.fact("GOOD t2Result", t2Result);
        LabRunnerPrint.fact("GOOD finalStock/finalVersion", goodReloaded.getStock() + "/" + goodReloaded.getVersion());
    }

    private static void pessimisticDemo(org.springframework.context.ConfigurableApplicationContext ctx) throws Exception {
        LabRunnerPrint.banner("PESSIMISTIC LOCKING — FOR UPDATE gerçekten bloke eder");
        LockingProductRepository lockingRepo = ctx.getBean(LockingProductRepository.class);
        PessimisticStockService pessimisticService = ctx.getBean(PessimisticStockService.class);

        Product product = lockingRepo.save(new Product("Widget", INITIAL_STOCK));
        ExecutorService executor = Executors.newFixedThreadPool(2); // 1'i T1, 1'i T1'i GERÇEKTEN geciktirip serbest bırakan zamanlayıcı
        CountDownLatch t1LockAcquired = new CountDownLatch(1);
        CountDownLatch releaseT1 = new CountDownLatch(1);
        try {
            executor.submit(() -> pessimisticService.lockHoldThenDecrease(product.getId(), 2, t1LockAcquired, releaseT1)); // <- BREAKPOINT 5: findByIdForUpdate - KİLİT ALIR
            t1LockAcquired.await(5, TimeUnit.SECONDS);
            // T1'i AYRI bir thread'de 500ms SONRA serbest bırak - T2'nin bloke olan çağrısı ANA
            // thread'de SENKRON çalışırken bunu ANA thread'in KENDİSİNDEN yapamayız (deadlock olurdu:
            // finally bloğu T2'nin çağrısı DÖNENE KADAR hiç çalışmaz).
            executor.submit(() -> {
                sleepQuietly(500);
                releaseT1.countDown();
            });
            long start = System.currentTimeMillis();
            pessimisticService.lockThenDecreaseImmediately(product.getId(), 3); // <- BREAKPOINT 6: burada BLOKE olur (T1 kilidi bırakana kadar, ~500ms)
            long blockedMillis = System.currentTimeMillis() - start;
            LabRunnerPrint.fact("t2BlockedForMillis (T1'in tuttuğu ~500ms'ye yakın olmalı)", blockedMillis);
        } finally {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
        Product reloaded = lockingRepo.findById(product.getId()).orElseThrow();
        LabRunnerPrint.fact("finalStock (beklenen 5, doğru)", reloaded.getStock());

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("Optimistic: @Version olmadan T2'nin write'ı T1'inkinin üzerine SESSİZCE yazar (lost");
        LabRunnerPrint.line("update); @Version ile Hibernate'in WHERE version=? cümlesi 0 satır etkiler ve");
        LabRunnerPrint.line("ObjectOptimisticLockingFailureException fırlatır. Pessimistic: FOR UPDATE, T2'yi");
        LabRunnerPrint.line("VERİTABANI SEVİYESİNDE fiziksel olarak bloke eder - race koşulu OLUŞAMAZ.");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
