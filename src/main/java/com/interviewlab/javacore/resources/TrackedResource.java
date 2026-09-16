package com.interviewlab.javacore.resources;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gerçek bir dosya/socket/DB connection'ın yerini tutan, sahte bir {@link AutoCloseable}
 * kaynak - kaç tanesinin gerçekten kapatıldığını saymamızı sağlar.
 */
public class TrackedResource implements AutoCloseable {

    private static final AtomicInteger OPEN_COUNT = new AtomicInteger();
    private static final AtomicInteger CLOSED_COUNT = new AtomicInteger();

    public TrackedResource() {
        OPEN_COUNT.incrementAndGet();
    }

    public void doWork(boolean shouldFail) {
        if (shouldFail) {
            throw new IllegalStateException("simulated failure while resource is open");
        }
    }

    @Override
    public void close() {
        CLOSED_COUNT.incrementAndGet();
    }

    public static int openCount() {
        return OPEN_COUNT.get();
    }

    public static int closedCount() {
        return CLOSED_COUNT.get();
    }

    public static void reset() {
        OPEN_COUNT.set(0);
        CLOSED_COUNT.set(0);
    }
}
