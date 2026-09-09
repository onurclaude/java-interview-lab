package com.interviewlab.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewlab.AbstractPostgresIntegrationTest;
import com.interviewlab.transaction.isolation.IsolationAnomalyLab;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Gerçek PostgreSQL'e karşı gerçek eşzamanlı transaction'lar - Postgres'in istenen isolation
 * seviyesinden bağımsız olarak hangi anomalileri (ve neden) engellediği dahil, yazı için
 * docs/isolation.md dosyasına bakın.
 */
class IsolationLevelsTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private IsolationAnomalyLab isolationAnomalyLab;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from lab_isolation_account");
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void shouldExhibitNonRepeatableReadUnderReadCommitted() throws Exception {
        jdbcTemplate.update("insert into lab_isolation_account(id, balance) values (1, 100)");
        CountDownLatch readerHasReadOnce = new CountDownLatch(1);
        CountDownLatch writerCommitted = new CountDownLatch(1);

        Future<int[]> readerResult = executor.submit(() ->
                isolationAnomalyLab.readTwiceUnderReadCommitted(1L, readerHasReadOnce, writerCommitted));
        executor.submit(() ->
                isolationAnomalyLab.updateBalanceAfterSignal(1L, 200, readerHasReadOnce, writerCommitted));

        int[] reads = readerResult.get();
        assertThat(reads[0]).isEqualTo(100);
        assertThat(reads[1])
                .as("READ_COMMITTED her ifadede veriyi yeniden okur, bu yüzden diğer transaction'ın commit'ini görür")
                .isEqualTo(200);
    }

    @Test
    void shouldPreventNonRepeatableReadUnderRepeatableRead() throws Exception {
        jdbcTemplate.update("insert into lab_isolation_account(id, balance) values (2, 100)");
        CountDownLatch readerHasReadOnce = new CountDownLatch(1);
        CountDownLatch writerCommitted = new CountDownLatch(1);

        Future<int[]> readerResult = executor.submit(() ->
                isolationAnomalyLab.readTwiceUnderRepeatableRead(2L, readerHasReadOnce, writerCommitted));
        executor.submit(() ->
                isolationAnomalyLab.updateBalanceAfterSignal(2L, 200, readerHasReadOnce, writerCommitted));

        int[] reads = readerResult.get();
        assertThat(reads[0]).isEqualTo(100);
        assertThat(reads[1])
                .as("Postgres REPEATABLE_READ, tüm transaction için tek bir snapshot alır - "
                        + "diğer transaction'ın commit'i bu transaction için görünmez kalmalı")
                .isEqualTo(100);
    }

    @Test
    void shouldExhibitPhantomReadUnderReadCommitted() throws Exception {
        jdbcTemplate.update("insert into lab_isolation_account(id, balance) values (10, 50)");
        CountDownLatch readerCountedOnce = new CountDownLatch(1);
        CountDownLatch writerCommitted = new CountDownLatch(1);

        Future<int[]> readerResult = executor.submit(() ->
                isolationAnomalyLab.countAboveTwiceUnderReadCommitted(75, readerCountedOnce, writerCommitted));
        executor.submit(() ->
                isolationAnomalyLab.insertRowAfterSignal(11L, 999, readerCountedOnce, writerCommitted));

        int[] counts = readerResult.get();
        assertThat(counts[0]).isZero();
        assertThat(counts[1])
                .as("READ_COMMITTED, predicate'i yeniden baştan çalıştırır - yeni commit edilen satır görünür hale gelir: bir phantom")
                .isEqualTo(1);
    }

    @Test
    void shouldPreventPhantomReadUnderRepeatableReadOnPostgres() throws Exception {
        jdbcTemplate.update("insert into lab_isolation_account(id, balance) values (20, 50)");
        CountDownLatch readerCountedOnce = new CountDownLatch(1);
        CountDownLatch writerCommitted = new CountDownLatch(1);

        Future<int[]> readerResult = executor.submit(() ->
                isolationAnomalyLab.countAboveTwiceUnderRepeatableRead(75, readerCountedOnce, writerCommitted));
        executor.submit(() ->
                isolationAnomalyLab.insertRowAfterSignal(21L, 999, readerCountedOnce, writerCommitted));

        int[] counts = readerResult.get();
        assertThat(counts[0]).isZero();
        assertThat(counts[1])
                .as("standart SQL, REPEATABLE READ altında phantom'lara izin verir, ancak Postgres bunu tam "
                        + "snapshot isolation olarak uygular ve bu da phantom'ları engeller - SQL standardının gerektirdiğinden daha güçlü")
                .isZero();
    }

    @Test
    void shouldNeverExhibitDirtyReadRegardlessOfReadUncommittedRequest() throws Exception {
        jdbcTemplate.update("insert into lab_isolation_account(id, balance) values (30, 100)");
        CountDownLatch writerHasWrittenUncommitted = new CountDownLatch(1);
        CountDownLatch readerHasReadSignal = new CountDownLatch(1);

        Future<Integer> readerResult = executor.submit(() -> {
            int value = isolationAnomalyLab.readUnderReadUncommittedWhileOtherTxUncommitted(30L, writerHasWrittenUncommitted);
            readerHasReadSignal.countDown();
            return value;
        });
        Future<?> writerResult = executor.submit(() ->
                isolationAnomalyLab.writeWithoutCommittingThenWait(30L, 999, writerHasWrittenUncommitted, readerHasReadSignal));

        int observed = readerResult.get();
        writerResult.get();

        assertThat(observed)
                .as("PostgreSQL, READ UNCOMMITTED'ı uygulamaz - onu sessizce READ COMMITTED'e yükseltir, "
                        + "bu yüzden commit edilmemiş 999 değeri asla görünür olmamalı")
                .isEqualTo(100);

        Integer finalBalance = jdbcTemplate.queryForObject(
                "select balance from lab_isolation_account where id = ?", Integer.class, 30L);
        assertThat(finalBalance).isEqualTo(999); // writer'ın transaction'ı gerçekten commit oldu, sadece okumamızdan sonra
    }

    @Test
    void shouldRejectSecondCommitWithSerializationFailureUnderRepeatableRead() throws Exception {
        jdbcTemplate.update("insert into lab_isolation_account(id, balance) values (40, 100)");
        CountDownLatch bothHaveRead = new CountDownLatch(2);
        CountDownLatch proceedToWrite = new CountDownLatch(1);

        Callable<Object> task = () -> {
            isolationAnomalyLab.readModifyWriteUnderRepeatableRead(40L, -10, bothHaveRead, proceedToWrite);
            return null;
        };
        Future<Object> t1 = executor.submit(task);
        Future<Object> t2 = executor.submit(task);

        bothHaveRead.await();
        proceedToWrite.countDown();

        int failures = 0;
        int successes = 0;
        for (Future<Object> f : java.util.List.of(t1, t2)) {
            try {
                f.get();
                successes++;
            } catch (Exception e) {
                failures++;
                assertThat(rootCauseMessage(e).toLowerCase())
                        .as("Postgres, kaybeden transaction'ın commit'ini bir serialization failure olarak "
                                + "reddetmeli, kayıp bir güncellemeyi sessizce kabul etmemeli")
                        .containsAnyOf("serializ", "concurrent update");
            }
        }

        assertThat(successes).isEqualTo(1);
        assertThat(failures)
                .as("Postgres'te TEK BAŞINA isolation level (ne @Version ne de açık bir kilit) de kayıp bir "
                        + "güncellemeyi engelleyebilir - bunun bedeli, kaybedenin yeniden denemek zorunda kalmasıdır")
                .isEqualTo(1);
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return String.valueOf(current.getMessage());
    }
}
