package com.interviewlab.aop;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    @Around("@annotation(com.interviewlab.aop.TrackExecutionTime)")
    public Object trackExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
            String signature = joinPoint.getSignature().toShortString();
            lastDurationsMillis.put(signature, elapsedMillis);
            log.info("TrackExecutionTime {} took {}ms", signature, elapsedMillis);
        }
    }

    public Long lastDurationMillis(String methodShortSignature) {
        return lastDurationsMillis.get(methodShortSignature);
    }
}
