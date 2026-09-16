package com.interviewlab.transaction.isolation;

import com.interviewlab.labrunner.spring.SpringLabRunnerSupport;

import com.interviewlab.labrunner.LabRunnerPrint;
import com.interviewlab.transaction.isolation.IsolationAnomalyLab;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;

/** IntelliJ'de sağ tık -> Run/Debug. Postman/HTTP GEREKMEZ. `docker compose up -d` GEREKİR. */
public final class IsolationSpringLabRunner {

    private static final long ACCOUNT_ID = 1L;

    public static void main(String[] args) throws Exception {
        SpringLabRunnerSupport.run(ctx -> {
            try {
                run(ctx);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void run(org.springframework.context.ConfigurableApplicationContext ctx) throws Exception {
        JdbcTemplate jdbcTemplate = ctx.getBean(JdbcTemplate.class);
        IsolationAnomalyLab lab = ctx.getBean(IsolationAnomalyLab.class);

        jdbcTemplate.update("delete from lab_isolation_account");
        jdbcTemplate.update("insert into lab_isolation_account(id, balance) values (?, ?)", ACCOUNT_ID, 100);

        LabRunnerPrint.banner("ISOLATION — non-repeatable read (READ_COMMITTED vs REPEATABLE_READ)");
        nonRepeatableReadDemo(jdbcTemplate, lab, true);
        nonRepeatableReadDemo(jdbcTemplate, lab, false);

        LabRunnerPrint.banner("ISOLATION — phantom read");
        jdbcTemplate.update("delete from lab_isolation_account where id = 2");
        jdbcTemplate.update("update lab_isolation_account set balance = 100 where id = ?", ACCOUNT_ID);
        phantomReadDemo(jdbcTemplate, lab, true);
        jdbcTemplate.update("delete from lab_isolation_account where id = 2");
        phantomReadDemo(jdbcTemplate, lab, false);

        LabRunnerPrint.banner("ISOLATION — dirty read (Postgres READ_UNCOMMITTED'ı yükseltir)");
        jdbcTemplate.update("update lab_isolation_account set balance = 100 where id = ?", ACCOUNT_ID);
        dirtyReadDemo(lab);

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("Postgres'te REPEATABLE_READ, transaction-başı TEK bir snapshot ile hem non-repeatable");
        LabRunnerPrint.line("read'i HEM phantom read'i engeller. READ_UNCOMMITTED, Postgres tarafından sessizce");
        LabRunnerPrint.line("READ_COMMITTED'a yükseltilir - kirli okuma FİZİKSEL OLARAK MÜMKÜN DEĞİLDİR.");
    }

    private static void nonRepeatableReadDemo(JdbcTemplate jdbcTemplate, IsolationAnomalyLab lab, boolean readCommitted) throws Exception {
        jdbcTemplate.update("update lab_isolation_account set balance = 100 where id = ?", ACCOUNT_ID);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch committed = new CountDownLatch(1);
        try {
            var readerResult = readCommitted
                    ? executor.submit(() -> lab.readTwiceUnderReadCommitted(ACCOUNT_ID, ready, committed)) // <- BREAKPOINT 1: 2. read
                    : executor.submit(() -> lab.readTwiceUnderRepeatableRead(ACCOUNT_ID, ready, committed)); // <- BREAKPOINT 2: 2. read
            executor.submit(() -> lab.updateBalanceAfterSignal(ACCOUNT_ID, 200, ready, committed));
            int[] reads = readerResult.get(10, TimeUnit.SECONDS);
            LabRunnerPrint.fact((readCommitted ? "READ_COMMITTED" : "REPEATABLE_READ") + " firstRead/secondRead",
                    reads[0] + "/" + reads[1]);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void phantomReadDemo(JdbcTemplate jdbcTemplate, IsolationAnomalyLab lab, boolean readCommitted) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch committed = new CountDownLatch(1);
        try {
            var readerResult = readCommitted
                    ? executor.submit(() -> lab.countAboveTwiceUnderReadCommitted(50, ready, committed))
                    : executor.submit(() -> lab.countAboveTwiceUnderRepeatableRead(50, ready, committed));
            executor.submit(() -> lab.insertRowAfterSignal(2L, 150, ready, committed)); // <- BREAKPOINT 3
            int[] counts = readerResult.get(10, TimeUnit.SECONDS);
            LabRunnerPrint.fact((readCommitted ? "READ_COMMITTED" : "REPEATABLE_READ") + " firstCount/secondCount",
                    counts[0] + "/" + counts[1]);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void dirtyReadDemo(IsolationAnomalyLab lab) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch writerHasWritten = new CountDownLatch(1);
        CountDownLatch readerHasRead = new CountDownLatch(1);
        try {
            var writerResult = executor.submit(() -> {
                lab.writeWithoutCommittingThenWait(ACCOUNT_ID, 999, writerHasWritten, readerHasRead); // <- BREAKPOINT 4
                return null;
            });
            var readerResult = executor.submit(() -> lab.readUnderReadUncommittedWhileOtherTxUncommitted(ACCOUNT_ID, writerHasWritten)); // <- BREAKPOINT 5
            int value = readerResult.get(10, TimeUnit.SECONDS);
            readerHasRead.countDown();
            writerResult.get(10, TimeUnit.SECONDS);
            LabRunnerPrint.fact("valueReadByT2WhileT1Uncommitted (100 olmalı, 999 DEĞİL)", value);
            LabRunnerPrint.fact("dirtyReadObserved", value == 999);
        } finally {
            executor.shutdownNow();
        }
    }
}
