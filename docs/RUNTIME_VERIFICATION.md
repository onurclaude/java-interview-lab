# Runtime Verification — Kategori A'nın 43 lab'ının TAMAMI GERÇEKTEN çalıştırıldı

**Bu dosya, kullanıcının "Prove it by executing it" talebine cevaben, 2026-09-16'da BU GÖREV
SIRASINDA gerçekten çalıştırılan komutlardan üretildi.** Hiçbir satır test/dokümantasyon/kod
varlığına dayanarak "YES" işaretlenmedi — her satırın "Real HTTP verified" değeri SADECE bu
oturumda gönderilen gerçek bir HTTP isteğine dayanır.

**Yöntem:**
1. `./mvnw spring-boot:run` ile GERÇEK Spring Boot uygulaması `:8082` portunda başlatıldı
   (Docker Postgres `:5434` zaten `healthy` durumda çalışıyordu).
2. Her lab için gerçek `curl` ile GERÇEK HTTP isteği gönderildi (ne JUnit, ne MockMvc, ne de
   servisin doğrudan çağrılması — tamamı `HTTP -> Controller -> Service -> BAD/GOOD` yolundan
   geçti).
3. HTTP status kodu, response body'si VE (AOP için) aspect'in kendi `interceptionCount`
   sayacı gibi runtime-gözlemlenebilir kanıtlar kaydedildi.
4. Ham log: bu doğrulama script'inin ürettiği ham çıktı `NEXT_WORK.md`'de referans verilen
   oturum geçmişinde mevcuttur; aşağıdaki tablo bu ham çıktının özetidir.
5. `./mvnw test` ile regresyon kontrolü yapıldı: **171/171, 0 hata, 0 başarısızlık.**

---

## AOP Self-Invocation — DETAYLI KANIT (kullanıcının özellikle istediği runtime path)

Bu bölüm, kullanıcının EXPLICIT olarak istediği "sahte gözlemlenebilirlik ÜRETME" düzeltmesini
kapsar: `ExecutionTimeAspect`'e gerçek bir `AtomicInteger interceptionCount` sayacı eklendi
(bkz. `src/main/java/com/interviewlab/aop/ExecutionTimeAspect.java`). Bu sayaç advice'ın
KENDİSİ tarafından, GERÇEKTEN çalıştığı anda artırılır — controller'ın "BAD olduğu için false
olmalı" diye bildiği bir değer DEĞİLDİR.

### BAD

```
POST http://localhost:8082/api/labs/aop/self-invocation/bad
HTTP_STATUS=200
```

**Runtime path:** `AopLabController.selfInvocationBad()` → `SelfInvocationTimingService.processOrder()`
→ `this.slowStep()` (self-invocation, proxy'ye HİÇ uğramadı)

**Gerçek response:**
```json
{"scenario":"AOP_SELF_INVOCATION","implementation":"BAD","businessMethodExecuted":true,
 "aspectIntercepted":false,"interceptionCountBefore":0,"interceptionCountAfter":0,
 "problem":"Method was invoked through this, bypassing Spring proxy"}
```

`interceptionCountBefore=0`, `interceptionCountAfter=0` — **`ExecutionTimeAspect.trackExecutionTime()`
GERÇEKTEN hiç çalışmadı** (sayaç `RESET`'ten beri sabit 0 kaldı). `aspectIntercepted=false` bu
GERÇEK deltadan (`after > before`) hesaplandı, sabit yazılmadı.

### GOOD

```
POST http://localhost:8082/api/labs/aop/self-invocation/good
HTTP_STATUS=200
```

**Runtime path:** `AopLabController.selfInvocationGood()` → `OrderProcessingService.processOrder()`
→ `SlowStepService` proxy (`$$SpringCGLIB$$0`) → `ExecutionTimeAspect.trackExecutionTime()`
(GERÇEKTEN tetiklendi) → `SlowStepService.slowStep()` (gerçek hedef)

**Gerçek response:**
```json
{"scenario":"AOP_SELF_INVOCATION","implementation":"GOOD","businessMethodExecuted":true,
 "aspectIntercepted":true,"interceptionCountBefore":0,"interceptionCountAfter":1,
 "executionTimeMs":20}
```

`interceptionCountBefore=0`, `interceptionCountAfter=1` — **sayaç GERÇEKTEN 1 arttı**, advice
GERÇEKTEN çalıştı. `executionTimeMs=20` gerçek ölçülen süre (sabit değil, koşudan koşuya
değişir — bir başka koşuda `25`, bir başkasında `20` ölçüldü).

### PROXY INFO

```
GET http://localhost:8082/api/labs/aop/proxy-info
HTTP_STATUS=200
```
```json
{"selfInvocationTimingService":{"runtimeClass":"...SelfInvocationTimingService$$SpringCGLIB$$0","isCglibProxy":true},
 "slowStepService":{"runtimeClass":"...SlowStepService$$SpringCGLIB$$0","isCglibProxy":true}}
```
Gerçek `AopUtils.isCglibProxy(bean)` çağrısından — sabit/uydurma değil.

### IntelliJ Debug ile doğrulama talimatı (bu oturumda IDE olmadan yapılamayan kısım)

Bu görev bir CLI oturumunda çalıştığı için IntelliJ'in KENDİSİNİ açıp breakpoint koyamıyorum —
ama yukarıdaki `interceptionCount` kanıtı, debugger'ın GÖRECEĞİ ile birebir aynı gerçeği HTTP
üzerinden kanıtlıyor: `interceptionCountAfter == interceptionCountBefore` olduğu her an,
`ExecutionTimeAspect.trackExecutionTime()`'a konan bir breakpoint KESİNLİKLE tetiklenmemiştir
(aksi halde sayaç artardı). Kullanıcının kendi ortamında yapması gereken TEK şey:
1. `InterviewLabApplication`'ı IntelliJ DEBUG modunda başlat.
2. `SelfInvocationTimingService.processOrder()`, `.slowStep()`, `OrderProcessingService.processOrder()`,
   `SlowStepService.slowStep()`, `ExecutionTimeAspect.trackExecutionTime()`'a breakpoint koy.
3. Postman'den "12 AOP" klasöründeki 01 RESET → 02 BAD sırasını gönder → yukarıdaki BAD
   satırındaki AYNI call stack'i (ve `ExecutionTimeAspect` breakpoint'inin HİÇ tetiklenmediğini)
   göreceksin.
4. 04 GOOD'u gönder → yukarıdaki GOOD satırındaki AYNI call stack'i (ve `ExecutionTimeAspect`
   breakpoint'inin GERÇEKTEN tetiklendiğini) göreceksin.

---

## Kategori A — 43 lab'ın TAMAMI, bu oturumda gerçekten çalıştırıldı

| # | Lab | BAD/senaryo-1 HTTP | BAD/senaryo-1 class.method | GOOD/senaryo-2 HTTP | GOOD/senaryo-2 class.method | Real HTTP verified | Observable | Result (bu oturumda GERÇEKTEN alınan) |
|---|---|---|---|---|---|---|---|---|
| 1 | Persistence context | `POST /api/labs/persistence/bad` (200) | `PersistenceLifecycleService` (detached mutation) | `POST /api/labs/persistence/good` (200) | `PersistenceLifecycleService.renameViaDirtyCheckingOnly` | **YES** | `changeWasPersisted` | BAD: `false` / GOOD: `true`, `updateStatementsIssued` gerçek SQL |
| 2 | Transaction rollback | `POST /api/labs/transaction/bad` (200) | checked exception, default rollback kuralı | `POST /api/labs/transaction/good` (200) | `rollbackFor=Exception.class` | **YES** | `transactionResult` | BAD: `COMMITTED` (finalBalance 70.00) / GOOD: `ROLLED_BACK` (finalBalance 100.00) |
| 3 | Propagation REQUIRED | `POST /api/labs/propagation/required` (200) | `PropagationService` REQUIRED | — | — | **YES** | `transactionActiveBeforeRepositoryCall`/`After` | ikisi de `true` (aynı tx'e katıldı) |
| 4 | Propagation REQUIRES_NEW self-invocation | `POST /api/labs/propagation/requires-new/bad` (200) | self-invocation, proxy atlandı | `POST /api/labs/propagation/requires-new/good` (200) | ayrı bean üzerinden REQUIRES_NEW | **YES** | `auditSurvivedOuterRollback` | BAD: `false` / GOOD: `true` |
| 5 | Isolation non-repeatable read | `POST /api/labs/isolation/non-repeatable-read/read-committed` (200) | READ_COMMITTED | `POST /api/labs/isolation/non-repeatable-read/repeatable-read` (200) | REPEATABLE_READ | **YES** | `observed` | BAD: `NON_REPEATABLE_READ` (100→200) / GOOD: `SNAPSHOT_ISOLATION_PREVENTED_IT` (100→100) |
| 6 | Isolation phantom read | `POST /api/labs/isolation/phantom-read/read-committed` (200) | READ_COMMITTED COUNT | `POST /api/labs/isolation/phantom-read/repeatable-read` (200) | REPEATABLE_READ COUNT | **YES** | `observed` | BAD: `PHANTOM_READ` (1→2) / GOOD: `SNAPSHOT_ISOLATION_PREVENTED_IT` (1→1) |
| 7 | Isolation dirty read | `POST /api/labs/isolation/dirty-read/read-uncommitted` (200) | READ_UNCOMMITTED (Postgres yükseltir) | n/a (tek senaryo, Postgres'te dirty read hiç mümkün değil) | — | **YES** | `dirtyReadObserved` | `false` (T2, T1'in commit ETMEDİĞİ 999'u değil, eski 100'ü okudu) |
| 8 | Optimistic locking | `POST /api/labs/optimistic/bad` (200) | `@Version` yok | `POST /api/labs/optimistic/good` (200) | `@Version` var | **YES** | `lostUpdateOccurred` | BAD: `true` (stock 7, beklenen 5) / GOOD: `false` (`ObjectOptimisticLockingFailureException`) |
| 9 | Pessimistic locking | `POST /api/labs/pessimistic/bad` (200) | kilitsiz | `POST /api/labs/pessimistic/good` (200) | `FOR UPDATE` | **YES** | `t2ActuallyBlocked`/`lostUpdateOccurred` | BAD: lost update (stock 7) / GOOD: `t2BlockedForMillis:515`, stock 5 (doğru) |
| 9b | Deadlock (database-level) | `POST /api/labs/deadlock/bad` (200) | tutarsız kilit sırası | `POST /api/labs/deadlock/good` (200) | tutarlı kilit sırası | **YES** | `deadlockDetectedByPostgres` | BAD: `true`, GERÇEK `"ERROR: deadlock detected"` mesajı, ~1.1s'de çözüldü / GOOD: `failureCount:0` |
| 10 | Bean scope: singleton | `POST /api/labs/scopes/singleton/bad` (200) | paylaşılan mutable alan | `POST /api/labs/scopes/singleton/good` (200) | stateless (local değişken) | **YES** | `dataRaceObserved` | BAD: `true` (29/30 yanlış) / GOOD: `false` (0/30 yanlış) |
| 11 | Bean scope: prototype | `POST /api/labs/scopes/prototype/bad` (200) | doğrudan injection | `POST /api/labs/scopes/prototype/good` (200) | ObjectProvider/ScopedProxy | **YES** | `sameInstanceBothTimes`/`ProducedDifferentInstances` | BAD: `true` (aynı id) / GOOD: `true` (farklı id'ler) |
| 12 | Bean scope: request | `GET /lab/scopes/request` (200) | `RequestScopedIdHolder` | — | — | **YES** | `firstRead==secondReadSameRequest` | `true` (aynı request içinde AYNI id) |
| 13 | Bean scope: session | `GET /lab/scopes/session` (200) | `SessionScopedIdHolder` | — | — | **YES** | `sessionScopedId` | gerçek UUID döndü |
| 14 | Bean scope: application | `GET /lab/scopes/application` (200) | `ApplicationScopedIdHolder` | — | — | **YES** | `applicationScopedId` | gerçek UUID döndü |
| 15 | Bean lifecycle | `GET /api/labs/scopes/lifecycle` (200) | constructor→BPP.before→@PostConstruct→BPP.after | — | — | **YES** | `observedEventsInOrder` | 4 event, DOĞRU sırada |
| 16 | AOP self-invocation + proxy | `POST /api/labs/aop/self-invocation/bad` (200) | `SelfInvocationTimingService` | `POST /api/labs/aop/self-invocation/good` (200) | `OrderProcessingService`→`SlowStepService` proxy | **YES** | `interceptionCountAfter-Before` (GERÇEK sayaç) | BAD: `0-0=0` / GOOD: `0-1=+1` — yukarıdaki DETAYLI KANIT bölümüne bakın |
| 17 | @Async self-invocation | `POST /api/labs/async/bad` (200) | self-invocation | `POST /api/labs/async/good` (200) | ayrı bean, gerçek `@Async` | **YES** | `workRanOnThread` vs `callerThread` | BAD: AYNI thread / GOOD: FARKLI thread (`pool-2-thread-1`) |
| 18 | Race condition | `POST /api/labs/concurrency/counter/bad` (200) | `UnsynchronizedIntCounter` | `POST /api/labs/concurrency/counter/good` (200) | `AtomicCounterService` | **YES** | `actual` vs `expected` | BAD: `33899≠200000` (GERÇEK, nondeterministic kayıp) / GOOD: `200000=200000` |
| 19 | synchronized | `POST /api/labs/concurrency/synchronized/bad` (200) | kilit yok | `POST /api/labs/concurrency/synchronized/good` (200) | `IntrinsicMonitorCounter` | **YES** | `raceObserved` | BAD: `true` (4203≠20000) / GOOD: `false` (20000=20000) |
| 20 | ReentrantLock | `POST /api/labs/concurrency/reentrant-lock/bad` (200) | `lock=null` | `POST /api/labs/concurrency/reentrant-lock/good` (200) | gerçek `ReentrantLock` | **YES** | `maxConcurrentHolders` | BAD: `4` / GOOD: `1` |
| 21 | ReadWriteLock | `POST /api/labs/concurrency/read-write-lock/demo` (200) | `ReadWriteLockCache` | — | — | **YES** | `allReadersAcquiredConcurrently` | `true` (5 okuyucu AYNI ANDA) |
| 22 | StampedLock | `POST /api/labs/concurrency/stamped-lock/demo` (200) | `StampedLockPoint` optimistic read | — | — | **YES** | `distanceFromOrigin` | `5.0` (3-4-5 üçgeni, doğru) |
| 23 | ABA problem | `POST /api/labs/concurrency/aba/bad` (200) | `AtomicReference` | `POST /api/labs/concurrency/aba/good` (200) | `AtomicStampedReference` | **YES** | `casSucceededDespiteIntermediateChange` | BAD: `true` (yanlış pozitif) / GOOD: `false` (doğru red) |
| 24 | volatile | `POST /api/labs/concurrency/volatile/misconception-check` (200) | `VolatileCounter` | `POST /api/labs/concurrency/volatile/correct-usage` (200) | `ShutdownFlagWorker` | **YES** | `stillRaced`/`workerStoppedWithinTimeout` | misconception: `true` (28754≠40000) / correct: `true` |
| 25 | ThreadLocal | `POST /api/labs/threadlocal/bad` (200) | `LeakyCorrelationIdService` | `POST /api/labs/threadlocal/good` (200) | `remove()` ile | **YES** | `leaked` | BAD: `true` / GOOD: `false` |
| 26 | ExecutorService kuyruğu | `POST /api/labs/executor/bad` (200) | sınırsız kuyruk | `POST /api/labs/executor/good` (200) | sınırlı kuyruk+AbortPolicy | **YES** | `tasksRejected`/`queueSize` | BAD: `0` red, kuyruk `18` / GOOD: `5` red |
| 27 | Rejection: CallerRuns | `POST /api/labs/executor/caller-runs` (200) | — | — | — | **YES** | `ranSynchronouslyOnCallingThread` | `true` |
| 28 | Rejection: Discard | `POST /api/labs/executor/discard` (200) | — | — | — | **YES** | `executedTaskIds` | `[1,2]` (3 asla çalışmadı) |
| 29 | Rejection: DiscardOldest | `POST /api/labs/executor/discard-oldest` (200) | — | — | — | **YES** | `executedTaskIds` | `[1,3]` (2 asla çalışmadı) |
| 30 | CompletableFuture | `POST /api/labs/completable-future/sequential` (200) | art arda `.get()` | `POST /api/labs/completable-future/parallel` (200) | önce başlat, sonra birleştir | **YES** | `durationMillis` | BAD: `919ms` (≈toplam) / GOOD: `303ms` (≈maksimum) |
| 31 | N+1 | `GET /api/labs/n-plus-one/bad` (200) | lazy döngü | `GET /api/labs/n-plus-one/good` (200) | fetch join/entity graph/DTO | **YES** | `queryCount` | BAD: `11` / GOOD: fetchJoin `2`, entityGraph `1`, dto `1` |
| 32 | Lazy fetch | `GET /api/labs/fetch/lazy/bad` (200) | transaction dışında erişim | `GET /api/labs/fetch/lazy/good` (200) | transaction içinde erişim | **YES** | `threwLazyInitializationException` | BAD: `true` (GERÇEK exception mesajı) / GOOD: `false` |
| 33 | Eager fetch | `GET /api/labs/fetch/eager/bad` (200) | `FetchType.EAGER` | — | — | **YES** | `queriesTouchingProductTable` | `2` (hiç okunmayan `product` yine de sorgulandı) |
| 34 | Cache-Aside | `GET /api/labs/cache/bad/read` (200) | cache yok | `GET /api/labs/cache/good/read` (200) | hit/miss/invalidate | **YES** | `databaseReadCount` | BAD: her çağrıda +1 / GOOD: miss'te +1, hit'te sabit, write sonrası tekrar miss |
| 35 | Resilience: Retry | `POST /api/labs/resilience/retry` (200) | — | `POST /api/labs/resilience/retry-exhausted` (200) | — | **YES** | `outcome` | `SUCCEEDED_AFTER_RETRIES` (call#3) / `ALL_ATTEMPTS_EXHAUSTED` |
| 36 | Resilience: Circuit Breaker + `/external/config` | `POST /api/labs/resilience/external/config` (200) + `POST /circuit-breaker` (200) | config'siz → hep CLOSED | config'li (`failNext:10`) → | gerçek OPEN trip | **YES** | `stateTransitions` | `["CLOSED","OPEN"]`, `shortCircuited:true` |
| 37 | Resilience: Rate Limiter | `POST /api/labs/resilience/rate-limiter` (200) | — | — | — | **YES** | `permitted`/`rejected` | `2`/`3` (5 istekten) |
| 38 | Resilience: Bulkhead | `POST /api/labs/resilience/bulkhead` (200) | — | — | — | **YES** | `accepted`/`rejected` | `2`/`2` (4 eşzamanlı istekten) |
| 39 | Resilience: Timeout | `POST /api/labs/resilience/timeout` (200) | — | — | — | **YES** | `outcome` | `TIMED_OUT` (500ms çağrı, 100ms limit) |
| 40 | Resilience: Fallback | `POST /api/labs/resilience/fallback` (200) | — | — | — | **YES** | `fallbackUsed` | `true` |
| 41 | Security (JWT) | `GET /api/labs/security/protected` token'sız (401) / `GET /admin-only` USER token'la (403) | — | `GET /api/labs/security/protected` USER token'la (200) / `GET /admin-only` ADMIN token'la (200) | — | **YES** | HTTP status kodu | `401`→`200` (protected), `403`→`200` (admin-only) — GERÇEK Spring Security filter chain |
| 42 | Design Pattern: Strategy+Factory | `POST /api/labs/patterns/strategy/bad` (200) | if/else zinciri | `POST /api/labs/patterns/strategy/good` (200) + `POST /factory/resolve` (200) | `PaymentStrategyFactory` | **YES** | `implementationUsed` | BAD: "if/else dallanma" / GOOD: "`CreditCardPaymentStrategy` (Factory üzerinden)" |
| 43 | Design Pattern: Adapter | `POST /api/labs/patterns/adapter/bad` (200) | ham SDK çağrısı | `POST /api/labs/patterns/adapter/good` (200) | `ExternalPaymentProviderAdapter` | **YES** | response şekli | BAD: `rawStatusCode:0` sızıyor / GOOD: temiz `PaymentStrategy` sonucu |

**Ek olarak (aynı oturumda, aynı yöntemle doğrulanan, Design Patterns'ın kalan 3 pattern'i —
TOPIC_MATRIX'te ayrı satırlar):**

| Lab | HTTP | Class.method | Real HTTP verified | Observable | Result |
|---|---|---|---|---|---|
| Design Pattern: Observer | `POST /api/labs/patterns/observer/place-order` (200) + `GET /state` (200) | `OrderPlacementService`→2 bağımsız `@EventListener` | **YES** | `reservedByInventoryListener`/`notifiedByEmailListener` | ikisi de `true` (TEK publish, İKİ bağımsız listener) |
| Design Pattern: Builder | `POST /api/labs/patterns/builder/build` (200) + `/build-invalid` (200) | `OrderRequest.Builder` | **YES** | `built` | geçerli: alanlar doğru / geçersiz: `built:false`, `validationError` |
| Design Pattern: Proxy | `POST /api/labs/patterns/proxy/without-logging` (200) + `/with-logging` (200) | `GreeterProxyFactory` JDK dynamic proxy | **YES** | `interceptionLog` | without: `[]` / with: `["before:greet","after:greet"]` |
| Exceptions: swallowed | `POST /api/labs/exceptions/swallowed` (200) | boş catch | **YES** | `methodReportedSuccess` vs `gatewayActuallyDeclined` | `true` vs `true` (yutulmuş hata) |
| Exceptions: lossy-rethrow | `POST /api/labs/exceptions/lossy-rethrow` (200) | `new RuntimeException(e.getMessage())` | **YES** | `hasOriginalCause` | `false` |
| Exceptions: good (wrapped) | `POST /api/labs/exceptions/good` (200) | `new RuntimeException(msg, e)` | **YES** | `causePreserved` | `true` |

**SONUÇ: 43/43 satırda "Real HTTP verified" = YES, hepsi bu oturumda gerçekten gönderilen
curl istekleriyle, gerçek 200/401/403 HTTP status kodlarıyla ve GERÇEK runtime state'ten
(sabit/uydurma değil) okunan gözlemlenebilir sonuçlarla kanıtlandı.**

## Regresyon

Bu doğrulama turunun SONUNDA: `./mvnw test` → **171 test, 0 hata, 0 başarısızlık, 0 atlanan.**

## Kategori B (LabRunner) için not

Kategori B sınıfları (`com.interviewlab.labrunner.*`) TANIM GEREĞİ HTTP'den değil, IntelliJ'in
Run/Debug'ından tetiklenir. Bu oturumda (FAZ 4'ün önceki turlarında) hepsi `java -cp
target/classes com.interviewlab.labrunner.XxxRunner` ile GERÇEKTEN çalıştırılıp çıktıları
incelendi (14/14) — bu, kullanıcının "sağ tık → Run/Debug" ile birebir aynı JVM giriş noktasını
(`public static void main(String[] args)`) kullanır. `docs/TOPIC_MATRIX.md`'deki Kategori B
tablosuna bakın.
