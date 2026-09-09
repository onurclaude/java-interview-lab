package com.interviewlab.patterns.proxy;

import java.lang.reflect.Proxy;
import java.util.function.Consumer;

/**
 * Gerçek bir örneğe delege etmeden önce her çağrıyı loglayan, {@link Greeter}'ı implemente
 * eden düz bir {@link Proxy} oluşturur - bu projedeki {@code @Transactional}, {@code @Async},
 * {@code @Cacheable} ve her {@code @Aspect}'in tam olarak dayandığı şeyin minimal, sıfırdan
 * yazılmış bir versiyonudur: Spring, sınıfınızın bytecode'unu değiştirmez, onun yerine size
 * (ya da bağımlılarınıza autowire ederek) onun yerine geçen bir PROXY nesnesi verir. Bu
 * proxy aynı arayüzü implemente eder (buradaki gibi JDK dynamic proxy) ya da aynı sınıfı
 * extend eder (arayüz yokken CGLIB), her çağrıyı intercept eder, kendi mantığını çalıştırır
 * (bir transaction başlatmak, bir executor'a submit etmek, bir cache kontrol etmek, çağrıyı
 * zamanlamak) ve ardından gerçek nesneye çağrıyı yönlendirip yönlendirmeyeceğine karar verir
 * - gerçek nesnenin içinden `this` üzerinden bir metot çağırmanın tüm bunları neden
 * atladığının nedeni de tam olarak budur: `this`, proxy değil gerçek nesnedir. Spring'in
 * kendi proxy'lerindeki bu hata modu için {@code transaction.propagation.bad.SelfInvocationPaymentService},
 * {@code async.spring.bad.SelfInvocationNotificationService} ve
 * {@code aop.bad.SelfInvocationTimingService} sınıflarına bakın.
 */
public final class GreeterProxyFactory {

    private GreeterProxyFactory() {
    }

    public static Greeter withLogging(Greeter target, Consumer<String> logSink) {
        return (Greeter) Proxy.newProxyInstance(
                Greeter.class.getClassLoader(),
                new Class<?>[] {Greeter.class},
                (proxy, method, args) -> {
                    logSink.accept("before:" + method.getName());
                    Object result = method.invoke(target, args);
                    logSink.accept("after:" + method.getName());
                    return result;
                });
    }
}
