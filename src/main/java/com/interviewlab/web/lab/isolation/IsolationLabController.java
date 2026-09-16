package com.interviewlab.web.lab.isolation;

import com.interviewlab.transaction.isolation.IsolationAnomalyLab;
import com.interviewlab.web.lab.LabLog;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/INTERACTIVE_LABS.md — LAB 06. Aynı senaryoyu (T1 iki kere okur, arada T2 commit eder)
 * iki farklı izolasyon seviyesi altında çalıştırıp PostgreSQL'in gerçek MVCC davranışını
 * karşılaştırmak için - bkz. docs/isolation.md. Bilinçli olarak JPA yerine ham JdbcTemplate
 * kullanır (nedeni için {@code IsolationAccount} javadoc'una bakın).
 */
@RestController
@RequestMapping("/api/labs/isolation")
public class IsolationLabController {

    private static final long ACCOUNT_ID = 1L;
    private static final int INITIAL_BALANCE = 100;
    private static final int UPDATED_BALANCE = 200;

    private final IsolationAnomalyLab isolationAnomalyLab;
    private final JdbcTemplate jdbcTemplate;

    public IsolationLabController(IsolationAnomalyLab isolationAnomalyLab, JdbcTemplate jdbcTemplate) {
        this.isolationAnomalyLab = isolationAnomalyLab;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        jdbcTemplate.update("delete from lab_isolation_account");
        jdbcTemplate.update("insert into lab_isolation_account(id, balance) values (?, ?)", ACCOUNT_ID, INITIAL_BALANCE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "ISOLATION");
        body.put("action", "RESET");
        body.put("accountId", ACCOUNT_ID);
        body.put("balance", INITIAL_BALANCE);
        body.put("nextStep", "POST /api/labs/isolation/non-repeatable-read/read-committed");
        return body;
    }

    @PostMapping("/non-repeatable-read/read-committed")
    public Map<String, Object> nonRepeatableReadUnderReadCommitted() throws Exception {
        return runNonRepeatableReadScenario("READ_COMMITTED", true);
    }

    @PostMapping("/non-repeatable-read/repeatable-read")
    public Map<String, Object> nonRepeatableReadUnderRepeatableRead() throws Exception {
        return runNonRepeatableReadScenario("REPEATABLE_READ", false);
    }

    private Map<String, Object> runNonRepeatableReadScenario(String isolation, boolean readCommitted) throws Exception {
        LabLog.banner("ISOLATION", isolation);
        jdbcTemplate.update("update lab_isolation_account set balance = ? where id = ?", INITIAL_BALANCE, ACCOUNT_ID);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readerHasReadOnce = new CountDownLatch(1);
        CountDownLatch writerCommitted = new CountDownLatch(1);
        try {
            Future<int[]> readerResult = readCommitted
                    ? executor.submit(() -> isolationAnomalyLab.readTwiceUnderReadCommitted(ACCOUNT_ID, readerHasReadOnce, writerCommitted))
                    : executor.submit(() -> isolationAnomalyLab.readTwiceUnderRepeatableRead(ACCOUNT_ID, readerHasReadOnce, writerCommitted));
            executor.submit(() -> isolationAnomalyLab.updateBalanceAfterSignal(ACCOUNT_ID, UPDATED_BALANCE, readerHasReadOnce, writerCommitted));

            int[] reads = readerResult.get(10, TimeUnit.SECONDS);
            boolean nonRepeatableReadObserved = reads[0] != reads[1];
            LabLog.line("T1 first read balance={}, T2 committed balance={}, T1 second read balance={}",
                    reads[0], UPDATED_BALANCE, reads[1]);
            LabLog.lesson(readCommitted
                    ? "READ_COMMITTED, her ifadede veriyi yeniden okur - T1'in ikinci okuması T2'nin commit'ini "
                    + "GÖRDÜ. Aynı transaction içinde aynı satırı iki kez okumak farklı sonuç verebilir."
                    : "PostgreSQL'de REPEATABLE_READ, transaction başında alınan TEK bir snapshot kullanır - "
                    + "T1'in ikinci okuması T2'nin commit'ini GÖRMEDİ, ilk okuduğu değeri gördü.");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("lab", "ISOLATION");
            body.put("isolation", isolation);
            body.put("transaction1FirstRead", reads[0]);
            body.put("transaction2UpdatedValue", UPDATED_BALANCE);
            body.put("transaction1SecondRead", reads[1]);
            body.put("observed", nonRepeatableReadObserved ? "NON_REPEATABLE_READ" : "SNAPSHOT_ISOLATION_PREVENTED_IT");
            body.put("lesson", readCommitted
                    ? "READ_COMMITTED her ifade için veriyi tazeler - non-repeatable read mümkündür."
                    : "Postgres'te REPEATABLE_READ tam bir transaction-başı snapshot alır - non-repeatable read engellenir.");
            return body;
        } finally {
            executor.shutdownNow();
        }
    }

    private static final long PHANTOM_NEW_ROW_ID = 2L;
    private static final int PHANTOM_THRESHOLD = 50;

    @PostMapping("/phantom-read/read-committed")
    public Map<String, Object> phantomReadUnderReadCommitted() throws Exception {
        return runPhantomReadScenario("READ_COMMITTED", true);
    }

    @PostMapping("/phantom-read/repeatable-read")
    public Map<String, Object> phantomReadUnderRepeatableRead() throws Exception {
        return runPhantomReadScenario("REPEATABLE_READ", false);
    }

    private Map<String, Object> runPhantomReadScenario(String isolation, boolean readCommitted) throws Exception {
        LabLog.banner("ISOLATION", isolation + " — PHANTOM READ (COUNT sorgusu)");
        jdbcTemplate.update("delete from lab_isolation_account where id = ?", PHANTOM_NEW_ROW_ID);
        jdbcTemplate.update("update lab_isolation_account set balance = ? where id = ?", INITIAL_BALANCE, ACCOUNT_ID);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readerHasCountedOnce = new CountDownLatch(1);
        CountDownLatch writerCommitted = new CountDownLatch(1);
        try {
            Future<int[]> readerResult = readCommitted
                    ? executor.submit(() -> isolationAnomalyLab.countAboveTwiceUnderReadCommitted(PHANTOM_THRESHOLD, readerHasCountedOnce, writerCommitted))
                    : executor.submit(() -> isolationAnomalyLab.countAboveTwiceUnderRepeatableRead(PHANTOM_THRESHOLD, readerHasCountedOnce, writerCommitted));
            executor.submit(() -> isolationAnomalyLab.insertRowAfterSignal(PHANTOM_NEW_ROW_ID, UPDATED_BALANCE, readerHasCountedOnce, writerCommitted));

            int[] counts = readerResult.get(10, TimeUnit.SECONDS);
            boolean phantomRowObserved = counts[0] != counts[1];
            LabLog.line("T1 first COUNT(balance>{})={}, T2 inserted a new qualifying row and committed, T1 second COUNT={}",
                    PHANTOM_THRESHOLD, counts[0], counts[1]);
            LabLog.lesson(readCommitted
                    ? "READ_COMMITTED, her ifadede veriyi yeniden okur - T2'nin YENİ eklediği satır T1'in ikinci "
                    + "COUNT'unda GÖRÜNDÜ (phantom read)."
                    : "PostgreSQL'de REPEATABLE_READ, transaction başında alınan TEK bir snapshot kullanır - T2'nin "
                    + "YENİ satırı T1'in ikinci COUNT'unda GÖRÜNMEDİ (standart SQL'in aksine, Postgres'in "
                    + "REPEATABLE_READ'i phantom read'i de engeller).");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("lab", "ISOLATION");
            body.put("isolation", isolation);
            body.put("threshold", PHANTOM_THRESHOLD);
            body.put("transaction1FirstCount", counts[0]);
            body.put("transaction2InsertedRow", true);
            body.put("transaction1SecondCount", counts[1]);
            body.put("observed", phantomRowObserved ? "PHANTOM_READ" : "SNAPSHOT_ISOLATION_PREVENTED_IT");
            body.put("lesson", readCommitted
                    ? "READ_COMMITTED altında T2'nin yeni eklediği satır T1'in ikinci COUNT'unda göründü."
                    : "Postgres'te REPEATABLE_READ phantom read'i de engeller (standart SQL tanımından daha güçlü).");
            return body;
        } finally {
            executor.shutdownNow();
        }
    }

    @PostMapping("/dirty-read/read-uncommitted")
    public Map<String, Object> dirtyReadUnderReadUncommitted() throws Exception {
        LabLog.banner("ISOLATION", "READ_UNCOMMITTED — KİRLİ OKUMA (Postgres bunu izin VERMEZ)");
        jdbcTemplate.update("update lab_isolation_account set balance = ? where id = ?", INITIAL_BALANCE, ACCOUNT_ID);
        int uncommittedBalance = 999;

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch writerHasWrittenUncommitted = new CountDownLatch(1);
        CountDownLatch readerHasReadSignal = new CountDownLatch(1);
        try {
            Future<Void> writerResult = executor.submit(() -> {
                isolationAnomalyLab.writeWithoutCommittingThenWait(ACCOUNT_ID, uncommittedBalance, writerHasWrittenUncommitted, readerHasReadSignal);
                return null;
            });
            Future<Integer> readerResult = executor.submit(() ->
                    isolationAnomalyLab.readUnderReadUncommittedWhileOtherTxUncommitted(ACCOUNT_ID, writerHasWrittenUncommitted));

            int readValue = readerResult.get(10, TimeUnit.SECONDS);
            readerHasReadSignal.countDown();
            writerResult.get(10, TimeUnit.SECONDS);
            boolean dirtyReadObserved = readValue == uncommittedBalance;
            int finalCommittedBalance = jdbcTemplate.queryForObject(
                    "select balance from lab_isolation_account where id = ?", Integer.class, ACCOUNT_ID);
            LabLog.line("T2 requested READ_UNCOMMITTED, read balance={} while T1's write ({}) was still uncommitted; final committed balance={}",
                    readValue, uncommittedBalance, finalCommittedBalance);
            LabLog.lesson("PostgreSQL, ISOLATION.READ_UNCOMMITTED talebini SESSİZCE READ_COMMITTED'a yükseltir - "
                    + "Postgres'te KİRLİ OKUMA fiziksel olarak İMKANSIZDIR (MVCC her zaman commit edilmiş bir "
                    + "snapshot okur). Bu, 'READ_UNCOMMITTED istersem kirli okuma alırım' varsayımını ÇÜRÜTÜR.");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("lab", "ISOLATION");
            body.put("isolation", "READ_UNCOMMITTED (Postgres tarafından READ_COMMITTED'a yükseltilir)");
            body.put("uncommittedValueWrittenByT1", uncommittedBalance);
            body.put("valueReadByT2WhileT1Uncommitted", readValue);
            body.put("dirtyReadObserved", dirtyReadObserved);
            body.put("finalCommittedBalance", finalCommittedBalance);
            body.put("lesson", "Postgres READ_UNCOMMITTED'ı READ_COMMITTED'a yükseltir - kirli okuma HİÇBİR ZAMAN gözlemlenmedi.");
            return body;
        } finally {
            executor.shutdownNow();
        }
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "ISOLATION");
        body.put("rows", jdbcTemplate.queryForList("select * from lab_isolation_account order by id"));
        body.put("dbeaverQuery", "SELECT * FROM lab_isolation_account;");
        return body;
    }
}
