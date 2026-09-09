package com.interviewlab.web;

import com.interviewlab.executor.good.ProductionThreadPoolExecutorFactory;
import com.interviewlab.locking.entity.LockingProductRepository;
import com.interviewlab.locking.entity.Product;
import com.interviewlab.locking.optimistic.good.RetryingStockService;
import com.interviewlab.locking.pessimistic.good.PessimisticStockService;
import com.interviewlab.transaction.entity.Account;
import com.interviewlab.transaction.entity.AccountRepository;
import com.interviewlab.transaction.entity.AuditLogRepository;
import com.interviewlab.transaction.propagation.OrderProcessingException;
import com.interviewlab.transaction.propagation.bad.SelfInvocationPaymentService;
import com.interviewlab.transaction.propagation.good.OrderService;
import com.interviewlab.transaction.rollback.SimulatedCheckedFailureException;
import com.interviewlab.transaction.rollback.bad.CheckedExceptionNoRollbackService;
import com.interviewlab.transaction.rollback.good.RollbackForCheckedExceptionService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manuel keşif için, laboratuvarların bir kısmına Postman dostu giriş noktaları. Bu
 * projenin başka yerlerinde olduğu gibi, her davranışın GÜVENİLİR, assertion yapılabilir
 * kanıtı test paketinde yer alır - bu endpoint'ler bunun üzerine bir kolaylıktır, onun
 * yerine geçmez.
 */
@RestController
public class LabDemoController {

    private final LockingProductRepository lockingProductRepository;
    private final RetryingStockService retryingStockService;
    private final PessimisticStockService pessimisticStockService;
    private final AccountRepository accountRepository;
    private final AuditLogRepository auditLogRepository;
    private final CheckedExceptionNoRollbackService checkedExceptionNoRollbackService;
    private final RollbackForCheckedExceptionService rollbackForCheckedExceptionService;
    private final SelfInvocationPaymentService selfInvocationPaymentService;
    private final OrderService orderService;
    private final ThreadPoolExecutor demoExecutor;

    public LabDemoController(LockingProductRepository lockingProductRepository,
                              RetryingStockService retryingStockService,
                              PessimisticStockService pessimisticStockService,
                              AccountRepository accountRepository,
                              AuditLogRepository auditLogRepository,
                              CheckedExceptionNoRollbackService checkedExceptionNoRollbackService,
                              RollbackForCheckedExceptionService rollbackForCheckedExceptionService,
                              SelfInvocationPaymentService selfInvocationPaymentService,
                              OrderService orderService) {
        this.lockingProductRepository = lockingProductRepository;
        this.retryingStockService = retryingStockService;
        this.pessimisticStockService = pessimisticStockService;
        this.accountRepository = accountRepository;
        this.auditLogRepository = auditLogRepository;
        this.checkedExceptionNoRollbackService = checkedExceptionNoRollbackService;
        this.rollbackForCheckedExceptionService = rollbackForCheckedExceptionService;
        this.selfInvocationPaymentService = selfInvocationPaymentService;
        this.orderService = orderService;
        this.demoExecutor = ProductionThreadPoolExecutorFactory.callerRunsPolicyExecutor(4, 8, 50);
    }

    @PostMapping("/lab/optimistic-lock/reset")
    public Map<String, Object> resetOptimisticLockDemo() {
        Product product = lockingProductRepository.save(new Product("Demo Widget", 100));
        return Map.of("productId", product.getId(), "stock", product.getStock(), "version", product.getVersion());
    }

    @PostMapping("/lab/optimistic-lock/run")
    public Map<String, Object> runOptimisticLockDemo(@RequestParam Long productId, @RequestParam(defaultValue = "1") int amount) {
        int attempts = retryingStockService.decreaseStockWithRetry(productId, amount);
        Product reloaded = lockingProductRepository.findById(productId).orElseThrow();
        return Map.of("attempts", attempts, "stock", reloaded.getStock(), "version", reloaded.getVersion());
    }

    @PostMapping("/lab/pessimistic-lock/run")
    public Map<String, Object> runPessimisticLockDemo(@RequestParam Long productId, @RequestParam(defaultValue = "1") int amount) {
        pessimisticStockService.lockThenDecreaseImmediately(productId, amount);
        Product reloaded = lockingProductRepository.findById(productId).orElseThrow();
        return Map.of("stock", reloaded.getStock());
    }

    @PostMapping("/lab/executor/run")
    public Map<String, Object> runExecutorDemo(@RequestParam(defaultValue = "20") int tasks) throws InterruptedException {
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(tasks);
        for (int i = 0; i < tasks; i++) {
            demoExecutor.submit(() -> {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        done.await();
        return Map.of("tasksSubmitted", tasks, "poolSize", demoExecutor.getPoolSize(),
                "completedTaskCount", demoExecutor.getCompletedTaskCount());
    }

    @PostMapping("/lab/completable-future/run")
    public Map<String, Object> runCompletableFutureDemo() {
        Instant start = Instant.now();
        CompletableFuture<String> orders = CompletableFuture.supplyAsync(() -> slowCall("orders"), demoExecutor);
        CompletableFuture<String> payments = CompletableFuture.supplyAsync(() -> slowCall("payments"), demoExecutor);
        CompletableFuture<String> recommendations = CompletableFuture.supplyAsync(() -> slowCall("recommendations"), demoExecutor);
        String combined = CompletableFuture.allOf(orders, payments, recommendations)
                .thenApply(v -> orders.join() + "|" + payments.join() + "|" + recommendations.join())
                .join();
        long elapsedMillis = Duration.between(start, Instant.now()).toMillis();
        return Map.of("result", combined, "elapsedMillis", elapsedMillis);
    }

    @PostMapping("/lab/transaction/rollback")
    public Map<String, Object> runRollbackDemo(@RequestParam(defaultValue = "checked") String mode) {
        Account account = accountRepository.save(new Account("Demo-" + mode, new BigDecimal("100.00")));
        String outcome;
        try {
            if ("unchecked".equals(mode) || "fixed".equals(mode)) {
                rollbackForCheckedExceptionService.debitThenFailWithCheckedException(account.getId(), new BigDecimal("30.00"));
            } else {
                checkedExceptionNoRollbackService.debitThenFailWithCheckedException(account.getId(), new BigDecimal("30.00"));
            }
            outcome = "unexpectedly succeeded";
        } catch (SimulatedCheckedFailureException e) {
            outcome = "failed as expected: " + e.getMessage();
        }
        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        return Map.of("mode", mode, "outcome", outcome, "finalBalance", reloaded.getBalance());
    }

    @PostMapping("/lab/transaction/requires-new")
    public Map<String, Object> runRequiresNewDemo(@RequestParam(defaultValue = "false") boolean selfInvocation) {
        Account account = accountRepository.save(new Account("Demo-audit", new BigDecimal("500.00")));
        String auditMessage = "demo-" + account.getId() + "-" + selfInvocation;
        try {
            if (selfInvocation) {
                selfInvocationPaymentService.processPayment(account.getId(), new BigDecimal("50.00"), auditMessage);
            } else {
                orderService.processPayment(account.getId(), new BigDecimal("50.00"), auditMessage);
            }
        } catch (OrderProcessingException e) {
            // beklenen: simüle edilmiş ödeme gateway zaman aşımı
        }
        boolean auditSurvived = !auditLogRepository.findByMessage(auditMessage).isEmpty();
        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        return Map.of("selfInvocation", selfInvocation, "auditSurvivedRollback", auditSurvived, "finalBalance", reloaded.getBalance());
    }

    private static String slowCall(String name) {
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return name;
    }
}
