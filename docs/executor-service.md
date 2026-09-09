# ExecutorService ve Production ThreadPoolExecutor Ayarları

Kod: `com.interviewlab.executor.*` — Testler: `ExecutorServiceTest`

# Problem

`Executors.newFixedThreadPool(n)`, ona bir thread sayısı verdiğiniz için bounded gibi
görünür. Öyle midir?

## Hatalı Kod

```java
// com.interviewlab.executor.bad.UnboundedQueueExecutorService
Executors.newFixedThreadPool(threadCount);
```

## Neden Yanlış

`newFixedThreadPool`'un arkasındaki queue, **sınırsız** bir `LinkedBlockingQueue`'dur.
"Bounded thread" demek "bounded bekleyen iş" demek değildir - eğer task'lar `n` thread'in
onları tüketebileceğinden daha hızlı gelirse, hiçbir limit olmadan, tek tek kuyruğa girerler.
(`Executors.newCachedThreadPool()`'un ayna görüntüsü bir problemi vardır: sıfır kapasiteli bir
`SynchronousQueue`, yani kuyruğa almak yerine sınırsız sayıda yeni thread oluşturmaya devam
eder.)

## Gerçekte Ne Oluyor

`shouldQueueUnboundedWorkWithNewFixedThreadPool()`: 2-thread'lik bir fixed pool'a 5000
bloklayan task gönderilir - queue ~4998'e kadar büyür, caller'ı reddeden veya bloklayan hiçbir
şey olmaz. Production'da bu, açık bir bug değil, yavaş bir dependency'yi bekleyen bir
`OutOfMemoryError`'dur.

## Doğru Kod

```java
// com.interviewlab.executor.good.ProductionThreadPoolExecutorFactory
new ThreadPoolExecutor(
    corePoolSize, maxPoolSize, keepAliveTime, TimeUnit.SECONDS,
    new ArrayBlockingQueue<>(queueCapacity),   // BOUNDED - the actual fix
    namedDaemonThreadFactory,
    rejectionHandler);
```

- **corePoolSize / maxPoolSize** — sürdürülebilir ile burst worker sayısı;
  `corePoolSize`'ın ötesindeki thread'ler sadece queue dolduğunda oluşturulur.
- **keepAliveTime** — core'un üstündeki boşta bir thread'in geri alınmadan önce ne kadar
  hayatta kaldığı.
- **bounded queue** — `newFixedThreadPool`'da eksik olan gerçek backpressure mekanizması.
- **isimlendirilmiş `ThreadFactory`** — olmadan, her pool'un thread'leri `pool-N-thread-M`
  olur, bir thread dump'ta sıkışmış bir thread'in hangi pool'a ait olduğunu söylemekte
  işe yaramaz.
- **`RejectedExecutionHandler`** — hem queue hem de `maxPoolSize` tükendiğinde ne olacağı.

## AbortPolicy ile CallerRunsPolicy (backpressure)

- **AbortPolicy**: hemen `RejectedExecutionException` fırlatır -
  `shouldRejectWithAbortPolicyOnceQueueAndPoolAreFull()`. Caller, aşırı yüklenmeyi açıkça
  yönetmek zorundadır.
- **CallerRunsPolicy**: reddedilen task'ı ÇAĞIRAN thread'de, senkron olarak çalıştırır -
  `shouldRunOnCallingThreadWithCallerRunsPolicyOnceQueueAndPoolAreFull()`, task'ın testin
  kendi thread'inde çalıştığını kanıtlar. Bu, basit ve etkili bir backpressure
  mekanizmasıdır: submitter yavaşlatılır (task N kendi thread'inde bitene kadar N+1'i submit
  edemez), iş kaybetmeden geliş hızını doğal olarak kısar. Trade-off: caller (örn. bir HTTP
  request thread'i) artık pool'un işini senkron olarak yapar, bu da request gecikme
  artışlarına neden olabilir veya bir üst katmanda FARKLI bir pool'u tüketebilir.

## Shutdown yaşam döngüsü

```java
executor.shutdown();                              // stop accepting new tasks; let queued/running finish
if (!executor.awaitTermination(timeout, unit)) {
    executor.shutdownNow();                       // cancel queued tasks, interrupt running ones
}
```

`shouldFinishQueuedWorkOnGracefulShutdownButNotAcceptNewWork()`: `shutdown()`'dan sonra,
zaten kuyrukta olan 5 task da tamamlanana kadar çalışmaya devam eder, ama sonrasında yeni bir
submission reddedilir. `shutdownNowShouldReturnTasksThatNeverStarted()`: `shutdownNow()`,
kuyrukta hâlâ duran, başlamamış `Runnable`'ları tam olarak geri verir.

## Trade-off'lar

| Policy | Aşırı yük altındaki davranış | Ne zaman kullanılır |
|---|---|---|
| AbortPolicy | Hızlı ve gürültülü başarısız olur | Caller'ın kendi retry/fallback stratejisi varsa |
| CallerRunsPolicy | Caller'ı yavaşlatır, iş kaybı yok | Caller ara sıra işi kendisi yapmaya tolerans gösterebiliyorsa |
| DiscardPolicy / DiscardOldestPolicy | İşi sessizce düşürür | Nadiren doğru seçimdir - genellikle gerçek bir kapasite problemini gizler |

## Sık Sorulan Mülakat Soruları

- **Q:** `Executors.newFixedThreadPool` production'da neden riskli olabilir? — Queue'su
  unbounded; backpressure yok.
- **Q:** CallerRunsPolicy nasıl backpressure sağlar? — Submitter'ı işin kendisini yapmaya
  zorlayarak submission hızını doğal olarak yavaşlatır.
- **Q:** `shutdown()` ile `shutdownNow()` farkı nedir? — Biri kuyruktaki işlerin bitmesine
  izin verir, diğeri onları iptal edip çalışanları interrupt eder.

## 30 Saniyelik Mülakat Cevabı

"newFixedThreadPool'un aslında bounded olmadığını gösterdim: 2 thread'lik pool'a 5000 blocking
task attığımda, hiçbir şey reddedilmedi veya bloklanmadı - queue'da neredeyse 5000 task
birikti. Kendi ThreadPoolExecutor'ımı bounded bir ArrayBlockingQueue ve CallerRunsPolicy ile
kurunca, queue+pool dolduğunda yeni submit'ler caller'ın kendi thread'inde çalışmaya
başladı - bu doğal bir backpressure mekanizması, work kaybı yok ama caller yavaşlıyor."

## Takip Soruları

- `ForkJoinPool` ile `ThreadPoolExecutor` ne zaman farklı davranır (work-stealing)?
- `Future.cancel(true)` çalışan bir task'ı nasıl durdurur (interrupt semantics)?
