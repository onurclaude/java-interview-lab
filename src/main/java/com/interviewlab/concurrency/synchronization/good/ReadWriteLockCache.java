package com.interviewlab.concurrency.synchronization.good;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Sade bir {@link ReentrantLock} ya da {@code synchronized} yerine NEDEN
 * {@link ReentrantReadWriteLock} kullanılır? İkisi de, okumalar dahil <em>her</em> erişimi
 * seri hale getirir - okumalar nadirse sorun değildir, ama okumalar yazmalardan çok daha
 * fazlaysa (tipik bir cache senaryosu) israftır. Bir okuma-yazma kilidi, herhangi sayıda
 * okuyucunun okuma kilidini aynı anda tutmasına izin verir; yalnızca bir yazıcının özel
 * (exclusive) erişime ihtiyacı vardır ve bir yazıcı hem okuyucuları hem diğer yazıcıları
 * dışlar. {@code ReadWriteLockContentionTest}, burada birden fazla okuyucunun gerçekten
 * eşzamanlı çalıştığını ve bir yazıcının onlar bitene kadar bloklandığını kanıtlar.
 */
public class ReadWriteLockCache {

    private final Map<String, String> data = new HashMap<>();
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    public String get(String key) {
        rwLock.readLock().lock();
        try {
            return data.get(key);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void put(String key, String value) {
        rwLock.writeLock().lock();
        try {
            data.put(key, value);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /** Test kancası (hook): {@code onLocked} dönene kadar okuma kilidini tutar, böylece bir test eşzamanlılığı ölçebilir. */
    public void withReadLockHeld(Runnable onLocked) {
        rwLock.readLock().lock();
        try {
            onLocked.run();
        } finally {
            rwLock.readLock().unlock();
        }
    }
}
