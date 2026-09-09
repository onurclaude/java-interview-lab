package com.interviewlab.locking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.interviewlab.AbstractPostgresIntegrationTest;
import com.interviewlab.common.sql.SqlStatementRecorder;
import com.interviewlab.locking.deadlock.DeterministicOrderTransferService;
import com.interviewlab.locking.deadlock.InconsistentLockOrderTransferService;
import com.interviewlab.locking.entity.Product;
import com.interviewlab.locking.entity.LockingProductRepository;
import com.interviewlab.locking.pessimistic.bad.LockHeldDuringExternalCallService;
import com.interviewlab.locking.pessimistic.bad.LockOutsideTransactionService;
import com.interviewlab.locking.pessimistic.good.PessimisticStockService;
import jakarta.persistence.TransactionRequiredException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Yazı ve mülakat cevapları için docs/pessimistic-locking.md dosyasına bakın. */
class PessimisticLockingTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private LockingProductRepository productRepository;

    @Autowired
    private PessimisticStockService pessimisticStockService;

    @Autowired
    private LockOutsideTransactionService lockOutsideTransactionService;

    @Autowired
    private LockHeldDuringExternalCallService lockHeldDuringExternalCallService;

    @Autowired
    private InconsistentLockOrderTransferService inconsistentLockOrderTransferService;

    @Autowired
    private DeterministicOrderTransferService deterministicOrderTransferService;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        SqlStatementRecorder.clear();
        executor = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void shouldIssueForUpdateSqlForPessimisticWriteLock() {
        Product product = productRepository.save(new Product("Widget", 100));
        pessimisticStockService.lockThenDecreaseImmediately(product.getId(), 1);

        // Hibernate 6, PostgreSQL üzerinde PESSIMISTIC_WRITE için varsayılan olarak
        // "for update" değil "for no key update" üretir (foreign-key kısıtlarını gereksiz yere
        // bloklamayan, PostgreSQL'e özgü daha gevşek bir satır kilidi) - ikisi de gerçek bir
        // satır düzeyinde yazma kilididir, bu yüzden ikisini de kabul ediyoruz.
        assertThat(SqlStatementRecorder.allStatements())
                .as("PESSIMISTIC_WRITE, PostgreSQL üzerinde 'for update' ya da 'for no key update' ifadesine dönüşmeli")
                .anyMatch(sql -> sql.toLowerCase().matches(".*for (no key )?update.*"));
    }

    @Test
    void shouldBlockSecondTransactionWithPessimisticWriteLock() throws Exception {
        Product product = productRepository.save(new Product("Widget", 100));
        CountDownLatch t1LockAcquired = new CountDownLatch(1);
        CountDownLatch releaseT1 = new CountDownLatch(1);
        long holdMillis = 500;

        executor.submit(() -> pessimisticStockService.lockHoldThenDecrease(product.getId(), 1, t1LockAcquired, releaseT1));
        assertThat(t1LockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

        executor.submit(() -> {
            try {
                Thread.sleep(holdMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            releaseT1.countDown();
        });

        Instant start = Instant.now();
        pessimisticStockService.lockThenDecreaseImmediately(product.getId(), 1); // T1 serbest bırakana kadar bloklanmalı
        long waitedMillis = Duration.between(start, Instant.now()).toMillis();

        assertThat(waitedMillis)
                .as("T2, T1'in FOR UPDATE kilidinin serbest bırakılmasını fiziksel olarak beklemiş olmalı")
                .isGreaterThanOrEqualTo(holdMillis - 100);
    }

    @Test
    void shouldThrowWhenAcquiringPessimisticLockOutsideTransaction() {
        Product product = productRepository.save(new Product("Widget", 100));
        // Ham JPA TransactionRequiredException, çağrı bir Spring Data repository'sinden
        // geçtiği için Spring'in exception translation'ı tarafından InvalidDataAccessApiUsageException'a
        // sarılır - bu, PersistenceExceptionTranslationInterceptor'ın her @Repository çağrısı için
        // yaptığı standart, kaçınılamaz bir davranıştır.
        assertThatThrownBy(() -> lockOutsideTransactionService.lockWithoutTransaction(product.getId()))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(TransactionRequiredException.class);
    }

    @Test
    void shouldHoldLockForFullDurationOfExternalCall() throws Exception {
        Product product = productRepository.save(new Product("Widget", 100));
        long simulatedCallMillis = 400;

        Future<?> t1 = executor.submit(() ->
                lockHeldDuringExternalCallService.lockThenCallExternalServiceThenDecrease(product.getId(), 1, simulatedCallMillis));
        Thread.sleep(50); // T1'in kilidi almasına ve "external call"ına girmesine izin ver

        Instant start = Instant.now();
        pessimisticStockService.lockThenDecreaseImmediately(product.getId(), 1);
        long waitedMillis = Duration.between(start, Instant.now()).toMillis();
        t1.get(2, TimeUnit.SECONDS);

        assertThat(waitedMillis)
                .as("kilidi bekleyen ikinci taraf, sadece DB yazmasını değil, simüle edilen dış çağrının TAMAMINI beklemeli")
                .isGreaterThanOrEqualTo(simulatedCallMillis - 150);
    }

    @Test
    void shouldDeadlockAtDatabaseLevelWithInconsistentLockOrder() throws Exception {
        Product a = productRepository.save(new Product("A", 100));
        Product b = productRepository.save(new Product("B", 100));
        CountDownLatch t1HasFirstLock = new CountDownLatch(1);
        CountDownLatch t2HasFirstLock = new CountDownLatch(1);

        Future<?> t1 = executor.submit(() ->
                inconsistentLockOrderTransferService.transferStock(a.getId(), b.getId(), 10, t1HasFirstLock, t2HasFirstLock));
        Future<?> t2 = executor.submit(() ->
                inconsistentLockOrderTransferService.transferStock(b.getId(), a.getId(), 10, t2HasFirstLock, t1HasFirstLock));

        int failures = 0;
        for (Future<?> f : java.util.List.of(t1, t2)) {
            try {
                f.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                failures++;
                assertThat(rootCauseMessage(e).toLowerCase()).contains("deadlock");
            }
        }
        assertThat(failures)
                .as("PostgreSQL'in kendi deadlock dedektörü, birbirini döngüsel olarak bekleyen iki transaction'dan tam olarak birini iptal etmeli")
                .isEqualTo(1);
    }

    @Test
    void shouldNotDeadlockWithDeterministicLockOrder() throws Exception {
        Product a = productRepository.save(new Product("A", 100));
        Product b = productRepository.save(new Product("B", 100));

        Future<?> t1 = executor.submit(() -> deterministicOrderTransferService.transferStock(a.getId(), b.getId(), 10));
        Future<?> t2 = executor.submit(() -> deterministicOrderTransferService.transferStock(b.getId(), a.getId(), 10));

        t1.get(10, TimeUnit.SECONDS);
        t2.get(10, TimeUnit.SECONDS);

        Product reloadedA = productRepository.findById(a.getId()).orElseThrow();
        Product reloadedB = productRepository.findById(b.getId()).orElseThrow();
        assertThat(reloadedA.getStock() + reloadedB.getStock()).isEqualTo(200); // korundu, bozulma yok
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return String.valueOf(current.getMessage());
    }
}
