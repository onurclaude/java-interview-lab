package com.interviewlab.locking.deadlock;

import com.interviewlab.labrunner.spring.SpringLabRunnerSupport;

import com.interviewlab.labrunner.LabRunnerPrint;
import com.interviewlab.locking.deadlock.DeterministicOrderTransferService;
import com.interviewlab.locking.deadlock.InconsistentLockOrderTransferService;
import com.interviewlab.locking.entity.LockingProductRepository;
import com.interviewlab.locking.entity.Product;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** IntelliJ'de sağ tık -> Run/Debug. Postman/HTTP GEREKMEZ. `docker compose up -d` GEREKİR. */
public final class DeadlockSpringLabRunner {

    private static final int INITIAL_STOCK = 100;

    public static void main(String[] args) {
        SpringLabRunnerSupport.run(ctx -> {
            try {
                run(ctx);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void run(org.springframework.context.ConfigurableApplicationContext ctx) throws Exception {
        LabRunnerPrint.banner("DEADLOCK — database-level, tutarsız vs tutarlı kilit sırası");

        LockingProductRepository productRepository = ctx.getBean(LockingProductRepository.class);
        InconsistentLockOrderTransferService bad = ctx.getBean(InconsistentLockOrderTransferService.class);
        DeterministicOrderTransferService good = ctx.getBean(DeterministicOrderTransferService.class);

        productRepository.deleteAll();
        Product a = productRepository.save(new Product("A", INITIAL_STOCK));
        Product b = productRepository.save(new Product("B", INITIAL_STOCK));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch t1First = new CountDownLatch(1);
        CountDownLatch t2First = new CountDownLatch(1);
        int failureCount = 0;
        try {
            Future<?> t1 = executor.submit(() -> bad.transferStock(a.getId(), b.getId(), 10, t1First, t2First)); // <- BREAKPOINT 1: findByIdForUpdate(A) sonra findByIdForUpdate(B)
            Future<?> t2 = executor.submit(() -> bad.transferStock(b.getId(), a.getId(), 10, t2First, t1First)); // <- BREAKPOINT 2: findByIdForUpdate(B) sonra findByIdForUpdate(A) - DÖNGÜ
            for (Future<?> f : List.of(t1, t2)) {
                try {
                    f.get(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    failureCount++;
                    LabRunnerPrint.fact("BAD failure (Postgres'in KENDİ deadlock dedektörü)", rootCauseMessage(e));
                }
            }
        } finally {
            executor.shutdown();
            executor.awaitTermination(15, TimeUnit.SECONDS);
        }
        Product reloadedA = productRepository.findById(a.getId()).orElseThrow();
        Product reloadedB = productRepository.findById(b.getId()).orElseThrow();
        LabRunnerPrint.fact("BAD failureCount (tam olarak 1 olmalı)", failureCount);
        LabRunnerPrint.fact("BAD stockConserved (200 olmalı)", reloadedA.getStock() + reloadedB.getStock());

        ExecutorService executor2 = Executors.newFixedThreadPool(2);
        int goodFailureCount = 0;
        try {
            Future<?> t1 = executor2.submit(() -> good.transferStock(a.getId(), b.getId(), 10)); // <- BREAKPOINT 3: firstId=min(A,B) - tutarlı sıra
            Future<?> t2 = executor2.submit(() -> good.transferStock(b.getId(), a.getId(), 10));
            for (Future<?> f : List.of(t1, t2)) {
                try {
                    f.get(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    goodFailureCount++;
                }
            }
        } finally {
            executor2.shutdown();
            executor2.awaitTermination(15, TimeUnit.SECONDS);
        }
        LabRunnerPrint.fact("GOOD failureCount (0 olmalı - deadlock YOK)", goodFailureCount);

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("T1 (A->B) ve T2 (B->A) ÇAPRAZ sırayla FOR UPDATE kilidi istedi - PostgreSQL'in KENDİ");
        LabRunnerPrint.line("deadlock dedektörü döngüyü ~1 saniye içinde tespit edip taraflardan TAM OLARAK BİRİNİ");
        LabRunnerPrint.line("iptal etti (JVM'in ThreadMXBean'i DEĞİL). Tutarlı (id'ye göre) kilit sırası, döngü");
        LabRunnerPrint.line("OLUŞMASINI yapısal olarak imkansız kılar.");
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
