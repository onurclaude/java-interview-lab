package com.interviewlab.common.sql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Hibernate'in driver'a gerçekten gönderdiği her SQL ifadesini, onu gönderen thread'e göre
 * gruplayarak kaydeder.
 *
 * <p><b>Bu NEDEN var?</b> Bu projenin tamamının tezi şudur: "bana güvenme, SQL logunu oku".
 * "{@code save()} hemen bir INSERT gönderir mi?" ya da "optimistic locking gerçekten
 * WHERE cümlesine {@code AND version = ?} ekler mi?" gibi sorular bir yoruma güvenilerek
 * değil, gerçek ifade metnine karşı yapılan bir assertion ile cevaplanmalıdır. Thread adına
 * göre gruplama (tek, düz bir liste yerine), eşzamanlılık laboratuvarlarını (optimistic/
 * pessimistic locking, deadlock) okunabilir kılan şeydir: T1'in worker thread'i ve T2'nin
 * worker thread'i her biri kendi ifade listesini biriktirir, böylece bir test T1 arada commit
 * etmiş olsa bile "T2'nin UPDATE'i version=1 taşıyordu" şeklinde assertion yapabilir.
 *
 * <p>{@link com.interviewlab.common.config.SqlObservabilityConfig} üzerinden bir kez, global
 * olarak kaydedilir - ucuzdur (bellek içi bir liste ekleme işlemidir), bu yüzden her zaman
 * açık bırakmanın bir maliyeti yoktur; her ilgisiz testin konsolunu dolduracak olan ör.
 * {@code hibernate.show_sql}'in aksine.
 */
public final class SqlStatementRecorder implements StatementInspector {

    private static final Map<String, List<String>> STATEMENTS_BY_THREAD = new ConcurrentHashMap<>();
    private static final AtomicInteger TOTAL_COUNT = new AtomicInteger();

    @Override
    public String inspect(String sql) {
        STATEMENTS_BY_THREAD
                .computeIfAbsent(Thread.currentThread().getName(), t -> Collections.synchronizedList(new ArrayList<>()))
                .add(sql);
        TOTAL_COUNT.incrementAndGet();
        return sql;
    }

    /** Son {@link #clear()} çağrısından bu yana çağıran thread tarafından gönderilen ifadeler. */
    public static List<String> statementsForCurrentThread() {
        return statementsForThread(Thread.currentThread().getName());
    }

    public static List<String> statementsForThread(String threadName) {
        return List.copyOf(STATEMENTS_BY_THREAD.getOrDefault(threadName, List.of()));
    }

    public static List<String> allStatements() {
        return STATEMENTS_BY_THREAD.values().stream().flatMap(List::stream).toList();
    }

    public static int totalCount() {
        return TOTAL_COUNT.get();
    }

    public static long countMatching(String threadName, String sqlKeywordUpperCase) {
        return statementsForThread(threadName).stream()
                .filter(s -> s.toUpperCase().contains(sqlKeywordUpperCase.toUpperCase()))
                .count();
    }

    /** Assertion'ların yalnızca mevcut testten gelen ifadeleri görmesi için {@code @BeforeEach}'ten çağırın. */
    public static void clear() {
        STATEMENTS_BY_THREAD.clear();
        TOTAL_COUNT.set(0);
    }
}
