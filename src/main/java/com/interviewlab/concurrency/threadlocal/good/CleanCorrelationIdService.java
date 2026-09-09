package com.interviewlab.concurrency.threadlocal.good;

/**
 * {@link com.interviewlab.concurrency.threadlocal.bad.LeakyCorrelationIdService}'in doğru
 * karşılığı: {@link #runWithCorrelationId}, {@link ThreadLocal}'ı her zaman bir
 * {@code finally} bloğunda temizler, bu yüzden havuzlanmış bir thread hiçbir zaman bir
 * görevden diğerine durum taşımaz.
 */
public class CleanCorrelationIdService {

    private static final ThreadLocal<String> CORRELATION_ID = new ThreadLocal<>();

    public <T> T runWithCorrelationId(String id, java.util.function.Supplier<T> task) {
        CORRELATION_ID.set(id);
        try {
            return task.get();
        } finally {
            CORRELATION_ID.remove();
        }
    }

    public String getCorrelationId() {
        return CORRELATION_ID.get();
    }
}
