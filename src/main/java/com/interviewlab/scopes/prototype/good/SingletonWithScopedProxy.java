package com.interviewlab.scopes.prototype.good;

import com.interviewlab.scopes.prototype.ScopedProxyPrototypeWorker;
import org.springframework.stereotype.Service;

/**
 * Hatalı olan
 * {@link com.interviewlab.scopes.prototype.bad.SingletonWithDirectPrototypeInjection} ile
 * BİREBİR AYNI görünür - sıradan constructor injection, bir alan, bir getter - ama doğru
 * çalışır, çünkü {@link ScopedProxyPrototypeWorker} {@code proxyMode = TARGET_CLASS} olarak
 * tanımlanmıştır. Buradaki alan gerçek bir {@code ScopedProxyPrototypeWorker} tutmaz; ona
 * benzeyen bir CGLIB proxy tutar. {@code worker.getId()} çağrısının her biri bu proxy
 * tarafından yakalanır; proxy container'dan yepyeni bir prototype instance getirir, ona
 * delege eder, SONRA onu atar. {@link SingletonWithObjectProvider}'dan farkı tamamen
 * ergonomiktir: bu, çağrı başına küçük bir proxy overhead'i ve hedef sınıf için CGLIB
 * proxying'in uygulanabilir olması gerekliliği pahasına, sıradan bir alan erişimi gibi okunur.
 */
@Service
public class SingletonWithScopedProxy {

    private final ScopedProxyPrototypeWorker worker;

    public SingletonWithScopedProxy(ScopedProxyPrototypeWorker worker) {
        this.worker = worker; // aslında bir scoped proxy, gerçek bir instance değil
    }

    public String getWorkerId() {
        return worker.getId(); // her çağrıda taze bir gerçek instance'a delege eder
    }
}
