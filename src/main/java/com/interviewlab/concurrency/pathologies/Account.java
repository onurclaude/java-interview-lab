package com.interviewlab.concurrency.pathologies;

/** Deadlock kötü/iyi örnekleri arasında paylaşılan basit bellek içi (in-memory) hesap. */
public final class Account {
    public final String id;
    public double balance;

    public Account(String id, double balance) {
        this.id = id;
        this.balance = balance;
    }
}
