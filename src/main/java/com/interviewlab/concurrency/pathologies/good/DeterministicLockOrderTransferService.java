package com.interviewlab.concurrency.pathologies.good;

import com.interviewlab.concurrency.pathologies.Account;

/**
 * {@link com.interviewlab.concurrency.pathologies.bad.InconsistentLockOrderTransferService}'in
 * doğru karşılığı: her iki hesap da - burada, sabit (stable) bir tanımlayıcı
 * karşılaştırılarak - çağıranın hangisini "from", hangisini "to" olarak adlandırdığından
 * bağımsız olarak her zaman aynı sırayla kilitlenir. Zıt yönlerde transfer yapan iki thread
 * artık bir döngü oluşturmak yerine sadece aynı ilk kilit için sıraya girer.
 */
public class DeterministicLockOrderTransferService {

    public void transfer(Account from, Account to, double amount) {
        Account first = from.id.compareTo(to.id) <= 0 ? from : to;
        Account second = (first == from) ? to : from;

        synchronized (first) {
            synchronized (second) {
                from.balance -= amount;
                to.balance += amount;
            }
        }
    }
}
