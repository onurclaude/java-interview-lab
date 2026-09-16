# Interactivity Audit

Bu dosya, projenin TÜM konularını yeni, kesinleşmiş "interactive" tanımına göre denetler:

> Bir konu SADECE şu durumda INTERACTIVE'dir: bir HTTP isteği (Postman) GERÇEK kod
> çalıştırıyor, IntelliJ debugger ile ADIM ADIM izlenebiliyor, sonuç GÖZLEMLENEBİLİYOR, ve
> BAD/GOOD karşılaştırması KENDİ İSTEĞİMLE tetiklenebiliyor. JUnit test'in var olması TEK
> BAŞINA yeterli DEĞİLDİR - JUnit otomatik doğrulamadır, birincil öğrenme arayüzü değildir.

**Önceki `TOPIC_MATRIX.md`'deki "DONE" durumları bu denetim için GEÇERSİZ SAYILDI.** Bu
dosya, her konuyu SIFIRDAN, bu yeni standarda göre yeniden değerlendirir. Denetim
2026-09-15'te, kullanıcının "STOP" mesajından sonra yapıldı.

Kolonlar: **Current implementation** (kod var mı) / **Current trigger** (nasıl çalıştırılıyordu
- ÖNCEKİ durum) / **Can Run Manually?** (Postman'den tetiklenebilir mi) / **Can Debug
Manually?** (IntelliJ'de breakpoint ile izlenebilir mi) / **BAD Observable?** / **GOOD
Observable?** / **Postman/Runner** (hangi mekanizma) / **Database Observable?** (DBeaver ile
görülebilir mi, ilgiliyse) / **Missing Work** / **Status** (bu turda: `FIXED`, `ALREADY_OK`,
`STILL_MISSING`).

---

## Kategori A — Postman + IntelliJ debugger ile tetiklenebilmesi GEREKEN konular

| Topic | Current impl | Current trigger (ÖNCE) | Run Manually? | Debug Manually? | BAD Obs? | GOOD Obs? | Postman/Runner | DB Obs? | Missing Work | Status |
|---|---|---|---|---|---|---|---|---|---|---|
| Persistence Context / Dirty Checking | ✅ | `/api/labs/persistence/*` | ✅ | ✅ | ✅ | ✅ | HTTP | ✅ | — | **ALREADY_OK** |
| Transaction Rollback | ✅ | `/api/labs/transaction/*` | ✅ | ✅ | ✅ | ✅ | HTTP | ✅ | — | **ALREADY_OK** |
| Propagation | ✅ | `/api/labs/propagation/*` | ✅ | ✅ | ✅ | ✅ | HTTP | ✅ | — | **ALREADY_OK** |
| Isolation | ✅ | `/api/labs/isolation/*` | ✅ | ✅ | ✅ | ✅ | HTTP | ✅ | ~~Phantom/dirty-read sadece testte~~ | **FIXED** (`/phantom-read/*` ve `/dirty-read/*` eklendi, GERÇEK Postgres MVCC davranışı doğrulandı: phantom READ_COMMITTED'de görünür/REPEATABLE_READ'de görünmez, dirty read Postgres'te HİÇBİR ZAMAN mümkün değil) |
| Optimistic Locking | ✅ | `/api/labs/optimistic/*` | ✅ | ✅ | ✅ | ✅ | HTTP | ✅ | — | **ALREADY_OK** |
| Pessimistic Locking | ✅ | `/api/labs/pessimistic/*` | ✅ | ✅ | ✅ | ✅ | HTTP | ✅ | — | **ALREADY_OK** |
| Singleton scope | ✅ | `/api/labs/scopes/singleton/*` | ✅ | ✅ | ✅ (gerçek data race) | ✅ | HTTP | — | — | **ALREADY_OK** |
| Prototype scope | ✅ | `/api/labs/scopes/prototype/*` | ✅ | ✅ | ✅ | ✅ | HTTP | — | — | **ALREADY_OK** |
| Request scope | ✅ | `/lab/scopes/request` | ✅ | ✅ | n/a | ✅ | HTTP | — | — | **ALREADY_OK** |
| Session scope | ✅ | `/lab/scopes/session` | ✅ (cookie jar gerekir) | ✅ | n/a | ✅ | HTTP | — | ~~Postman cookie jar adımı INFO'ya eklenmeliydi~~ | **FIXED** (Postman "11" 00 INFO'ya adım adım Cookie Jar/Cookies manager talimatı eklendi) |
| Application scope | ✅ | `/lab/scopes/application` | ✅ | ✅ | n/a | ✅ | HTTP | — | — | **ALREADY_OK** |
| Bean lifecycle | ✅ | `/api/labs/scopes/lifecycle` | ✅ | ✅ (uygulama başlangıcında) | n/a | ✅ | HTTP | — | — | **ALREADY_OK** |
| **AOP Self Invocation** | ✅ | ÖNCE: JUnit'e yönlendiriliyordu (docs/aop.md) | ✅ (HER ZAMAN vardı, ama KEŞFEDİLEMEZDİ) | ✅ | ✅ | ✅ | HTTP | — | ~~docs/aop.md JUnit'e yönlendiriyordu~~ | **FIXED** (bu turda: alan adları/proxy-info eklendi, docs düzeltildi) |
| @Transactional proxy behavior | ✅ (transactions.md/self-invocation) | `/api/labs/propagation/requires-new/*` | ✅ | ✅ | ✅ | ✅ | HTTP | ✅ | — | **ALREADY_OK** |
| @Async proxy behavior | ✅ | `/api/labs/async/*` | ✅ | ✅ | ✅ | ✅ | HTTP | — | — | **ALREADY_OK** |
| **Race condition** | ✅ | ÖNCE: sadece `RaceConditionTest` | ❌ → ✅ | ❌ → ✅ | ❌ → ✅ | ❌ → ✅ | ❌ → **HTTP** (`/api/labs/concurrency/counter/*`) | — | ~~HTTP endpoint yoktu~~ | **FIXED** (bu turda eklendi) |
| **synchronized** | ✅ | ÖNCE: sadece `SynchronizationTest` | ❌ → ✅ | ❌ → ✅ | ❌ → ✅ | ❌ → ✅ | ❌ → **HTTP** (`/api/labs/concurrency/synchronized/*`) | — | ~~HTTP endpoint yoktu~~ | **FIXED** |
| **ReentrantLock** | ✅ | ÖNCE: sadece `SynchronizationTest` | ❌ → ✅ | ❌ → ✅ | ❌ → ✅ | ❌ → ✅ | ❌ → **HTTP** (`/api/labs/concurrency/reentrant-lock/*`, THREAD/ATTEMPTING_LOCK/LOCK_ACQUIRED/WORK/LOCK_RELEASED logu + ölçülen `maxConcurrentHolders`) | — | ~~HTTP endpoint yoktu~~ | **FIXED** |
| **ReadWriteLock** | ✅ | ÖNCE: sadece `SynchronizationTest` | ❌ → ✅ | ❌ → ✅ | n/a | ✅ | ❌ → **HTTP** (`/api/labs/concurrency/read-write-lock/demo`) | — | ~~HTTP endpoint yoktu~~ | **FIXED** |
| **StampedLock** | ✅ | ÖNCE: sadece `SynchronizationTest` | ❌ → ✅ | ❌ → ✅ | n/a | ✅ | ❌ → **HTTP** (`/api/labs/concurrency/stamped-lock/demo`) | — | ~~HTTP endpoint yoktu~~ | **FIXED** |
| **volatile** | ✅ | ÖNCE: sadece `VolatileAndAtomicTest` | ❌ → ✅ | ❌ → ✅ | ✅ (yanlış varsayım çürütülüyor) | ✅ | ❌ → **HTTP** (`/api/labs/concurrency/volatile/*`) | — | ~~HTTP endpoint yoktu~~ | **FIXED** |
| AtomicInteger/CAS | ✅ | `VolatileAndAtomicTest` | ✅ | ✅ | ✅ | ✅ | HTTP (`/api/labs/concurrency/counter/good` + `/api/labs/concurrency/aba/{bad,good}`) | — | ~~ABA problemi sadece testte~~ | **FIXED** (`AtomicReference` CAS'in ABA'ya karşı KÖR olduğu, `AtomicStampedReference`'ın damga ile bunu YAKALADIĞI curl ile doğrulandı) |
| ThreadLocal | ✅ | `/api/labs/threadlocal/*` | ✅ | ✅ | ✅ | ✅ | HTTP | — | — | **ALREADY_OK** |
| ExecutorService | ✅ | `/api/labs/executor/*` | ✅ | ✅ | ✅ | ✅ | HTTP | — | ~~metrikler eksikti~~ | **FIXED** (corePoolSize/maximumPoolSize/queueCapacity/poolSize/activeCount/queueSize ARTIK HER response'ta) |
| ThreadPoolExecutor | ✅ | `/api/labs/executor/*` | ✅ | ✅ | ✅ | ✅ | HTTP | — | — | **FIXED** |
| Rejection Policies | ✅ | `/api/labs/executor/*` | ✅ | ✅ | ✅ (Abort/Discard/DiscardOldest) | ✅ (CallerRuns) | HTTP | — | ~~Discard/DiscardOldest yoktu~~ | **FIXED** (GERÇEK gözlemlenebilir `executedTaskIds` ile: discard→[1,2], discardOldest→[1,3]) |
| CompletableFuture | ✅ | `/api/labs/completable-future/*` | ✅ | ✅ | ✅ | ✅ | HTTP | — | ~~threadNames yoktu~~ | **FIXED** (`threadNames` + `distinctThreadCount`, GERÇEK `Thread.currentThread().getName()` ile toplanıyor) |
| Spring @Async | ✅ | `/api/labs/async/*` | ✅ | ✅ | ✅ | ✅ | HTTP | — | — | **ALREADY_OK** |
| N+1 | ✅ | `/api/labs/n-plus-one/*` | ✅ | ✅ | ✅ | ✅ | HTTP | ✅ | — | **ALREADY_OK** |
| Lazy/Eager | ✅ | ÖNCE: sadece `NPlusOneTest` | ❌ → ✅ | ❌ → ✅ | ❌ → ✅ | ❌ → ✅ | ❌ → **HTTP** (`/api/labs/fetch/{lazy,eager}/*`, GERÇEK `LazyInitializationException` + `SqlStatementRecorder` ile ölçülen eager sorgu sayısı) | ✅ (SQL log) | ~~HTTP endpoint yoktu~~ | **FIXED** (bu turda eklendi) |
| Cache Aside | ✅ | ÖNCE: sadece `CacheTest` | ❌ → ✅ | ❌ → ✅ | ❌ → ✅ | ❌ → ✅ | ❌ → **HTTP** (`/api/labs/cache/{bad,good}/*`, GERÇEK ölçülen `databaseReadCount` hit/miss farkı) | — | ~~HTTP endpoint yoktu~~ | **FIXED** (bu turda eklendi) |
| Circuit Breaker/Retry/Timeout/RateLimiter/Bulkhead/Fallback | ✅ | `/api/labs/resilience/*` | ✅ | ✅ | ✅ | ✅ | HTTP | — | ~~`/config` endpoint'i yoktu~~ | **FIXED** (`POST /api/labs/resilience/external/config {"failNext":N,"delayMs":N}` eklendi; `circuit-breaker` senaryosu ARTIK kendi içinde reset/setAlwaysFail YAPMIYOR, TAMAMEN bu config'e bağımlı - config'siz çağrılırsa devre hiç açılmıyor, curl ile HER İKİ durum da doğrulandı. Diğer 5 senaryo [Retry/RateLimiter/Bulkhead/Timeout/Fallback] hâlâ kendi kendine configure ediyor - işlevsel olarak eşdeğer, bilinçli olarak değiştirilmedi çünkü zaten JUnit-bağımsız gerçek HTTP lab'ları) |
| Authentication/Authorization | ✅ | `/api/labs/security/*` | ✅ | ✅ | n/a | ✅ | HTTP | — | — | **ALREADY_OK** |
| Design patterns (Strategy) | ✅ | `/api/labs/patterns/strategy/*` | ✅ | ✅ | ✅ | ✅ | HTTP | — | — | **ALREADY_OK** |
| Design patterns (Factory/Adapter/Observer/Builder/Proxy) | ✅ | ÖNCE: sadece `DesignPatternsTest` | ❌ → ✅ | ❌ → ✅ | ❌ → ✅ | ❌ → ✅ | ❌ → **HTTP** (`/api/labs/patterns/{factory,adapter,observer,builder,proxy}/*`, backend-gerçekçi: PaymentStrategyFactory/ExternalPaymentProviderAdapter/Order event Observer/OrderRequest Builder/GreeterProxyFactory JDK proxy) | — | ~~HTTP endpoint yoktu~~ | **FIXED** (bu turda eklendi) |

## Kategori B — IntelliJ'den Run/Debug edilebilen standalone `main()` runner GEREKEN konular

| Topic | Current impl | Current trigger (ÖNCE) | Runnable from IntelliJ? | Status |
|---|---|---|---|---|
| String Pool | ✅ | ÖNCE: sadece `JavaCoreTest` | ❌ → ✅ `StringPoolLabRunner.main()` | **FIXED** |
| Integer Cache | ✅ | ÖNCE: sadece `JavaCoreTest` | ❌ → ✅ `IntegerCacheLabRunner.main()` | **FIXED** |
| HashMap Internals | ✅ | ÖNCE: sadece `JavaCoreTest` | ❌ → ✅ `HashMapInternalsLabRunner.main()` (gerçek bir --add-opens engeliyle karşılaşıp çözüldü) | **FIXED** |
| Thread Lifecycle | ✅ | ÖNCE: sadece `ThreadLifecycleTest` | ❌ → ✅ `ThreadLifecycleLabRunner.main()` | **FIXED** |
| Virtual Threads | ✅ | ÖNCE: sadece `JavaCoreTest` | ❌ → ✅ `VirtualThreadLabRunner.main()` | **FIXED** |
| JVM Memory (heap/stack) | ✅ (yeni) | ÖNCE: sadece `docs/jvm-memory-model.md` (DOC) | ❌ → ✅ `JvmMemoryLabRunner.main()` | **FIXED** |
| GC Reachability | ✅ (yeni) | ÖNCE: sadece `docs/garbage-collection.md` (DOC) | ❌ → ✅ `GcReachabilityLabRunner.main()` | **FIXED** |
| Immutability | ✅ | ÖNCE: sadece `ImmutabilityTest` | ❌ → ✅ `ImmutabilityLabRunner.main()` (gerçek bir crash bulunup düzeltildi) | **FIXED** |
| equals/hashCode | ✅ | ÖNCE: sadece `EqualsHashCodeTest` | ❌ → ✅ `EqualsHashCodeLabRunner.main()` | **FIXED** |
| Collections | ✅ | ÖNCE: sadece `CollectionsConcurrencyTest` | ❌ → ✅ `CollectionsLabRunner.main()` | **FIXED** |
| Stream | ✅ | ÖNCE: sadece `StreamApiTest` | ❌ → ✅ `StreamLabRunner.main()` | **FIXED** |
| Parallel Stream | ✅ | ÖNCE: sadece `StreamApiTest` | ❌ → ✅ `ParallelStreamLabRunner.main()` | **FIXED** |
| Reflection | ✅ | ÖNCE: sadece `ReflectionTest` | ❌ → ✅ `ReflectionLabRunner.main()` | **FIXED** |
| BlockingQueue | ✅ | ÖNCE: sadece `CollectionsConcurrencyTest` | ❌ → ✅ `BlockingQueueLabRunner.main()` | **FIXED** |

**Kategori B SONUÇ: 14/14 runner oluşturuldu VE bizzat çalıştırılıp doğrulandı** (sadece
derleme değil - gerçek `java` çağrısıyla, çıktı incelendi). Bu süreçte 2 gerçek hata
bulunup düzeltildi: (1) Türkçe karakterlerin Windows'un Cp1254 konsol code page'i yüzünden
bozuk göründüğü (`LabRunnerPrint`'e UTF-8 zorlaması eklendi), (2) `ImmutabilityLabRunner`'ın
`List.copyOf()`'un sadece ayrı değil aynı zamanda immutable olduğunu hesaba katmadığı için
çöktüğü (düzeltildi, daha güçlü bir ders haline getirildi).

## Kategori C — Database labs (DBeaver ile gözlemlenebilir)

| Topic | Current impl | Real Postgres? | EXPLAIN ANALYZE karşılaştırması? | Status |
|---|---|---|---|---|
| Transactions/Rollback/Propagation/Isolation/Locking | ✅ | ✅ | n/a (bu konular için gerekli değil) | **ALREADY_OK** |
| N+1 / query behavior | ✅ | ✅ | n/a (query COUNT karşılaştırması var, EXPLAIN değil) | **ALREADY_OK** |
| Indexes | ✅ (docs/DB_LABS.md) | ✅ (bizzat çalıştırıldı, gerçek sayılar) | ✅ (Seq Scan vs Index Scan, gerçek EXPLAIN ANALYZE çıktısı) | **ALREADY_OK** |
| Normalization | ✅ (docs/DB_LABS.md, DOC) | n/a (kavramsal) | n/a | **ALREADY_OK** (DOC olması uygun - normalization çalıştırılabilir bir "deney" değil) |

---

## ÖZET

- **STILL_MISSING: YOK.** Orijinal denetimdeki tüm STILL_MISSING madde (Lazy/Eager, Cache-Aside,
  Design Patterns'ın 5 pattern'i) bu fazda HTTP+debugger lab'ına dönüştürüldü ve doğrulandı.
- **PARTIAL: YOK.** Bu turda TÜM PARTIAL madde de (Isolation phantom/dirty-read, Session scope
  cookie jar talimatı, AtomicInteger ABA, Executor/CompletableFuture eksik alanlar+Discard
  policy'leri, Resilience `/config` pattern'i) FIXED'e çevrildi.
- **FIXED (bu turda düzeltildi):** AOP (docs + alan adları + proxy-info), Race Condition,
  synchronized, ReentrantLock, ReadWriteLock, StampedLock, volatile (6 yeni HTTP lab) + 14
  Category B LabRunner (hepsi bizzat çalıştırılıp doğrulandı, 2 gerçek hata bulunup
  düzeltildi).
- **Doküman keşfedilebilirlik denetimi TAMAMLANDI:** AOP'daki AYNI sorun (doc'un SADECE
  JUnit'e işaret etmesi, HTTP lab'a hiç değinmemesi) bir Explore subagent ile TÜM
  `docs/*.md` dosyalarında sistematik olarak arandı. 16 doküman (`async.md`,
  `completable-future.md`, `atomic.md`, `bean-scopes.md`, `design-patterns.md`,
  `exceptions.md`, `executor-service.md`, `isolation.md`, `java-locks.md`, `n-plus-one.md`,
  `optimistic-locking.md`, `persistence-context.md`, `pessimistic-locking.md`,
  `propagation.md`, `transactions.md`, `spring-configuration.md`) NEEDS_FIX olarak
  işaretlendi ve HEPSİ düzeltildi — her biri artık açılışta "Birincil öğrenme arayüzü —
  Postman + IntelliJ debugger" bloğuyla GERÇEK `/api/labs/...` endpoint'lerini listeliyor,
  JUnit test sınıfları "ikincildir, birincil değil" ifadesiyle demote edildi. 3 doküman
  zaten OK'ti (`aop.md`, `resilience.md`, `http-and-security.md`); 3 doküman (`distributed-
  systems.md`, `garbage-collection.md`, `jvm-memory-model.md`) için HTTP lab yok (Kategori B/
  kavramsal konular, uygun). `./mvnw test` ile regresyon doğrulandı (171/171, sadece doküman
  değişikliği).

Devam eden çalışma için bkz. `NEXT_WORK.md`.
