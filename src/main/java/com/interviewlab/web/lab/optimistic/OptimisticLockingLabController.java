package com.interviewlab.web.lab.optimistic;

import com.interviewlab.locking.entity.LockingProductRepository;
import com.interviewlab.locking.entity.Product;
import com.interviewlab.locking.optimistic.bad.NoVersionProduct;
import com.interviewlab.locking.optimistic.bad.NoVersionProductRepository;
import com.interviewlab.locking.optimistic.bad.NoVersionStockService;
import com.interviewlab.locking.optimistic.good.OptimisticStockService;
import com.interviewlab.web.lab.LabLog;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/INTERACTIVE_LABS.md — LAB 04. İki GERÇEK, eşzamanlı transaction, CountDownLatch ile
 * deterministik olarak orkestre edilir (T1/T2'nin ikisi de aynı satırı okur, sonra biri
 * commit eder, sonra öteki devam eder) - bkz. docs/optimistic-locking.md.
 */
@RestController
@RequestMapping("/api/labs/optimistic")
public class OptimisticLockingLabController {

    private static final int INITIAL_STOCK = 10;

    private final NoVersionProductRepository noVersionProductRepository;
    private final NoVersionStockService noVersionStockService;
    private final LockingProductRepository lockingProductRepository;
    private final OptimisticStockService optimisticStockService;
    private final AtomicReference<Long> currentProductId = new AtomicReference<>();

    public OptimisticLockingLabController(NoVersionProductRepository noVersionProductRepository,
                                           NoVersionStockService noVersionStockService,
                                           LockingProductRepository lockingProductRepository,
                                           OptimisticStockService optimisticStockService) {
        this.noVersionProductRepository = noVersionProductRepository;
        this.noVersionStockService = noVersionStockService;
        this.lockingProductRepository = lockingProductRepository;
        this.optimisticStockService = optimisticStockService;
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        noVersionProductRepository.deleteAll();
        lockingProductRepository.deleteAll();
        currentProductId.set(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "OPTIMISTIC_LOCKING");
        body.put("action", "RESET");
        body.put("nextStep", "POST /api/labs/optimistic/bad");
        return body;
    }

    @PostMapping("/bad")
    public Map<String, Object> bad() throws Exception {
        LabLog.banner("OPTIMISTIC LOCKING", "BAD (@Version yok)");
        NoVersionProduct product = noVersionProductRepository.save(new NoVersionProduct("Widget", INITIAL_STOCK));
        currentProductId.set(product.getId());

        ExecutorService executor = Executors.newFixedThreadPool(1);
        CountDownLatch t2Loaded = new CountDownLatch(1);
        CountDownLatch t1Committed = new CountDownLatch(1);
        try {
            Future<?> t2 = executor.submit(() ->
                    noVersionStockService.loadSignalWaitThenDecrease(product.getId(), 3, t2Loaded, t1Committed));

            t2Loaded.await(5, TimeUnit.SECONDS);
            LabLog.line("T2 satırı okudu (stock={}) ve bekliyor - şimdi T1 aynı satırı okuyup HEMEN commit ediyor.", INITIAL_STOCK);
            noVersionStockService.decreaseImmediately(product.getId(), 2);
            LabLog.line("T1 commit oldu: stock {} -> {}", INITIAL_STOCK, INITIAL_STOCK - 2);
            t1Committed.countDown();

            String t2Result;
            try {
                t2.get(5, TimeUnit.SECONDS);
                t2Result = "COMMITTED_OVERWRITING_T1";
            } catch (Exception e) {
                t2Result = "FAILED: " + e.getCause();
            }

            NoVersionProduct reloaded = noVersionProductRepository.findById(product.getId()).orElseThrow();
            LabLog.line("T2 de commit oldu: T1'in azaltmasını görmeden kendi bayat taban değeri üzerinden yazdı.");
            LabLog.lesson("@Version olmadan, dirty checking sadece bellekteki güncel alan değerlerini yazar - "
                    + "satırın yüklendiğinden beri değişip değişmediğini hiç kontrol etmez. T2'nin commit'i "
                    + "T1'in azaltmasının üzerine yazdı: T1'in -2'si TAMAMEN kayboldu.");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("lab", "OPTIMISTIC_LOCKING");
            body.put("mode", "BAD");
            body.put("productId", product.getId());
            body.put("initialStock", INITIAL_STOCK);
            body.put("transaction1Result", "COMMITTED (stock -= 2)");
            body.put("transaction2Result", t2Result + " (stock = 10 - 3 = 7, T1'in -2'sini görmeden)");
            body.put("expectedStockIfNoLostUpdate", INITIAL_STOCK - 2 - 3);
            body.put("finalStock", reloaded.getStock());
            body.put("lostUpdateOccurred", reloaded.getStock() != INITIAL_STOCK - 2 - 3);
            body.put("problem", "@Version alanı yok - T2'nin write'ı T1'in write'ının üzerine sessizce yazdı (lost update).");
            body.put("nextStep", "SELECT * FROM lab_no_version_product; ile doğrula, sonra POST /api/labs/optimistic/good");
            return body;
        } finally {
            executor.shutdownNow();
        }
    }

    @PostMapping("/good")
    public Map<String, Object> good() throws Exception {
        LabLog.banner("OPTIMISTIC LOCKING", "GOOD (@Version)");
        Product product = lockingProductRepository.save(new Product("Widget", INITIAL_STOCK));
        currentProductId.set(product.getId());

        ExecutorService executor = Executors.newFixedThreadPool(1);
        CountDownLatch t2Loaded = new CountDownLatch(1);
        CountDownLatch t1Committed = new CountDownLatch(1);
        try {
            Future<?> t2 = executor.submit(() ->
                    optimisticStockService.loadSignalWaitThenDecrease(product.getId(), 3, t2Loaded, t1Committed));

            t2Loaded.await(5, TimeUnit.SECONDS);
            LabLog.line("T2 satırı okudu (stock={} version=0) ve bekliyor.", INITIAL_STOCK);
            optimisticStockService.decreaseStock(product.getId(), 2);
            LabLog.line("T1 commit oldu: stock {} -> {}, version 0 -> 1", INITIAL_STOCK, INITIAL_STOCK - 2);
            t1Committed.countDown();

            String t2Result;
            try {
                t2.get(5, TimeUnit.SECONDS);
                t2Result = "COMMITTED (BEKLENMEDİK)";
            } catch (Exception e) {
                boolean isOptimisticLockFailure = e.getCause() instanceof ObjectOptimisticLockingFailureException;
                t2Result = isOptimisticLockFailure
                        ? "ObjectOptimisticLockingFailureException (BEKLENEN)"
                        : "FAILED (beklenmeyen tür): " + e.getCause();
            }

            Product reloaded = lockingProductRepository.findById(product.getId()).orElseThrow();
            LabLog.line("T2'nin UPDATE'i WHERE version=0 kullandı ama satır artık version=1 - 0 satır eşleşti.");
            LabLog.lesson("@Version, Hibernate'e her UPDATE'in WHERE cümlesine AND version=? eklettirir. T2'nin "
                    + "bayat version'ı artık eşleşmiyor, Hibernate 0 satırın etkilendiğini görüyor ve "
                    + "ObjectOptimisticLockingFailureException fırlatıyor - lost update FİZİKSEL OLARAK İMKANSIZ.");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("lab", "OPTIMISTIC_LOCKING");
            body.put("mode", "GOOD");
            body.put("productId", product.getId());
            body.put("initialStock", INITIAL_STOCK);
            body.put("initialVersion", 0);
            body.put("transaction1Result", "COMMITTED (stock -= 2, version 0 -> 1)");
            body.put("transaction2Result", t2Result);
            body.put("finalStock", reloaded.getStock());
            body.put("finalVersion", reloaded.getVersion());
            body.put("lostUpdateOccurred", false);
            body.put("lesson", "@Version, stale bir transaction'ın satırı sessizce ezmesini engelledi.");
            return body;
        } finally {
            executor.shutdownNow();
        }
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "OPTIMISTIC_LOCKING");
        body.put("noVersionProducts", noVersionProductRepository.findAll());
        body.put("versionedProducts", lockingProductRepository.findAll());
        body.put("currentProductId", currentProductId.get());
        body.put("dbeaverQuery", "SELECT id, stock FROM lab_no_version_product; "
                + "SELECT id, stock, version FROM lab_locking_product;");
        return body;
    }
}
