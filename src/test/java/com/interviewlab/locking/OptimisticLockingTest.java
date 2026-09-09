package com.interviewlab.locking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.interviewlab.AbstractPostgresIntegrationTest;
import com.interviewlab.locking.entity.Product;
import com.interviewlab.locking.entity.LockingProductRepository;
import com.interviewlab.locking.optimistic.bad.ClientControlledVersionService;
import com.interviewlab.locking.optimistic.bad.NoVersionProduct;
import com.interviewlab.locking.optimistic.bad.NoVersionProductRepository;
import com.interviewlab.locking.optimistic.bad.NoVersionStockService;
import com.interviewlab.locking.optimistic.bad.SwallowingOptimisticLockService;
import com.interviewlab.locking.optimistic.bad.UnboundedRetryStockService;
import com.interviewlab.locking.optimistic.good.OptimisticStockService;
import com.interviewlab.locking.optimistic.good.RetryingStockService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/** Yazı ve mülakat cevapları için docs/optimistic-locking.md dosyasına bakın. */
class OptimisticLockingTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private LockingProductRepository productRepository;

    @Autowired
    private NoVersionProductRepository noVersionProductRepository;

    @Autowired
    private OptimisticStockService optimisticStockService;

    @Autowired
    private NoVersionStockService noVersionStockService;

    @Autowired
    private SwallowingOptimisticLockService swallowingOptimisticLockService;

    @Autowired
    private RetryingStockService retryingStockService;

    @Autowired
    private UnboundedRetryStockService unboundedRetryStockService;

    @Autowired
    private ClientControlledVersionService clientControlledVersionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ExecutorService executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldPreventLostUpdateWithOptimisticLock() throws Exception {
        Product product = productRepository.save(new Product("Widget", 100));
        executor = Executors.newFixedThreadPool(1);
        CountDownLatch t2Loaded = new CountDownLatch(1);
        CountDownLatch t1Committed = new CountDownLatch(1);

        Future<?> t2 = executor.submit(() ->
                optimisticStockService.loadSignalWaitThenDecrease(product.getId(), 3, t2Loaded, t1Committed));

        assertThat(t2Loaded.await(5, TimeUnit.SECONDS)).isTrue();
        optimisticStockService.decreaseStock(product.getId(), 2); // T1: yükler (version=0), azaltır, commit yapar -> version=1
        t1Committed.countDown();

        assertThatThrownBy(t2::get)
                .as("T2'nin commit'i, belleğindeki eski (stale) version değerinden oluşturulan WHERE version=? koşulunu kullanır - 0 satır eşleşir")
                .hasCauseInstanceOf(ObjectOptimisticLockingFailureException.class);

        Product reloaded = productRepository.findById(product.getId()).orElseThrow();
        assertThat(reloaded.getStock()).isEqualTo(98); // sadece T1'in azaltması uygulandı
    }

    @Test
    void shouldLoseUpdateWithoutVersionField() throws Exception {
        NoVersionProduct product = noVersionProductRepository.save(new NoVersionProduct("Widget", 100));
        executor = Executors.newFixedThreadPool(1);
        CountDownLatch t2Loaded = new CountDownLatch(1);
        CountDownLatch t1Committed = new CountDownLatch(1);

        Future<?> t2 = executor.submit(() ->
                noVersionStockService.loadSignalWaitThenDecrease(product.getId(), 3, t2Loaded, t1Committed));

        assertThat(t2Loaded.await(5, TimeUnit.SECONDS)).isTrue();
        noVersionStockService.decreaseImmediately(product.getId(), 2); // stock: 100 -> 98
        t1Committed.countDown();

        t2.get(); // hiç exception yok - işte bug bu

        NoVersionProduct reloaded = noVersionProductRepository.findById(product.getId()).orElseThrow();
        assertThat(reloaded.getStock())
                .as("T2, T1'in commit ettiği 98 üzerine inşa etmek yerine kendi eski (stale) taban değerini "
                        + "kullanarak (100-3=97) T1'in azaltmasının üzerine yazdı - doğru sonuca (100-2-3=95) hiç ulaşılmıyor")
                .isEqualTo(97);
    }

    @Test
    void shouldSilentlyDoNothingWhenSwallowingOptimisticLockException() throws Exception {
        Product product = productRepository.save(new Product("Widget", 100));
        executor = Executors.newFixedThreadPool(1);
        CountDownLatch t2Loaded = new CountDownLatch(1);
        CountDownLatch t1Committed = new CountDownLatch(1);

        Future<?> t2 = executor.submit(() -> swallowingOptimisticLockService
                .loadSignalWaitThenDecreaseSwallowingConflicts(product.getId(), 3, t2Loaded, t1Committed));

        assertThat(t2Loaded.await(5, TimeUnit.SECONDS)).isTrue();
        optimisticStockService.decreaseStock(product.getId(), 2);
        t1Committed.countDown();

        t2.get(); // exception fırlatmamalı - çakışma yutuldu

        Product reloaded = productRepository.findById(product.getId()).orElseThrow();
        assertThat(reloaded.getStock())
                .as("T2'nin 3'lük azaltması sessizce düşürüldü - sadece T1'in 2'lik azaltması görünüyor")
                .isEqualTo(98);
    }

    @Test
    void shouldGiveUpAfterBoundedRetryAttemptsUnderSustainedContention() throws Exception {
        Product product = productRepository.save(new Product("Widget", 100_000));
        AtomicBoolean keepHammering = new AtomicBoolean(true);
        Thread hammer = startVersionHammer(product.getId(), keepHammering);
        Thread.sleep(50); // hammer'ın gerçekten dönmeye başladığından emin ol, ilk denemenin şansla çakışmasız geçmesini önle

        try {
            assertThatThrownBy(() -> retryingStockService.decreaseStockWithRetry(product.getId(), 1))
                    .as("satır üzerindeki sürekli çakışma, sınırlı retry bütçesini tüketmeli")
                    .isInstanceOf(IllegalStateException.class)
                    .hasCauseInstanceOf(ObjectOptimisticLockingFailureException.class);
        } finally {
            keepHammering.set(false);
            hammer.join(2000);
        }
    }

    @Test
    void shouldEventuallySucceedWithUnboundedRetryOnceContentionStops() throws Exception {
        Product product = productRepository.save(new Product("Widget", 100_000));
        AtomicBoolean keepHammering = new AtomicBoolean(true);
        Thread hammer = startVersionHammer(product.getId(), keepHammering);

        executor = Executors.newSingleThreadExecutor();
        Future<Integer> result = executor.submit(() -> unboundedRetryStockService.decreaseStockRetryingForever(product.getId(), 1));

        Thread.sleep(300); // hammer'ın bir süre sürekli çakışma üretmesine izin ver
        keepHammering.set(false);
        hammer.join(2000);

        int attempts = result.get(5, TimeUnit.SECONDS);
        assertThat(attempts)
                .as("sınır olmadan, sınırlı bir retry'ı tüketecek çakışma boyunca yeniden denemeye devam etti")
                .isGreaterThan(3);
    }

    @Test
    void shouldIgnoreClientSuppliedVersionAndAlwaysPersistHibernatesOwnIncrement() {
        Product product = productRepository.save(new Product("Widget", 100));

        clientControlledVersionService.updateStockTrustingClientSuppliedVersion(product.getId(), 50, 999L);

        Product reloaded = productRepository.findById(product.getId()).orElseThrow();
        assertThat(reloaded.getVersion())
                .as("entity'nin bellekteki version alanı 999'a zorlanmış olsa bile, Hibernate SET cümlesi için "
                        + "her zaman kendi 'yüklenen değer + 1' hesabını kullanır - client'ın sağladığı değeri "
                        + "değil; bu yüzden burada gerçek bir güvenlik açığı yoktur")
                .isEqualTo(1L);
        assertThat(reloaded.getStock()).isEqualTo(50);
    }

    /** Sürekli hot-row çakışmasını simüle etmek için aynı satıra hızlı ham-JDBC güncellemeleri; her iterasyonda version'ı artırır. */
    private Thread startVersionHammer(Long productId, AtomicBoolean keepRunning) {
        Thread hammer = new Thread(() -> {
            while (keepRunning.get()) {
                jdbcTemplate.update(
                        "update lab_locking_product set stock = stock - 1, version = version + 1 where id = ?", productId);
            }
        });
        hammer.setDaemon(true);
        hammer.start();
        return hammer;
    }
}
