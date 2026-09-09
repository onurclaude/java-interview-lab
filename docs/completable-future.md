# CompletableFuture

Kod: `com.interviewlab.async.completablefuture.*` — Testler: `CompletableFutureTest`

# Problem

Bir müşterinin siparişlerini, ödemelerini ve önerilerini getir - üç bağımsız, yavaş çağrı -
ve bunları birleştir. Gerçek paralelliği nasıl elde edersin, ve kazara bunu elde
edememenin kolay yolları nelerdir?

## Hatalı Kod #1 — her future'ı hemen bloklamak

```java
// com.interviewlab.async.completablefuture.bad.BlockingGetAggregationService
String orders = CompletableFuture.supplyAsync(ordersSupplier, executor).get();
String payments = CompletableFuture.supplyAsync(paymentsSupplier, executor).get();
String recommendations = CompletableFuture.supplyAsync(recommendationsSupplier, executor).get();
```

**Neden yanlış:** her `.get()`, BİR SONRAKİ `supplyAsync` başlamadan önce bile bloklar - sıfır
örtüşme vardır. Toplam süre üçünün TOPLAMIDIR, onları senkron çağırmakla aynıdır.
`shouldTakeSumOfDurationsWhenBlockingOnEachFutureImmediately()`, geçen sürenin tek bir
çağrının süresinin yaklaşık 3 katı olduğunu kanıtlar.

## Hatalı Kod #2 — varsayılan common pool

```java
// com.interviewlab.async.completablefuture.bad.DefaultCommonPoolService
CompletableFuture.supplyAsync(blockingTask); // no executor -> ForkJoinPool.commonPool()
```

**Neden yanlış:** executor'sız overload, paylaşılan, JVM-genelinde
`ForkJoinPool.commonPool()` üzerinde çalışır - bu, her parallel stream ve niteliksiz her
`CompletableFuture` çağrısı tarafından da kullanılır. Boyutu varsayılan olarak
`availableProcessors() - 1`'dir ve kısa, CPU-bound işler için tasarlanmıştır, bloklayan I/O
için değil.
`shouldStarveCommonPoolWhenUsingDefaultExecutorForBlockingWork()`: common pool'u bloklayan
task'larla doyurmak, ilgisiz bir `parallelStream()` işleminin ilerlemesini ölçülebilir
şekilde engeller.

## Doğru Kod — önce her şeyi başlat, sonra birleştir

```java
// com.interviewlab.async.completablefuture.good.ComposedAggregationService
CompletableFuture<String> ordersFuture = CompletableFuture.supplyAsync(orders, executor);
CompletableFuture<String> paymentsFuture = CompletableFuture.supplyAsync(payments, executor);
CompletableFuture<String> recommendationsFuture = CompletableFuture.supplyAsync(recommendations, executor);

return CompletableFuture.allOf(ordersFuture, paymentsFuture, recommendationsFuture)
        .thenApply(v -> ordersFuture.join() + "|" + paymentsFuture.join() + "|" + recommendationsFuture.join());
```

Üçü de hiçbir şey bloklamadan önce, açık, kendine ait bir `Executor` üzerinde başlar.
`shouldTakeMaxDurationWhenComposingFuturesConcurrently()`: geçen süre, toplam değil, TEK BİR
çağrının süresine yakındır.

## thenApply ile thenCompose

```java
initial.thenApply(id -> CompletableFuture.completedFuture("fetched-" + id));   // CompletableFuture<CompletableFuture<String>> - NESTED
initial.thenCompose(id -> CompletableFuture.completedFuture("fetched-" + id)); // CompletableFuture<String> - FLAT
```

`thenApply`, bir değeri bir değere eşler; eğer mapper'ın kendisi başka bir async adım
başlatıyorsa (bir `CompletableFuture` döndürüyorsa), sonuç, açmak için İKİNCİ bir
`join()`/`get()` gerektiren beceriksiz, iç içe bir future olur. `thenCompose` tam olarak
bunun içindir - `Optional.map` karşısında `Optional.flatMap`'in async karşılığı.
`shouldProduceNestedFutureWithThenApplyAndFlatFutureWithThenCompose()`, `thenApply` için
çift-join gerekliliğini ve `thenCompose` için tek join'i kanıtlar.

## Exception yönetimi: exceptionally / handle / whenComplete

- **`exceptionally`** — sadece başarısızlık yolunda (tıpkı `catch` gibi) bir fallback ile
  recover eder.
- **`handle`** — HER İKİ yolda da çalışır, `(result, throwable)` ile, ikisinden tam olarak
  biri null değildir; dönüş değeri her durumda yeni sonuç olur.
- **`whenComplete`** — `handle` gibi `(result, throwable)`'ı gözlemler, ama dönüş değeri
  GÖZ ARDI EDİLİR - side effect'ler için (logging), tıpkı `finally` gibi.
  `shouldObserveButNotSuppressFailureWithWhenComplete()`, `whenComplete` çalıştıktan sonra
  bile future'ın hâlâ failed olduğunu kanıtlar (`join()` hâlâ exception fırlatır).

## Trade-off'lar

| Yaklaşım | Paralellik | Kullanılan pool | Hata görünürlüğü |
|---|---|---|---|
| Her future için bloklayan `.get()` | Yok (serileştirilmiş) | Verilen her neyse | Anlık, her çağrıda |
| Sonra `allOf` + `join()` | Tam | Verilen her neyse | Birleştirme adımına ertelenmiş |
| Executor argümanı yok | Tam, ama global bir pool'u paylaşır | `ForkJoinPool.commonPool()` | Aynı, artı ilgisiz kodu doyurma riski |

## Sık Sorulan Mülakat Soruları

- **Q:** `supplyAsync(...).get()` art arda neden yavaş? — Her `.get()` bir sonraki
  `supplyAsync`'i engelliyor, paralellik hiç oluşmuyor.
- **Q:** Default `supplyAsync` hangi thread pool'u kullanır? — `ForkJoinPool.commonPool()`,
  parallelStream ile paylaşılan JVM-genelinde tek bir pool.
- **Q:** `thenApply` ile `thenCompose` farkı nedir? — `thenApply` nested future üretebilir,
  `thenCompose` flatten eder.
- **Q:** `whenComplete` exception'ı yutar mı? — Hayır, sadece gözlemler; future hala failed
  kalır.

## 30 Saniyelik Mülakat Cevabı

"Üç bağımsız çağrıyı (orders, payments, recommendations) önce sırayla get() ile blokladım,
toplam süre üç çağrının toplamı kadar çıktı - hiç paralellik yoktu. Hepsini önce başlatıp
sonra allOf ile birleştirince süre tek bir çağrı kadar düştü. Ayrıca default supplyAsync'in
ortak ForkJoinPool'u kullandığını ve bloklayan işlerle bu pool'u doyurduğumda, tamamen
ilgisiz bir parallelStream() işleminin ilerleyemediğini gösterdim - bu yüzden production'da
her zaman kendi executor'ımı veriyorum."

## Takip Soruları

- `CompletableFuture.anyOf` ne zaman `allOf`'tan daha uygun olur?
- Bir `CompletableFuture` zincirinde exception nasıl "propagate" olur (kısa devre davranışı)?
