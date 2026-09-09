package com.interviewlab.concurrency.synchronization.good;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * {@link com.interviewlab.concurrency.synchronization.bad.LockWithoutFinallyService} ile
 * karşılaştırılan, doğru {@link ReentrantLock} kullanımı.
 *
 * <p><b>{@code synchronized} yerine hiç ReentrantLock'a başvurmanın nedeni ne?</b> Intrinsic
 * monitor'ün sunmadığı yetenekler sunar: {@link #incrementOrGiveUp}, sonsuza kadar bloklamak
 * yerine geri çekilebilir ({@code tryLock}, zaman aşımlı ya da zaman aşımsız);
 * {@link #incrementInterruptibly}, kilidi beklerken kesintiye uğratılabilir
 * ({@code lockInterruptibly}, iptal edilebilir görevler için kullanışlıdır); ve
 * {@code new ReentrantLock(true)} constructor'ı üzerinden, verimlilik (throughput) yerine en
 * uzun süredir bekleyen thread'i önceliklendiren bir adillik (fairness) politikası talep
 * edilebilir - bkz. {@link #FAIR_LOCK_EXAMPLE}. Bunlara yalnızca ihtiyaç duyduğunda başvur:
 * sade {@code synchronized}, yaygın durumlar için daha basit ve biraz daha ucuzdur.
 */
public class ReentrantLockCounter {

    /** Adillik (fairness), FIFO'ya yakın bir alım sırası karşılığında verimlilikten (throughput) fedakarlık eder; pratikte nadiren gereklidir. */
    public static final ReentrantLock FAIR_LOCK_EXAMPLE = new ReentrantLock(true);

    private final ReentrantLock lock = new ReentrantLock();
    private int count;

    public void increment() {
        lock.lock();
        try {
            count++;
        } finally {
            lock.unlock();
        }
    }

    /** Kilit zaten tutuluyorsa sonsuza kadar bloklamak yerine false döner. */
    public boolean incrementOrGiveUp(long timeout, TimeUnit unit) throws InterruptedException {
        if (!lock.tryLock(timeout, unit)) {
            return false;
        }
        try {
            count++;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** synchronized'ın aksine, kilit beklenirken Thread.interrupt() ile uyandırılabilir. */
    public void incrementInterruptibly() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            count++;
        } finally {
            lock.unlock();
        }
    }

    public int get() {
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }
}
