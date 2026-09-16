# Spring @Async

Kod: `com.interviewlab.async.spring.*`

**Birincil öğrenme arayüzü — Postman + IntelliJ debugger:**
```http
POST /api/labs/async/reset
POST /api/labs/async/bad
POST /api/labs/async/good
POST /api/labs/async/void-exception
```
`AsyncSelfInvocationTest`, AYNI davranışın otomatik regresyon kanıtıdır — ikincildir, birincil değil.

# Problem

`@Async`'in bir metodu arka planda çalıştırması bekleniyor. Hangi koşullar altında bunu
sessizce YAPMAZ?

## Hatalı Kod — self-invocation

```java
// com.interviewlab.async.spring.bad.SelfInvocationNotificationService
public String notifyUser(String message) {
    this.sendAsync(message); // SELF-INVOCATION
    return Thread.currentThread().getName();
}

@Async("labAsyncExecutor")
public void sendAsync(String message) { ... }
```

## Neden Yanlış

`@Async`, `@Transactional` (docs/transactions.md) ve herhangi bir `@Aspect` (docs/aop.md) ile
aynı Spring AOP proxy mekanizması üzerinden uygulanır. Bean'in kendi içinden kendi metoduna
yapılan bir çağrı asla proxy'den geçmez - `sendAsync()` sadece caller'ın kendi thread'inde,
senkron şekilde çalışır.

## Gerçekte Ne Oluyor

`shouldNotApplyAsyncDuringSelfInvocation()`: `sendAsync()`'in gövdesini çalıştıran thread,
caller ile TAMAMEN AYNI thread'dir. "Bu fire-and-forget'tir" diye varsayan kod, aslında tüm
gecikmeyi inline olarak öder.

## Doğru Kod

`@Async` metodunu farklı bir bean'e (`AsyncSender`) taşıyın ve inject edilen bir referans
üzerinden (`NotificationService`) çağırın —
`shouldRunOnDifferentThreadWhenCalledThroughARealProxy()`, thread isimlerinin farklı
olduğunu kanıtlar.

## Custom executor

`AsyncConfig`, `AsyncConfigurer`'ı implement eder ve isimlendirilmiş, sınırlı bir
`ThreadPoolExecutor` (`labAsyncExecutor`) sağlar. Yapılandırılmadan bırakılırsa,
`@EnableAsync`'in varsayılanı `SimpleAsyncTaskExecutor`'dır - her çağrı için yeni,
pool'lanmamış bir thread; bu da tam olarak
`concurrency.thread.bad.UncontrolledThreadCreationService`'teki "kontrolsüz thread
oluşturma" anti-pattern'idir, sadece bir annotation'ın arkasına gizlenmiştir.

## Exception yönetimi: void ile `CompletableFuture<T>`

- **`void` dönen `@Async` metod**: caller'ın inceleyebileceği bir `Future` yoktur - fırlatılan
  bir exception'ın gidebileceği tek yer, yapılandırılmış bir
  `AsyncUncaughtExceptionHandler`'dır
  (`shouldRouteVoidAsyncMethodFailuresToTheUncaughtExceptionHandler`). Yapılandırılmazsa
  Spring'in varsayılanı sadece loglar; buradaki `AsyncConfig` testlerin bunu assert
  edebilmesi için kaydeder.
- **`CompletableFuture<T>` dönen `@Async` metod**: Spring bunun yerine DÖNDÜRÜLEN future'ı
  exception ile tamamlar
  (`shouldCompleteFutureExceptionallyForCompletableFutureReturningAsyncMethod`) — caller,
  özel bir exception handler'a gerek kalmadan normal, incelenebilir bir başarısız future
  alır.

## Trade-off'lar

| Dönüş tipi | Hata görünürlüğü | Ne zaman kullanılır |
|---|---|---|
| `void` | Sadece `AsyncUncaughtExceptionHandler` üzerinden - fark edilmesi kolayca kaçabilir | Caller'ın sonucu gerçekten önemsemediği, gerçek fire-and-forget durumlar |
| `CompletableFuture<T>` | `.exceptionally`/`.handle`/`.join()` üzerinden normal, incelenebilir | Caller'ın başarılı olup olmadığını önemsediği her durum |

## Sık Sorulan Mülakat Soruları

- **Q:** `@Async` self-invocation ile çalışır mı? — Hayır, aynı proxy sınırlaması
  `@Transactional` ile birebir aynı.
- **Q:** `@Async` metod void dönerse exception'a ne olur? — `AsyncUncaughtExceptionHandler`'a
  gider, caller hiçbir şey göremez.
- **Q:** Custom executor tanımlamazsam ne olur? — `SimpleAsyncTaskExecutor` kullanılır - her
  çağrıda yeni, pool'lanmamış bir thread.

## 30 Saniyelik Mülakat Cevabı

"@Async'i self-invocation ile test ettim: `this.sendAsync()` çağrısı proxy'yi atlıyor ve
metod caller'ın kendi thread'inde senkron çalışıyor - thread isimlerini karşılaştırarak
kanıtladım. Ayrı bir bean üzerinden çağırınca gerçekten farklı bir thread'de çalıştığını
gördüm. Exception handling tarafında da ilginç bir fark var: void dönen @Async metodun
exception'ı sadece AsyncUncaughtExceptionHandler'a gidiyor, ama CompletableFuture dönen
metodun exception'ı doğrudan future'ın kendisinde - caller normal şekilde yakalayabiliyor."

## Takip Soruları

- `@Async` bir `@Transactional` metod içinden çağrılırsa transaction context'i ne olur?
- `@Async` metodun kendisi `@Transactional` olabilir mi, ve bu neyi ima eder?
