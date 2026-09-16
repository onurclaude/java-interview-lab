package com.interviewlab.aop;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ders kitabı örneği bir cross-cutting concern: execution time ölçmenin herhangi bir tek
 * metodun business logic'iyle hiçbir ilgisi yoktur, herhangi bir metoda aynı şekilde
 * uygulanır ve aksi takdirde onu isteyen her metoda kopyala-yapıştır yapılması (veya
 * sarmalanması) gerekirdi. AOP'nin tam olarak amacı budur - {@code @Around} advice'ı gerçek
 * metottan ÖNCE VE SONRA çalışır, onu tamamen sarar.
 *
 * <p><b>NEDEN {@code @Before}/{@code @After} değil de {@code @Around}?</b> Yalnızca
 * {@code @Around}, hem yakalanan çağrının bir tanımı HEM DE onu {@code proceed()} ile
 * gerçekten çağırmak için kullanılan handle olan bir {@link ProceedingJoinPoint} alır. Bu,
 * tek bir advice parçasında önce ile sonra ARASINDAKİ süreyi ölçmeyi ve dönüş değerini /
 * exception'ı gözlemlemeyi (hatta değiştirmeyi) mümkün kılan şeydir.
 *
 * <p><b>Pointcut</b> olan {@code @annotation(TrackExecutionTime)}, herhangi bir sınıf
 * üzerinde {@link TrackExecutionTime} ile işaretlenmiş herhangi bir metot çağrısıyla eşleşir
 * - annotation'ın kendisi pointcut'tur, bu yüzden metotlar bu aspect'in sınıf/paket
 * belirtmesi yerine annotation'ı ekleyerek dahil olur.
 *
 * <p><b>Bunun neden yalnızca Spring proxy'sinden geçen çağrılarda çalıştığı:</b> diğer her
 * {@code @Aspect} gibi, bu da hedef bean'i saran bir proxy olarak uygulanır. Bir
 * self-invocation (bir bean'in kendi {@code @TrackExecutionTime} metodunu
 * {@code this.foo()} üzerinden çağırması) hiçbir zaman proxy'ye ulaşmaz, bu yüzden hiçbir
 * zaman zamanlanmaz - bu projenin başka yerlerindeki {@code @Transactional} ve
 * {@code @Async} self-invocation sorunlarıyla tam olarak aynı kök neden; bkz.
 * {@link com.interviewlab.aop.bad.SelfInvocationTimingService}.
 */
@Aspect
@Component
public class ExecutionTimeAspect {

    private static final Logger log = LoggerFactory.getLogger(ExecutionTimeAspect.class);
    private final Map<String, Long> lastDurationsMillis = new ConcurrentHashMap<>();
    /**
     * Advice'ın GERÇEKTEN kaç kez çalıştığının sahte olmayan kanıtı - controller BUNU
     * hesaplamaz/varsaymaz, SADECE bu advice'ın kendisi, gerçekten çalıştığı ANDA artırır.
     * self-invocation senaryosunda bu sayaç HİÇ İLERLEMEZ, çünkü advice'ın kendisi HİÇ
     * ÇALIŞMAZ (proxy'ye hiç uğranmadı) - bu, "aspectIntercepted" alanının fabricate
     * edilmediğinin runtime kanıtıdır.
     */
    private final AtomicInteger interceptionCount = new AtomicInteger();

    @Around("@annotation(com.interviewlab.aop.TrackExecutionTime)")
    public Object trackExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        interceptionCount.incrementAndGet();
        long start = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
            String signature = joinPoint.getSignature().toShortString();
            lastDurationsMillis.put(signature, elapsedMillis);
            log.info("TrackExecutionTime {} took {}ms (interceptionCount={})", signature, elapsedMillis, interceptionCount.get());
        }
    }

    public Long lastDurationMillis(String methodShortSignature) {
        return lastDurationsMillis.get(methodShortSignature);
    }

    public int interceptionCount() {
        return interceptionCount.get();
    }

    public void resetInterceptionCount() {
        interceptionCount.set(0);
    }
}
