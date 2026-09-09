package com.interviewlab.concurrency.race.good;

/**
 * {@link com.interviewlab.concurrency.race.bad.UnsafeBankAccount}'un doğru karşılığı: tüm
 * check-then-act dizisi tek bir intrinsic monitor altında çalışır, bu yüzden aynı örnekteki
 * (instance) diğer tüm {@code synchronized} metotlara göre atomiktir.
 */
public class SynchronizedBankAccount {

    private int balance;

    public SynchronizedBankAccount(int initialBalance) {
        this.balance = initialBalance;
    }

    public synchronized boolean withdraw(int amount) {
        if (balance >= amount) {
            Thread.yield(); // kötü versiyonla aynı yapay race penceresi - yine de güvenli
            balance -= amount;
            return true;
        }
        return false;
    }

    public synchronized int getBalance() {
        return balance;
    }
}
