package com.interviewlab.web.lab.pessimistic;

import com.interviewlab.locking.entity.LockingProductRepository;
import com.interviewlab.locking.entity.Product;
import com.interviewlab.locking.optimistic.bad.NoVersionProduct;
import com.interviewlab.locking.optimistic.bad.NoVersionProductRepository;
import com.interviewlab.locking.optimistic.bad.NoVersionStockService;
import com.interviewlab.locking.pessimistic.good.PessimisticStockService;
import com.interviewlab.web.lab.LabLog;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/INTERACTIVE_LABS.md — LAB 05. BAD tarafı, LAB 04'teki {@code @Version} olmadan lost
 * update'in AYNISI: hiçbir kilit olmadan iki transaction aynı satırı okuyup birbirini
 * ezebilir. GOOD tarafı, gerçek bir PostgreSQL {@code FOR UPDATE} satır kilidiyle bu ırkın
 * (race'in) fiziksel olarak imkansız hale geldiğini - ikinci transaction'ın SESSİZCE çakışmak
 * yerine gerçekten BLOKLANDIĞINI - gösterir. Bkz. docs/pessimistic-locking.md.
 */
@RestController
@RequestMapping("/api/labs/pessimistic")
public class PessimisticLockingLabController {

    private static final int INITIAL_STOCK = 10;

    private final NoVersionProductRepository noVersionProductRepository;
    private final NoVersionStockService noVersionStockService;
    private final LockingProductRepository lockingProductRepository;
    private final PessimisticStockService pessimisticStockService;
    private final AtomicReference<Long> currentProductId = new AtomicReference<>();

    public PessimisticLockingLabController(NoVersionProductRepository noVersionProductRepository,
                                            NoVersionStockService noVersionStockService,
                                            LockingProductRepository lockingProductRepository,
                                            PessimisticStockService pessimisticStockService) {
        this.noVersionProductRepository = noVersionProductRepository;
        this.noVersionStockService = noVersionStockService;
        this.lockingProductRepository = lockingProductRepository;
        this.pessimisticStockService = pessimisticStockService;
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        noVersionProductRepository.deleteAll();
        lockingProductRepository.deleteAll();
        currentProductId.set(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "PESSIMISTIC_LOCKING");
        body.put("action", "RESET");
        body.put("nextStep", "POST /api/labs/pessimistic/bad");
        return body;
    }

    @PostMapping("/bad")
    public Map<String, Object> bad() throws Exception {
        LabLog.banner("PESSIMISTIC LOCKING", "BAD (hiçbir kilit yok)");
        NoVersionProduct product = noVersionProductRepository.save(new NoVersionProduct("Widget", INITIAL_STOCK));
        currentProductId.set(product.getId());

        ExecutorService executor = Executors.newFixedThreadPool(1);
        CountDownLatch t2Loaded = new CountDownLatch(1);
        CountDownLatch t1Committed = new CountDownLatch(1);
        try {
            executor.submit(() -> noVersionStockService.loadSignalWaitThenDecrease(product.getId(), 3, t2Loaded, t1Committed));
            t2Loaded.await(5, TimeUnit.SECONDS);
            LabLog.line("T2 satırı KİLİTSİZ okudu ve bekliyor - T1 de kilitsiz aynı satırı okuyup HEMEN commit ediyor.");
            noVersionStockService.decreaseImmediately(product.getId(), 2);
            t1Committed.countDown();
            Thread.sleep(200);

            NoVersionProduct reloaded = noVersionProductRepository.findById(product.getId()).orElseThrow();
            LabLog.lesson("Hiçbir satır kilidi alınmadığı için T1 ve T2 aynı anda aynı satırı okudu - T2'nin "
                    + "commit'i T1'in azaltmasının üzerine sessizce yazdı. FOR UPDATE olmadan bu race koşulu her "
                    + "zaman mümkündür.");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("lab", "PESSIMISTIC_LOCKING");
            body.put("mode", "BAD");
            body.put("productId", product.getId());
            body.put("initialStock", INITIAL_STOCK);
            body.put("expectedStockIfNoLostUpdate", INITIAL_STOCK - 2 - 3);
            body.put("finalStock", reloaded.getStock());
            body.put("lostUpdateOccurred", reloaded.getStock() != INITIAL_STOCK - 2 - 3);
            body.put("problem", "Hiçbir satır kilidi alınmadı - iki transaction aynı satırı eşzamanlı okuyup yazabildi.");
            body.put("nextStep", "POST /api/labs/pessimistic/good ile FOR UPDATE'in T2'yi gerçekten BLOKLADIĞINI gör");
            return body;
        } finally {
            executor.shutdownNow();
        }
    }

    @PostMapping("/good")
    public Map<String, Object> good() throws Exception {
        LabLog.banner("PESSIMISTIC LOCKING", "GOOD (SELECT ... FOR UPDATE)");
        Product product = lockingProductRepository.save(new Product("Widget", INITIAL_STOCK));
        currentProductId.set(product.getId());
        long holdMillis = 500;

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch t1LockAcquired = new CountDownLatch(1);
        CountDownLatch releaseT1 = new CountDownLatch(1);
        try {
            executor.submit(() -> pessimisticStockService.lockHoldThenDecrease(product.getId(), 2, t1LockAcquired, releaseT1));
            t1LockAcquired.await(5, TimeUnit.SECONDS);
            LabLog.line("T1 acquired DB row lock (FOR UPDATE)");

            executor.submit(() -> {
                try {
                    Thread.sleep(holdMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                LabLog.line("T1 COMMIT (releasing lock)");
                releaseT1.countDown();
            });

            LabLog.line("T2 attempting row lock - now BLOCKING until T1 releases it...");
            Instant start = Instant.now();
            pessimisticStockService.lockThenDecreaseImmediately(product.getId(), 3);
            long waitedMillis = Duration.between(start, Instant.now()).toMillis();
            LabLog.line("T2 acquired lock after waiting {}ms, T2 COMMIT", waitedMillis);

            Product reloaded = lockingProductRepository.findById(product.getId()).orElseThrow();
            LabLog.lesson("PESSIMISTIC_WRITE, gerçek bir 'SELECT ... FOR UPDATE' satır kilidi alır - T2, T1 "
                    + "commit edip kilidi bırakana kadar VERİTABANI SEVİYESİNDE fiziksel olarak bekledi. Bu bir "
                    + "'umarım çakışmayız' değil, gerçek bir engelleme (blocking) garantisidir.");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("lab", "PESSIMISTIC_LOCKING");
            body.put("mode", "GOOD");
            body.put("productId", product.getId());
            body.put("initialStock", INITIAL_STOCK);
            body.put("t1LockHeldForMillis", holdMillis);
            body.put("t2BlockedForMillis", waitedMillis);
            body.put("t2ActuallyBlocked", waitedMillis >= holdMillis - 100);
            body.put("finalStock", reloaded.getStock());
            body.put("expectedStock", INITIAL_STOCK - 2 - 3);
            body.put("lostUpdateOccurred", reloaded.getStock() != INITIAL_STOCK - 2 - 3);
            body.put("lesson", "FOR UPDATE, ikinci transaction'ı gerçekten veritabanı seviyesinde bloke eder - "
                    + "race koşulu fiziksel olarak oluşamaz.");
            return body;
        } finally {
            executor.shutdownNow();
        }
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "PESSIMISTIC_LOCKING");
        body.put("noVersionProducts", noVersionProductRepository.findAll());
        body.put("lockingProducts", lockingProductRepository.findAll());
        body.put("currentProductId", currentProductId.get());
        body.put("dbeaverQuery", "SELECT id, stock FROM lab_no_version_product; SELECT id, stock FROM lab_locking_product;");
        return body;
    }
}
