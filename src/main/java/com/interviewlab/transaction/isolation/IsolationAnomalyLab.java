package com.interviewlab.transaction.isolation;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Aşağıdaki metot çiftlerinin her biri bilinçli olarak tekrarlanmıştır (her izolasyon
 * seviyesi için bir kez), çünkü {@code @Transactional(isolation = ...)} derleme zamanı bir
 * annotation özelliğidir - çalışma zamanında parametrize edilemez. Her metot bilinçli olarak
 * {@link JdbcTemplate} üzerinden ham JDBC kullanıyor - nedeni için {@link IsolationAccount}'ın
 * javadoc'una bakın.
 *
 * <p>Her senaryoda iki {@link CountDownLatch}, iş parçacıklarının birbirine girmesinin
 * (interleaving) rastgele thread zamanlamasının ilginç durumu üretmesini ummak yerine
 * deterministik olmasını sağlar (T1 okur, ARDINDAN T2 yazar+commit eder, ARDINDAN T1 tekrar
 * okur).
 */
@Service
public class IsolationAnomalyLab {

    private static final Logger log = LoggerFactory.getLogger(IsolationAnomalyLab.class);
    private static final String SELECT_BALANCE = "select balance from lab_isolation_account where id = ?";
    private static final String UPDATE_BALANCE = "update lab_isolation_account set balance = ? where id = ?";
    private static final String COUNT_ABOVE = "select count(*) from lab_isolation_account where balance > ?";

    private final JdbcTemplate jdbcTemplate;

    public IsolationAnomalyLab(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---------- Tekrarlanamayan okuma (Non-repeatable read) ----------

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int[] readTwiceUnderReadCommitted(long accountId, CountDownLatch readerHasReadOnce, CountDownLatch writerCommitted) {
        int first = jdbcTemplate.queryForObject(SELECT_BALANCE, Integer.class, accountId);
        log.info("[READ_COMMITTED] T1 first read balance={}", first);
        readerHasReadOnce.countDown();
        awaitLatch(writerCommitted);
        int second = jdbcTemplate.queryForObject(SELECT_BALANCE, Integer.class, accountId);
        log.info("[READ_COMMITTED] T1 second read balance={}", second);
        return new int[] {first, second};
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public int[] readTwiceUnderRepeatableRead(long accountId, CountDownLatch readerHasReadOnce, CountDownLatch writerCommitted) {
        int first = jdbcTemplate.queryForObject(SELECT_BALANCE, Integer.class, accountId);
        log.info("[REPEATABLE_READ] T1 first read balance={}", first);
        readerHasReadOnce.countDown();
        awaitLatch(writerCommitted);
        int second = jdbcTemplate.queryForObject(SELECT_BALANCE, Integer.class, accountId);
        log.info("[REPEATABLE_READ] T1 second read balance={}", second);
        return new int[] {first, second};
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void updateBalanceAfterSignal(long accountId, int newBalance, CountDownLatch waitForReaderSignal, CountDownLatch doneSignal) {
        awaitLatch(waitForReaderSignal);
        jdbcTemplate.update(UPDATE_BALANCE, newBalance, accountId);
        log.info("T2 update çalıştı balance={} (henüz commit değil)", newBalance);
        // doneSignal'ı burada, metot dönmeden HEMEN ÖNCE countDown etmek yanıltıcı olurdu:
        // @Transactional'ın gerçek COMMIT'i, bu metot çağırana döndükten SONRA gerçekleşir.
        // afterCommit() kaydı, T1'in ikinci okumasının GERÇEK commit'ten önce serbest
        // kalmamasını garanti eder - aksi halde T1 hâlâ commit edilmemiş bir transaction'la
        // yarışabilir ve (READ_COMMITTED altında bile) bayat veri görebilir; bu, sıkı
        // zamanlamalı bir JUnit thread havuzunda genelde fark edilmez ama Tomcat worker
        // thread'leri + taze oluşturulmuş bir executor gibi farklı zamanlama koşullarında
        // güvenilir şekilde yanlış sonuç üretebilir.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("T2 gerçekten commit oldu balance={}", newBalance);
                doneSignal.countDown();
            }
        });
    }

    // ---------- Hayalet okuma (Phantom read) ----------

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int[] countAboveTwiceUnderReadCommitted(int threshold, CountDownLatch readerHasCountedOnce, CountDownLatch writerCommitted) {
        int first = jdbcTemplate.queryForObject(COUNT_ABOVE, Integer.class, threshold);
        readerHasCountedOnce.countDown();
        awaitLatch(writerCommitted);
        int second = jdbcTemplate.queryForObject(COUNT_ABOVE, Integer.class, threshold);
        return new int[] {first, second};
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public int[] countAboveTwiceUnderRepeatableRead(int threshold, CountDownLatch readerHasCountedOnce, CountDownLatch writerCommitted) {
        int first = jdbcTemplate.queryForObject(COUNT_ABOVE, Integer.class, threshold);
        readerHasCountedOnce.countDown();
        awaitLatch(writerCommitted);
        int second = jdbcTemplate.queryForObject(COUNT_ABOVE, Integer.class, threshold);
        return new int[] {first, second};
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void insertRowAfterSignal(long newId, int balance, CountDownLatch waitForReaderSignal, CountDownLatch doneSignal) {
        awaitLatch(waitForReaderSignal);
        jdbcTemplate.update("insert into lab_isolation_account(id, balance) values (?, ?)", newId, balance);
        // bkz. updateBalanceAfterSignal'daki not: doneSignal, gerçek COMMIT'ten SONRA
        // countDown edilmeli, metot dönmeden hemen önce değil.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                doneSignal.countDown();
            }
        });
    }

    // ---------- Kirli okuma (Dirty read) (Postgres, READ_UNCOMMITTED'ı READ_COMMITTED'a yükseltir) ----------

    @Transactional(isolation = Isolation.READ_UNCOMMITTED)
    public int readUnderReadUncommittedWhileOtherTxUncommitted(long accountId, CountDownLatch writerHasWrittenUncommitted) {
        awaitLatch(writerHasWrittenUncommitted);
        int value = jdbcTemplate.queryForObject(SELECT_BALANCE, Integer.class, accountId);
        log.info("[READ_UNCOMMITTED] T2 read balance={} while T1's write was still uncommitted", value);
        return value;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void writeWithoutCommittingThenWait(long accountId, int uncommittedBalance, CountDownLatch writerHasWrittenUncommitted,
                                                CountDownLatch readerHasReadSignal) {
        jdbcTemplate.update(UPDATE_BALANCE, uncommittedBalance, accountId);
        writerHasWrittenUncommitted.countDown();
        awaitLatch(readerHasReadSignal);
        // bu metot geri döndüğünde (gerçekten) commit edilir
    }

    // ---------- Sadece izolasyon seviyesiyle önlenen kayıp güncelleme (Lost update, @Version olmadan) ----------

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void readModifyWriteUnderRepeatableRead(long accountId, int delta, CountDownLatch bothHaveRead, CountDownLatch proceedToWrite) {
        int current = jdbcTemplate.queryForObject(SELECT_BALANCE, Integer.class, accountId);
        bothHaveRead.countDown();
        awaitLatch(proceedToWrite);
        jdbcTemplate.update(UPDATE_BALANCE, current + delta, accountId);
        // commit, metot döndüğünde gerçekleşir - Postgres bunu bir serialization failure ile reddedebilir
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for the other transaction");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
