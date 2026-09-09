# Spring AOP: Özel Annotation + @Around

Kod: `com.interviewlab.aop.*` — Testler: `AopSelfInvocationTest`

# Problem

Her metodun business logic'ine dokunmadan, annotation'lı herhangi bir metodun ne kadar
sürdüğünü ölçmek.

## Doğru Kod

```java
// com.interviewlab.aop.TrackExecutionTime — the pointcut marker
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface TrackExecutionTime { }

// com.interviewlab.aop.ExecutionTimeAspect
@Around("@annotation(com.interviewlab.aop.TrackExecutionTime)")
public Object trackExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
    long start = System.nanoTime();
    try {
        return joinPoint.proceed();
    } finally {
        log.info("{} took {}ms", joinPoint.getSignature().toShortString(), elapsedMillis);
    }
}
```

**Neden `@Before`/`@After` değil de `@Around`?** Sadece `@Around`, çağrının hem bir
açıklamasını hem de onu tetiklemek için kullanılan handle'ı (`proceed()`) içeren bir
`ProceedingJoinPoint` alır. Bu sayede tek bir advice, before ile after ARASINDAKİ süreyi
ölçebilir ve isterse dönüş değerini veya exception'ı inceleyip değiştirebilir.

**Pointcut, annotation'ın KENDİSİDİR**: `@annotation(TrackExecutionTime)`, hangi sınıfta
olursa olsun `@TrackExecutionTime` taşıyan her metotla eşleşir — metotlar, bu aspect'in
belirli sınıf/paketleri isimlendirmesi yerine annotation'ı ekleyerek buna dahil olmayı seçer.

## Hatalı Kod — yine self-invocation

```java
// com.interviewlab.aop.bad.SelfInvocationTimingService
public void processOrder() {
    this.slowStep(); // bypasses the aspect's proxy entirely
}

@TrackExecutionTime
public void slowStep() { ... }
```

`@Transactional` (docs/transactions.md) ve `@Async` (docs/async.md) ile aynı kök neden: her
`@Aspect` de proxy tabanlıdır. `shouldNotTrackExecutionTimeDuringSelfInvocation()`, aspect'in
self-invocation ile yapılan çağrı için hiçbir süre kaydetmediğini gösterir.

## Doğru Kod — ayrı bean

Süresi ölçülen metodu `SlowStepService`'e taşıyıp `OrderProcessingService`'ten inject edilen
bir referans üzerinden çağırın — `shouldTrackExecutionTimeWhenCalledThroughARealProxy()`,
aspect'in gerçek bir süre kaydettiğini gösterir.

## Neden Cross-Cutting Concern, Business Logic Değil

AOP, bir metodun yaptığı işten BAĞIMSIZ olan concern'ler (timing, auditing, security
kontrolleri) içindir; bunlar olmasa, isteyen her metoda kopyala-yapıştır yapmak gerekirdi.
Business logic ise metodun kendisinde, sade ve okunabilir kodla yer almalıdır — temel iş
kurallarını bir aspect'in içine gizlemek, onları çağrı noktasında görünmez ve takip edilmesi
zor hale getirir; bu da AOP'yi cross-cutting concern'ler için değerli kılan şeyin tam tersidir.

## Sık Sorulan Mülakat Soruları

- **Q:** `@Around` diğer advice tiplerinden ne zaman tercih edilir? — Metodun süresini
  ölçmek, dönüş değerini değiştirmek veya exception'ı yönetmek gerektiğinde -
  `ProceedingJoinPoint.proceed()`'e ihtiyaç duyulduğunda.
- **Q:** AOP self-invocation'dan neden etkilenir? — Çünkü proxy tabanlıdır; `this.` çağrısı
  proxy'yi atlar.
- **Q:** AOP hangi durumlarda kullanılmamalı? — Business logic için; sadece cross-cutting
  concern'ler için (logging, timing, audit, security).

## 30 Saniyelik Mülakat Cevabı

"Custom bir @TrackExecutionTime annotation'ı ve @Around aspect'i yazdım. Self-invocation'ı
burada da test ettim: aynı sınıf içinden `this.slowStep()` çağırınca aspect hiç devreye
girmedi, süre kaydedilmedi. Metodu ayrı bir bean'e taşıyıp gerçek proxy üzerinden çağırınca
aspect doğru şekilde çalıştı ve süreyi logladı - @Transactional ve @Async'te gördüğüm aynı
proxy sınırlaması burada da geçerliydi."

## Takip Soruları

- `@Before`/`@After`/`@AfterReturning`/`@AfterThrowing` ne zaman `@Around` yerine yeterlidir?
- CGLIB proxy ile JDK dynamic proxy arasındaki fark AOP'yi nasıl etkiler?
