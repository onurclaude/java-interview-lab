package com.interviewlab.scopes.prototype;

import java.util.UUID;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

/**
 * {@link PrototypeWorker} ile aynı fikir, ama {@code proxyMode = TARGET_CLASS} olarak
 * tanımlanmıştır: Spring, bu sınıfın yerine geçen bir CGLIB proxy inject eder ve o proxy
 * üzerindeki her metot çağrısı, delege etmeden önce container'dan şeffaf bir şekilde taze bir
 * prototype instance getirir - bunun sıradan constructor injection'ın bir prototype
 * dependency için neden doğru çalışmasını sağladığını görmek için bkz.
 * {@link com.interviewlab.scopes.prototype.good.SingletonWithScopedProxy}.
 */
@Component
@Scope(value = "prototype", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class ScopedProxyPrototypeWorker {

    private final String id = UUID.randomUUID().toString();

    public String getId() {
        return id;
    }
}
