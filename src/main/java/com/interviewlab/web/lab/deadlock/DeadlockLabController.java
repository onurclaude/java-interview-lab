package com.interviewlab.web.lab.deadlock;

import com.interviewlab.locking.deadlock.DeterministicOrderTransferService;
import com.interviewlab.locking.deadlock.InconsistentLockOrderTransferService;
import com.interviewlab.locking.entity.LockingProductRepository;
import com.interviewlab.locking.entity.Product;
import com.interviewlab.web.lab.LabLog;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PRIMARY interactive surface — bkz. docs/DEBUGGER_LABS.md "DEADLOCK" bölümü.
 * {@code PessimisticLockingTest.shouldDeadlockAtDatabaseLevelWithInconsistentLockOrder()},
 * AYNI davranışın otomatik regresyon kanıtıdır — ikincildir, birincil değil.
 *
 * <p>BAD senaryoda T1 (A->B) ve T2 (B->A) GERÇEKTEN çapraz sırayla {@code SELECT ... FOR
 * UPDATE} alır - bu, PostgreSQL'in KENDİ deadlock dedektörünü tetikler. HTTP isteği ASLA
 * sonsuza kadar asılı kalmaz: {@code Future.get(timeout)} ile SINIRLI (bounded) beklenir VE
 * Postgres'in kendi {@code deadlock_timeout} ayarı (varsayılan 1s) döngüyü otomatik kırar -
 * iki transaction'dan biri GERÇEK bir "deadlock detected" hatasıyla başarısız olur, diğeri
 * devam eder.
 */
@RestController
@RequestMapping("/api/labs/deadlock")
public class DeadlockLabController {

    private static final int INITIAL_STOCK = 100;
    private static final int TRANSFER_AMOUNT = 10;

    private final LockingProductRepository productRepository;
    private final InconsistentLockOrderTransferService inconsistentLockOrderTransferService;
    private final DeterministicOrderTransferService deterministicOrderTransferService;
    private final AtomicReference<Long[]> currentProductIds = new AtomicReference<>();

    public DeadlockLabController(LockingProductRepository productRepository,
                                  InconsistentLockOrderTransferService inconsistentLockOrderTransferService,
                                  DeterministicOrderTransferService deterministicOrderTransferService) {
        this.productRepository = productRepository;
        this.inconsistentLockOrderTransferService = inconsistentLockOrderTransferService;
        this.deterministicOrderTransferService = deterministicOrderTransferService;
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        productRepository.deleteAll();
        Product a = productRepository.save(new Product("A", INITIAL_STOCK));
        Product b = productRepository.save(new Product("B", INITIAL_STOCK));
        currentProductIds.set(new Long[] {a.getId(), b.getId()});

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "DEADLOCK");
        body.put("action", "RESET");
        body.put("productAId", a.getId());
        body.put("productBId", b.getId());
        body.put("breakpointHint", "InconsistentLockOrderTransferService.transferStock() içine breakpoint koy - "
                + "T1'in A'yı, T2'nin B'yi kilitlediği ANI, sonra ikisinin de diğerini istediği ANI izle.");
        body.put("nextStep", "POST /api/labs/deadlock/bad?productAId=" + a.getId() + "&productBId=" + b.getId());
        return body;
    }

    @PostMapping("/bad")
    public Map<String, Object> bad(Long productAId, Long productBId) throws InterruptedException {
        LabLog.banner("DEADLOCK", "BAD (tutarsız kilit sırası - A->B vs B->A)");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch t1HasFirstLock = new CountDownLatch(1);
        CountDownLatch t2HasFirstLock = new CountDownLatch(1);
        try {
            Future<?> t1 = executor.submit(() -> inconsistentLockOrderTransferService.transferStock(
                    productAId, productBId, TRANSFER_AMOUNT, t1HasFirstLock, t2HasFirstLock));
            Future<?> t2 = executor.submit(() -> inconsistentLockOrderTransferService.transferStock(
                    productBId, productAId, TRANSFER_AMOUNT, t2HasFirstLock, t1HasFirstLock));

            // BOUNDED bekleme - HTTP isteği ASLA sonsuza kadar asılı kalmaz. Postgres'in kendi
            // deadlock_timeout'u (varsayılan 1s) zaten döngüyü çok daha erken kırar; 10s sadece
            // testin/CI'ın en kötü durumda bile takılı kalmamasını garanti eden bir üst sınırdır.
            List<String> outcomes = new java.util.ArrayList<>();
            int failureCount = 0;
            for (Future<?> f : List.of(t1, t2)) {
                try {
                    f.get(10, TimeUnit.SECONDS);
                    outcomes.add("COMMITTED");
                } catch (java.util.concurrent.TimeoutException e) {
                    outcomes.add("TIMED_OUT (10s sınırına ulaşıldı - beklenmeyen, Postgres'in kendi deadlock_timeout'u çok daha erken tetiklenmeliydi)");
                } catch (Exception e) {
                    String rootCause = rootCauseMessage(e);
                    boolean isDeadlock = rootCause.toLowerCase().contains("deadlock");
                    outcomes.add(isDeadlock ? "FAILED_WITH_DEADLOCK_DETECTED: " + rootCause : "FAILED_OTHER: " + rootCause);
                    failureCount++;
                }
            }

            Product reloadedA = productRepository.findById(productAId).orElseThrow();
            Product reloadedB = productRepository.findById(productBId).orElseThrow();
            boolean stockConserved = (reloadedA.getStock() + reloadedB.getStock()) == 2 * INITIAL_STOCK;
            LabLog.line("t1={}, t2={}, failureCount={}, stockConserved={}", outcomes.get(0), outcomes.get(1), failureCount, stockConserved);
            LabLog.lesson("T1 (A->B) ve T2 (B->A), ÇAPRAZ sırayla FOR UPDATE kilidi istedi - PostgreSQL'in KENDİ "
                    + "deadlock dedektörü döngüyü tespit etti ve taraflardan TAM OLARAK BİRİNİ 'deadlock detected' "
                    + "hatasıyla iptal etti. Diğeri normal şekilde COMMIT oldu - hiçbir HTTP isteği sonsuza kadar "
                    + "asılı kalmadı (bounded Future.get + Postgres'in kendi timeout'u).");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("lab", "DEADLOCK");
            body.put("mode", "BAD");
            body.put("t1Outcome", outcomes.get(0));
            body.put("t2Outcome", outcomes.get(1));
            body.put("failureCount", failureCount);
            body.put("deadlockDetectedByPostgres", failureCount == 1);
            body.put("stockConserved", stockConserved);
            body.put("problem", "Kilitler tutarsız sırada alındı - PostgreSQL taraflardan birini deadlock hatasıyla iptal etmek ZORUNDA kaldı.");
            body.put("nextStep", "POST /api/labs/deadlock/good?productAId=" + productAId + "&productBId=" + productBId);
            return body;
        } finally {
            executor.shutdown();
            executor.awaitTermination(15, TimeUnit.SECONDS);
        }
    }

    @PostMapping("/good")
    public Map<String, Object> good(Long productAId, Long productBId) throws InterruptedException {
        LabLog.banner("DEADLOCK", "GOOD (tutarlı kilit sırası - her zaman küçük id önce)");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> t1 = executor.submit(() -> deterministicOrderTransferService.transferStock(productAId, productBId, TRANSFER_AMOUNT));
            Future<?> t2 = executor.submit(() -> deterministicOrderTransferService.transferStock(productBId, productAId, TRANSFER_AMOUNT));

            List<String> outcomes = new java.util.ArrayList<>();
            int failureCount = 0;
            for (Future<?> f : List.of(t1, t2)) {
                try {
                    f.get(10, TimeUnit.SECONDS);
                    outcomes.add("COMMITTED");
                } catch (Exception e) {
                    outcomes.add("FAILED: " + rootCauseMessage(e));
                    failureCount++;
                }
            }

            Product reloadedA = productRepository.findById(productAId).orElseThrow();
            Product reloadedB = productRepository.findById(productBId).orElseThrow();
            boolean stockConserved = (reloadedA.getStock() + reloadedB.getStock()) == 2 * INITIAL_STOCK;
            LabLog.lesson("Her iki transfer de ÖNCE küçük id'yi kilitledi - zıt yönlerdeki transferler artık bir "
                    + "döngü OLUŞTURMAK yerine AYNI ilk kilit için sırayla bekledi. HİÇBİR deadlock oluşmadı.");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("lab", "DEADLOCK");
            body.put("mode", "GOOD");
            body.put("t1Outcome", outcomes.get(0));
            body.put("t2Outcome", outcomes.get(1));
            body.put("failureCount", failureCount);
            body.put("stockConserved", stockConserved);
            body.put("lesson", "Kilitleri her zaman tutarlı, global bir sırayla (id'ye göre) almak deadlock'u YAPISAL OLARAK imkansız hale getirdi.");
            return body;
        } finally {
            executor.shutdown();
            executor.awaitTermination(15, TimeUnit.SECONDS);
        }
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "DEADLOCK");
        body.put("products", productRepository.findAll());
        body.put("dbeaverQuery", "SELECT * FROM lab_locking_product; -- deadlock ANINDA: SELECT * FROM pg_locks WHERE NOT granted;");
        return body;
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
