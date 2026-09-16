# Debugger Verification Matrix

**Dürüstlük ilkesi (kullanıcının açık talebi):** "Runtime Verified" sütunu SADECE bu oturumda
gerçekten çalıştırılan bir şeye dayanır. İki AYRI kanıt türü vardır ve KARIŞTIRILMAZ:

1. **HTTP/state kanıtı** — bu oturumda GERÇEKTEN gönderilen `curl` isteği + response body +
   (bazı lab'larda) `SqlStatementRecorder`/sayaç gibi runtime-gözlemlenebilir state. Bu tür
   kanıt **otomatik olarak, bu görev sırasında üretildi** → `YES (HTTP)`.
2. **IntelliJ debugger UI kanıtı** — bir breakpoint'in GERÇEKTEN hit/miss olduğunu, Call
   Stack panelinin GERÇEKTEN hangi sırayı gösterdiğini, Threads panelinin bir thread'i GERÇEKTEN
   BLOCKED/WAITING gösterdiğini SADECE IntelliJ'in kendi debugger UI'si kanıtlayabilir. Ben bu
   görevi headless bir CLI ortamında yürütüyorum — IntelliJ GUI'sine erişimim YOK. Bu yüzden
   HİÇBİR satırda "breakpoint gerçekten hit oldu" diye YALAN SÖYLEMİYORUM →
   `MANUAL_DEBUG_REQUIRED`.

`Result` sütunu, HTTP kanıtından (1) alınan GERÇEK response değerleridir — hiçbiri sabit/uydurma
değildir (bkz. `docs/RUNTIME_VERIFICATION.md`'deki ham log).

---

| # | Topic | Postman Request | Breakpoint 1 | Breakpoint 2 | Expected HIT/MISS | Runtime Verified | Result |
|---|---|---|---|---|---|---|---|
| 1 | Persistence context | `POST .../persistence/bad`, `.../good` | `MutatingDetachedEntityService.renameCustomerAssumingDirtyChecking()` | `PersistenceLifecycleService.renameViaDirtyCheckingOnly()` | BAD: 1 HIT (detached), GOOD: 1 HIT (managed) | HTTP: **YES** / breakpoint HIT-MISS: MANUAL_DEBUG_REQUIRED | `changeWasPersisted` BAD=`false`, GOOD=`true` |
| 2 | Transaction rollback | `POST .../transaction/bad`, `.../good` | `CheckedExceptionNoRollbackService.debitThenFailWithCheckedException()` | `RollbackForCheckedExceptionService.debitThenFailWithCheckedException()` | Her ikisi 1 HIT (ikisi de throw eder) | HTTP: **YES** / MANUAL_DEBUG_REQUIRED | BAD `finalBalance=70.00` (COMMITTED), GOOD `100.00` (ROLLED_BACK) |
| 3 | Propagation REQUIRED | `POST .../propagation/required` | `PropagationLabController.required()` | — | 1 HIT | HTTP: **YES** / MANUAL_DEBUG_REQUIRED | `transactionActiveBefore/After`=`true`/`true` |
| 4 | Propagation REQUIRES_NEW self-invocation | `POST .../requires-new/bad`, `.../good` | `SelfInvocationPaymentService.audit()` | `AuditService.audit()` (ayrı bean) | BAD: 1 HIT (self, proxy YOK); GOOD: 1 HIT (proxy üzerinden) | HTTP: **YES** / MANUAL_DEBUG_REQUIRED | `auditSurvivedOuterRollback` BAD=`false`, GOOD=`true` |
| 5 | Isolation non-repeatable read | `POST .../read-committed`, `.../repeatable-read` | `IsolationAnomalyLab.readTwiceUnderReadCommitted()` (2. read) | `readTwiceUnderRepeatableRead()` (2. read) | Her ikisi 2 HIT (T1 iki kez okur) | HTTP: **YES** / MANUAL_DEBUG_REQUIRED | BAD 100→200, GOOD 100→100 |
| 6 | Isolation phantom read | `POST .../phantom-read/read-committed`, `.../repeatable-read` | `countAboveTwiceUnderReadCommitted()` | `countAboveTwiceUnderRepeatableRead()` | Her ikisi 2 HIT | HTTP: **YES** / MANUAL_DEBUG_REQUIRED | BAD count 1→2, GOOD 1→1 |
| 7 | Isolation dirty read | `POST .../dirty-read/read-uncommitted` | `writeWithoutCommittingThenWait()` | `readUnderReadUncommittedWhileOtherTxUncommitted()` | Her ikisi 1 HIT, T2'ninki T1'in commit'inden ÖNCE | HTTP: **YES** / MANUAL_DEBUG_REQUIRED | `dirtyReadObserved=false` (100 okundu, 999 değil) |
| 8 | Optimistic locking | `POST .../optimistic/bad`, `.../good` | `NoVersionStockService.decreaseImmediately()` (T1) | `NoVersionStockService.loadSignalWaitThenDecrease()` (T2) / GOOD: `OptimisticStockService` eşdeğerleri | Her ikisi 1'er HIT, 2 AYRI thread'de | HTTP: **YES** / MANUAL_DEBUG_REQUIRED | BAD `finalStock=7` (lost update), GOOD `8` + `ObjectOptimisticLockingFailureException` |
| 9 | Pessimistic locking | `POST .../pessimistic/bad`, `.../good` | `NoVersionStockService` (BAD, kilitsiz) | `PessimisticStockService.lockHoldThenDecrease()`/`lockThenDecreaseImmediately()` (GOOD) | GOOD: T2'nin breakpoint'i T1 commit olana kadar HIT OLMAZ (DB seviyesinde bloke) | HTTP: **YES** / MANUAL_DEBUG_REQUIRED | BAD `finalStock=7`, GOOD `t2BlockedForMillis=515`, `finalStock=5` |
| 9b | Deadlock (DB-level) | `POST .../deadlock/bad`, `.../good` | `InconsistentLockOrderTransferService.transferStock()` | `DeterministicOrderTransferService.transferStock()` | BAD: her ikisi BLOKE olur, biri Postgres tarafından iptal edilir; GOOD: hiç deadlock yok | HTTP: **YES** (GERÇEK `"ERROR: deadlock detected"`, bu oturumda alındı, ~1.1s içinde) / MANUAL_DEBUG_REQUIRED | BAD: `t2Outcome=FAILED_WITH_DEADLOCK_DETECTED`, GOOD: ikisi de `COMMITTED` |
| 10 | Bean scope: singleton | `POST .../scopes/singleton/bad`, `.../good` | `MutableSingletonPriceService.calculateDiscountedPrice()` | `StatelessPriceService.calculateDiscountedPrice()` | Her ikisi 30 HIT (30 eşzamanlı çağrı) | HTTP: **YES** / MANUAL_DEBUG_REQUIRED | BAD `29/30` yanlış, GOOD `0/30` |
| 11 | Bean scope: prototype | `POST .../scopes/prototype/bad`, `.../good` | `SingletonWithDirectPrototypeInjection.getWorkerId()` | `SingletonWithObjectProvider.getWorkerId()` | BAD: 2 HIT (AYNI id), GOOD: 4 HIT (2+2, FARKLI id'ler) | HTTP: **YES** / MANUAL_DEBUG_REQUIRED | BAD aynı UUID, GOOD 4 farklı UUID |
| 12 | Bean scope: request | `GET /lab/scopes/request` | `RequestScopedIdHolder` constructor | — | Her HTTP request'te TAM 1 HIT | HTTP: **YES** / MANUAL_DEBUG_REQUIRED | `firstRead==secondReadSameRequest` (true) |
| 13 | Bean scope: session | `GET /lab/scopes/session` | `SessionScopedIdHolder` constructor | — | Session başına 1 HIT | HTTP: **YES** / MANUAL_DEBUG_REQUIRED | gerçek UUID döndü |
| 14 | Bean scope: application | `GET /lab/scopes/application` | `ApplicationScopedIdHolder` constructor | — | Uygulama ömrü boyunca 1 HIT | HTTP: **YES** / MANUAL_DEBUG_REQUIRED | gerçek UUID döndü |
| 15 | Bean lifecycle | `GET /api/labs/scopes/lifecycle` | `BeanLifecycleDemoBean` constructor | `BeanLifecycleLoggingPostProcessor.postProcessBeforeInitialization()` | Uygulama başlarken 4 HIT, SIRAYLA | HTTP: **YES** (event listesi) / MANUAL_DEBUG_REQUIRED (sıra debugger'da izlenmeli) | `observedEventsInOrder` 4 event, doğru sırada |
| 16 | AOP self-invocation + proxy | `POST .../aop/self-invocation/bad`, `.../good` | `SelfInvocationTimingService.slowStep()` | `ExecutionTimeAspect.trackExecutionTime()` | BAD: aspect **NOT HIT**; GOOD: aspect **HIT** | HTTP: **YES** (`interceptionCount` GERÇEK sayaçla ölçüldü) / MANUAL_DEBUG_REQUIRED (görsel breakpoint) | BAD `interceptionCount 0→0`; GOOD `0→1` |
| 17 | @Async self-invocation | `POST .../async/bad`, `.../good` | `SelfInvocationNotificationService.sendAsync()` | `AsyncSender.sendAsync()` | BAD: AYNI thread'de HIT; GOOD: FARKLI thread'de HIT | HTTP: **YES** (thread adları karşılaştırıldı) / MANUAL_DEBUG_REQUIRED | BAD aynı thread, GOOD `pool-2-thread-1` |
| 18 | Race condition | `POST .../counter/bad`, `.../good` | `UnsynchronizedIntCounter.increment()` | `AtomicCounterService.increment()` | Her ikisi 200.000 HIT (pratikte gözlemlenemez, Threads panelinden bak) | HTTP: **YES** / MANUAL_DEBUG_REQUIRED | BAD `33899≠200000`, GOOD `200000=200000` |
| 19 | synchronized | `POST .../synchronized/bad`, `.../good` | `UnsynchronizedIntCounter.increment()` | `IntrinsicMonitorCounter.incrementViaMethod()` | GOOD'da 9/10 thread BLOCKED görünmeli | HTTP: **YES** / MANUAL_DEBUG_REQUIRED | BAD `4203≠20000`, GOOD `20000=20000` |
| 20 | ReentrantLock | `POST .../reentrant-lock/bad`, `.../good` | `runLockLifecycleDemo()` (LOCK_ACQUIRED satırı) | aynı metot, `maxConcurrentHolders` | BAD: HIT'ler ÇAKIŞIR; GOOD: HER ZAMAN sırayla | HTTP: **YES** / MANUAL_DEBUG_REQUIRED | BAD `maxConcurrentHolders=4`, GOOD `=1` |
| 21 | ReadWriteLock | `POST .../read-write-lock/demo` | `ReadWriteLockCache.withReadLockHeld()` | — | 5 HIT, HEPSİ aynı anda | HTTP: **YES** / MANUAL_DEBUG_REQUIRED | `allReadersAcquiredConcurrently=true` |
| 22 | StampedLock | `POST .../stamped-lock/demo` | `StampedLockPoint.distanceFromOrigin()` | — | 1 HIT, hiçbir lock çağrısı yok | HTTP: **YES** / MANUAL_DEBUG_REQUIRED | `distanceFromOrigin=5.0` |
| 23 | ABA problem | `POST .../aba/bad`, `.../good` | `AbaProblemDemo.casSucceedsDespiteIntermediateChange()` | `AbaProblemDemo.stampedCasDetectsIntermediateChange()` | Her ikisi 1 HIT | HTTP: **YES** / MANUAL_DEBUG_REQUIRED | BAD `true` (yanlış pozitif), GOOD `false` (doğru red) |
| 24 | volatile | `POST .../volatile/misconception-check`, `.../correct-usage` | `VolatileCounter.increment()` | `ShutdownFlagWorker.requestStop()` | misconception: çakışan HIT'ler; correct: 1 HIT | HTTP: **YES** / MANUAL_DEBUG_REQUIRED | `stillRaced=true`, `workerStoppedWithinTimeout=true` |
| 25 | ThreadLocal | `POST .../threadlocal/bad`, `.../good` | `LeakyCorrelationIdService.getCorrelationId()` (task2) | GOOD eşdeğeri (`remove()` sonrası) | Her ikisi 1 HIT, AYNI worker thread'de | HTTP: **YES** / MANUAL_DEBUG_REQUIRED | BAD sızıntı `true`, GOOD `false` |
| 26 | ExecutorService kuyruğu | `POST .../executor/bad`, `.../good` | `ExecutorLabController.bad()` (queue size satırı) | `.good()` (queue size satırı) | Her ikisi 1 HIT | HTTP: **YES** / MANUAL_DEBUG_REQUIRED | BAD kuyruk `18`, GOOD `3` + `5` red |
| 27 | Rejection: CallerRuns | `POST .../executor/caller-runs` | `ExecutorLabController.callerRuns()` | — | 1 HIT | HTTP: **YES** / MANUAL_DEBUG_REQUIRED | `ranSynchronouslyOnCallingThread=true` |
| 28 | Rejection: Discard | `POST .../executor/discard` | `runDiscardDemo()` (task3 submit) | JDK `DiscardPolicy.rejectedExecution()` | HIT, boş gövde | HTTP: **YES** / MANUAL_DEBUG_REQUIRED | `executedTaskIds=[1,2]` |
| 29 | Rejection: DiscardOldest | `POST .../executor/discard-oldest` | `runDiscardDemo()` | JDK `DiscardOldestPolicy.rejectedExecution()` | HIT, `poll()+execute()` | HTTP: **YES** / MANUAL_DEBUG_REQUIRED | `executedTaskIds=[1,3]` |
| 30 | CompletableFuture | `POST .../sequential`, `.../parallel` | `BlockingGetAggregationService.aggregateSequentiallyByBlocking()` | `ComposedAggregationService.aggregateConcurrently()` | Her ikisi 1 HIT | HTTP: **YES** (`threadNames`, `durationMillis`) / MANUAL_DEBUG_REQUIRED | BAD `919ms`, GOOD `303ms` |
| 31 | N+1 | `GET .../n-plus-one/bad`, `.../good` | `NPlusOneOrderService` (döngü içi) | `FetchJoinOrderService`/`EntityGraphOrderService`/`DtoProjectionOrderService` | BAD: 10 HIT; GOOD: her biri 1 HIT | HTTP: **YES** (`queryCount`) / MANUAL_DEBUG_REQUIRED | BAD `11` sorgu, GOOD `2`/`1`/`1` |
| 32 | Lazy fetch | `GET .../fetch/lazy/bad`, `.../good` | `LazyInitializationDemoService.loadOrderWithoutTouchingItems()` | `LazyAccessWithinTransactionService.loadOrderAndCountItemsWithinTransaction()` | BAD: HIT + SONRASINDA exception; GOOD: HIT, sorun yok | HTTP: **YES** (GERÇEK exception mesajı) / MANUAL_DEBUG_REQUIRED | BAD `threwLazyInitializationException=true`, GOOD `false` |
| 33 | Eager fetch | `GET .../fetch/eager/bad` | `EagerFetchAlwaysLoadsService.sumQuantitiesOnly()` | — | 1 HIT | HTTP: **YES** (`SqlStatementRecorder`) / MANUAL_DEBUG_REQUIRED | `queriesTouchingProductTable=2` |
| 34 | Cache-Aside | `GET .../cache/bad/read`, `.../good/read` | `NoCacheProductService.read()` | `CacheAsideProductService.read()` | BAD: her çağrıda HIT; GOOD: hit/miss dalı DEĞİŞİR | HTTP: **YES** (`databaseReadCount`) / MANUAL_DEBUG_REQUIRED | BAD her çağrı +1, GOOD sadece miss'te +1 |
| 35 | Resilience Retry/Exhausted | `POST .../retry`, `.../retry-exhausted` | `FlakyExternalService.call()` | — | Her ikisi 3 HIT | HTTP: **YES** (`actualCallCount`) / MANUAL_DEBUG_REQUIRED | `SUCCEEDED_AFTER_RETRIES` / `ALL_ATTEMPTS_EXHAUSTED` |
| 36 | Resilience Circuit Breaker | `POST .../external/config`, `.../circuit-breaker` | `ResilienceLabController.configureExternal()` | `ResilienceLabController.circuitBreaker()` | config: 1 HIT; circuitBreaker: 2 HIT (CLOSED, OPEN) | HTTP: **YES** (`stateTransitions`) / MANUAL_DEBUG_REQUIRED | `["CLOSED","OPEN"]`, `shortCircuited=true` |
| 37 | Resilience RateLimiter | `POST .../rate-limiter` | `FlakyExternalService.call()` (izinliyken) | RateLimiter red yolunda catch bloğu | 2 HIT (izinli) + 3 catch | HTTP: **YES** (`permitted`/`rejected`) / MANUAL_DEBUG_REQUIRED | `2`/`3` |
| 38 | Resilience Bulkhead | `POST .../bulkhead` | `FlakyExternalService.call()` (kabul edilen) | catch (`BulkheadFullException`) | 2 HIT + 2 catch | HTTP: **YES** (`accepted`/`rejected`) / MANUAL_DEBUG_REQUIRED | `2`/`2` |
| 39 | Resilience Timeout | `POST .../timeout` | `ResilienceLabController.timeout()` (`executeFutureSupplier`) | catch (`TimeoutException`) | 1 HIT + catch | HTTP: **YES** (`outcome`) / MANUAL_DEBUG_REQUIRED | `TIMED_OUT` |
| 40 | Resilience Fallback | `POST .../fallback` | `ResilienceLabController.fallback()` catch bloğu | — | 1 HIT | HTTP: **YES** (`fallbackUsed`) / MANUAL_DEBUG_REQUIRED | `true` |
| 41 | Security (JWT) | `GET .../protected` (token'sız/USER), `.../admin-only` (USER/ADMIN) | `JwtAuthenticationFilter.doFilterInternal()` | `SecurityLabController.protectedEndpoint()`/`adminOnly()` | Filter HER istekte HIT; controller SADECE yetki varsa HIT | HTTP: **YES** (GERÇEK 401/403/200 status kodları) / MANUAL_DEBUG_REQUIRED | `401→200` (protected), `403→200` (admin-only) |
| 42 | Design Pattern Strategy+Factory | `POST .../strategy/bad`, `.../good`, `.../factory/resolve` | `IfElsePaymentProcessor.process()` | `PaymentStrategyFactory.getStrategy()` | Her ikisi 1 HIT | HTTP: **YES** (`implementationUsed`) / MANUAL_DEBUG_REQUIRED | if/else dalı vs Factory lookup |
| 43 | Design Pattern Adapter | `POST .../adapter/bad`, `.../good` | `DesignPatternsLabController.adapterBad()` | `ExternalPaymentProviderAdapter.pay()` | Her ikisi 1 HIT | HTTP: **YES** (`rawStatusCode` sızıntısı) / MANUAL_DEBUG_REQUIRED | BAD ham kod sızar, GOOD temiz |

**Ek satırlar (TOPIC_MATRIX'te ayrı, aynı yöntemle doğrulandı):**

| Topic | Postman Request | Breakpoint 1 | Breakpoint 2 | Expected HIT/MISS | Runtime Verified | Result |
|---|---|---|---|---|---|---|
| Design Pattern Observer | `POST .../observer/place-order` | `OrderPlacementService.placeOrder()` | `InventoryReservationListener.onOrderPlaced()` + `EmailNotificationListener.onOrderPlaced()` | publish 1 HIT, İKİ listener AYRI AYRI 1'er HIT | HTTP: **YES** / MANUAL_DEBUG_REQUIRED | `reservedByInventoryListener`/`notifiedByEmailListener`=`true`/`true` |
| Design Pattern Builder | `POST .../builder/build`, `.../build-invalid` | `OrderRequest.Builder.build()` (geçerli) | aynı metot (geçersiz, `itemIds.isEmpty()` dalı) | geçerli: HIT, döner; geçersiz: HIT, throw | HTTP: **YES** (`validationError`) / MANUAL_DEBUG_REQUIRED | geçersiz: `built=false` |
| Design Pattern Proxy | `POST .../proxy/without-logging`, `.../with-logging` | `GreeterProxyFactory` InvocationHandler lambda | — | without: **NOT HIT**; with: HIT | HTTP: **YES** (`interceptionLog`) / MANUAL_DEBUG_REQUIRED | without `[]`, with `["before:greet","after:greet"]` |
| Exceptions: swallowed | `POST .../exceptions/swallowed` | `SwallowingExceptionService.chargeCardSilently()` | — | 1 HIT, boş/log-only catch | HTTP: **YES** | `methodReportedSuccess=true` (yutuldu) |
| Exceptions: lossy-rethrow | `POST .../exceptions/lossy-rethrow` | `LossyRethrowService.chargeCardLosingCause()` | — | 1 HIT | HTTP: **YES** | `hasOriginalCause=false` |
| Exceptions: good (wrapped) | `POST .../exceptions/good` | `WrappingExceptionService.chargeCard()` | — | 1 HIT | HTTP: **YES** | `causePreserved=true` |

---

## Kategori B — main() varlığı (grep ile bu oturumda doğrulandı, execution önceki fazda yapıldı)

| Runner | main() imzası doğrulandı mı | `java -cp` ile execute edildi mi (önceki faz) | IntelliJ UI'de "Run/Debug" tıklandı mı |
|---|---|---|---|
| StringPoolLabRunner | **YES** (grep) | **YES** | MANUAL_DEBUG_REQUIRED |
| IntegerCacheLabRunner | **YES** | **YES** | MANUAL_DEBUG_REQUIRED |
| HashMapInternalsLabRunner | **YES** | **YES** | MANUAL_DEBUG_REQUIRED |
| ThreadLifecycleLabRunner | **YES** | **YES** | MANUAL_DEBUG_REQUIRED |
| VirtualThreadLabRunner | **YES** | **YES** | MANUAL_DEBUG_REQUIRED |
| JvmMemoryLabRunner | **YES** | **YES** | MANUAL_DEBUG_REQUIRED |
| GcReachabilityLabRunner | **YES** | **YES** | MANUAL_DEBUG_REQUIRED |
| ImmutabilityLabRunner | **YES** | **YES** | MANUAL_DEBUG_REQUIRED |
| EqualsHashCodeLabRunner | **YES** | **YES** | MANUAL_DEBUG_REQUIRED |
| CollectionsLabRunner | **YES** | **YES** | MANUAL_DEBUG_REQUIRED |
| StreamLabRunner | **YES** | **YES** | MANUAL_DEBUG_REQUIRED |
| ParallelStreamLabRunner | **YES** | **YES** | MANUAL_DEBUG_REQUIRED |
| ReflectionLabRunner | **YES** | **YES** | MANUAL_DEBUG_REQUIRED |
| BlockingQueueLabRunner | **YES** | **YES** | MANUAL_DEBUG_REQUIRED |

**Neden `java -cp` yeterli değil ama gerekli bir kanıt:** `java -cp target/classes com.interviewlab.labrunner.X`
komutu, IntelliJ'in "Run 'X.main()'" butonuna bastığında ARKA PLANDA ÇALIŞTIRDIĞI AYNI JVM
komutudur (IntelliJ sadece classpath'i kendi derleme çıktısından otomatik oluşturur ve bu
komutu senin yerine çalıştırır). Yani `java -cp` ile başarılı çalıştırma, kod YOLUNUN ve
main() metodunun GERÇEKTEN çalıştığını kanıtlar — ama "IntelliJ'in gutter'ında yeşil ok
ikonu göründü ve tıklanabildi" iddiasını SADECE IntelliJ'in kendisini açıp bakarak
doğrulayabilirim, ki bu görevde YAPAMADIM. Bu yüzden bu sütun dürüstçe `MANUAL_DEBUG_REQUIRED`
işaretlidir — kullanıcının kendi IntelliJ'inde, her .java dosyasını açıp main() yanındaki
gutter ikonuna TIKLAMASI gerekir (standart IntelliJ davranışı, ekstra konfigürasyon
gerektirmez, çünkü main() imzası ve sınıf erişilebilirliği zaten doğrulandı).

---

## Regresyon (bu oturumun sonunda)

`./mvnw test` → **171 test, 0 hata, 0 başarısızlık, 0 atlanan.**
