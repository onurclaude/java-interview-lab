# Handwritten Notes Audit

Kullanıcının el yazması mülakat notlarından verdiği TAM konu listesi, dört durumdan biriyle
sınıflandırıldı:

- **IMPLEMENTED_INTERACTIVE** — Kategori A: gerçek `/api/labs/...` HTTP endpoint'i + IntelliJ
  debugger ile izlenebilir (bkz. `docs/DEBUGGER_LABS.md`).
- **IMPLEMENTED_RUNNER** — Kategori B: IntelliJ'de `main()` ile Run/Debug edilebilen bir
  `LabRunner` sınıfı.
- **IMPLEMENTED_BUT_NOT_INTERACTIVE** — kod GERÇEKTEN var (bad/good sınıfları) ama SADECE
  JUnit test ile doğrulanıyor, ne HTTP ne Runner var; ya da SADECE bir `docs/*.md` dosyasında
  kavramsal olarak anlatılıyor.
- **MISSING** — repoda hiçbir şekilde yok.

Bu denetim, kaynak kodu (`grep`/`find` ile) GERÇEKTEN tarayarak yapıldı — varsayımla değil.

---

## Spring

| Konu | Durum | Kanıt |
|---|---|---|
| IoC | IMPLEMENTED_BUT_NOT_INTERACTIVE | `docs/spring-configuration.md` (kavramsal); somut kanıtı zaten TÜM projedeki constructor injection |
| Bean lifecycle | **IMPLEMENTED_INTERACTIVE** | `GET /api/labs/scopes/lifecycle`, DEBUGGER_LABS #15 |
| Bean scopes | **IMPLEMENTED_INTERACTIVE** | `/api/labs/scopes/*`, `/lab/scopes/*`, DEBUGGER_LABS #10-14 |
| AOP | **IMPLEMENTED_INTERACTIVE** | `/api/labs/aop/*`, DEBUGGER_LABS #16 |
| proxy/self invocation | **IMPLEMENTED_INTERACTIVE** | AOP + Async + Propagation REQUIRES_NEW hepsi bunu kapsıyor |
| JDK proxy | **IMPLEMENTED_INTERACTIVE** | `/api/labs/patterns/proxy/*` (`GreeterProxyFactory`, gerçek `java.lang.reflect.Proxy`) |
| CGLIB proxy | **IMPLEMENTED_INTERACTIVE** | `GET /api/labs/aop/proxy-info` — gerçek `AopUtils.isCglibProxy()` |
| @Transactional | **IMPLEMENTED_INTERACTIVE** | `/api/labs/transaction/*` |
| transaction propagation | **IMPLEMENTED_INTERACTIVE** | `/api/labs/propagation/*` |
| isolation | **IMPLEMENTED_INTERACTIVE** | `/api/labs/isolation/*` (non-repeatable/phantom/dirty) |
| rollback behavior | **IMPLEMENTED_INTERACTIVE** | `/api/labs/transaction/*` |

## Concurrency

| Konu | Durum | Kanıt |
|---|---|---|
| Thread | IMPLEMENTED_RUNNER | `ThreadLifecycleLabRunner` |
| Runnable | IMPLEMENTED_BUT_NOT_INTERACTIVE | altyapı olarak HER YERDE kullanılıyor (executor/race/lock lab'ları); ayrı bir "Runnable nedir" gösterimi yok |
| ExecutorService | **IMPLEMENTED_INTERACTIVE** | `/api/labs/executor/*`, DEBUGGER_LABS #26-29 |
| Future | IMPLEMENTED_BUT_NOT_INTERACTIVE | optimistic/pessimistic/deadlock lab'larında `Future<?>` kullanılıyor ama ayrı bir "Future API" gösterimi yok (CompletableFuture ayrı ele alınıyor) |
| CompletableFuture | **IMPLEMENTED_INTERACTIVE** | `/api/labs/completable-future/*`, DEBUGGER_LABS #30 |
| ThreadPoolExecutor | **IMPLEMENTED_INTERACTIVE** | `/api/labs/executor/*` |
| ThreadLocal | **IMPLEMENTED_INTERACTIVE** | `/api/labs/threadlocal/*`, DEBUGGER_LABS #25 |
| synchronized | **IMPLEMENTED_INTERACTIVE** | `/api/labs/concurrency/synchronized/*` |
| volatile | **IMPLEMENTED_INTERACTIVE** | `/api/labs/concurrency/volatile/*` |
| Atomic* | **IMPLEMENTED_INTERACTIVE** | `/api/labs/concurrency/counter/good`, `/aba/*` |
| CAS | **IMPLEMENTED_INTERACTIVE** | `/api/labs/concurrency/aba/*` (CAS'in TAM OLARAK nasıl çalıştığını/başarısız olduğunu gösterir) |
| deadlock | **IMPLEMENTED_INTERACTIVE (bu turda düzeltildi)** | ÖNCEDEN sadece `PessimisticLockingTest`'te idi (MISSING'e yakın) — bu turda `/api/labs/deadlock/*` eklendi, GERÇEK Postgres `"deadlock detected"` hatasıyla doğrulandı |
| race condition | **IMPLEMENTED_INTERACTIVE** | `/api/labs/concurrency/counter/*` |
| thread lifecycle | IMPLEMENTED_RUNNER | `ThreadLifecycleLabRunner` |
| virtual threads | IMPLEMENTED_RUNNER | `VirtualThreadLabRunner` |

## Collections

| Konu | Durum | Kanıt |
|---|---|---|
| HashMap internals | IMPLEMENTED_RUNNER | `HashMapInternalsLabRunner` |
| hashCode/equals | IMPLEMENTED_RUNNER | `EqualsHashCodeLabRunner` |
| collision | IMPLEMENTED_RUNNER | `HashMapInternalsLabRunner` (`CollidingKey`) |
| treeification | IMPLEMENTED_RUNNER | `HashMapInternalsLabRunner` |
| ConcurrentHashMap | IMPLEMENTED_BUT_NOT_INTERACTIVE | `ConcurrentHashMapCacheService`/`AtomicComputeIfAbsentCacheService` var, SADECE `CollectionsConcurrencyTest`'te (JUnit) — HTTP/Runner'a taşınmadı |
| ArrayList | IMPLEMENTED_RUNNER | `CollectionsLabRunner` (fail-fast iterator) |
| LinkedList | MISSING | ayrı bir gösterim yok |
| Queue | IMPLEMENTED_RUNNER | `BlockingQueueLabRunner` + `CollectionsLabRunner` (ArrayDeque as queue) |
| Deque | IMPLEMENTED_RUNNER | `CollectionsLabRunner` — `ArrayDeque`'in HEM stack HEM queue olarak kullanımı GERÇEKTEN gösteriliyor |
| Stack (LIFO) | IMPLEMENTED_RUNNER | `CollectionsLabRunner` (`ArrayDeque` ile, legacy `Stack` sınıfının neden tercih edilmediği açıklanıyor) |
| FIFO/LIFO | IMPLEMENTED_RUNNER | `CollectionsLabRunner`'da AYNI `ArrayDeque` örneğiyle ikisi de gösteriliyor |
| iterator behavior / fail-fast | IMPLEMENTED_RUNNER | `CollectionsLabRunner` |
| CopyOnWriteArrayList | IMPLEMENTED_BUT_NOT_INTERACTIVE | `LockingPrimitivesLabController`'da altyapı olarak kullanılıyor (lifecycleLog), ama kendi başına bir "CopyOnWriteArrayList ÖZELLİĞİ" demosu yok |
| blocking queues | IMPLEMENTED_RUNNER | `BlockingQueueLabRunner` |

## JVM

| Konu | Durum | Kanıt |
|---|---|---|
| JDK/JRE/JVM | IMPLEMENTED_BUT_NOT_INTERACTIVE | `docs/jvm-memory-model.md` (kavramsal) |
| heap | IMPLEMENTED_RUNNER | `JvmMemoryLabRunner` |
| stack | IMPLEMENTED_RUNNER | `JvmMemoryLabRunner` |
| metaspace | IMPLEMENTED_BUT_NOT_INTERACTIVE | `docs/jvm-memory-model.md`'de anlatılıyor, `ReflectionLabRunner`'ın TRY bölümünde metaspace/reflection ayrımına değiniliyor ama ayrı bir ölçüm/demo yok |
| GC | IMPLEMENTED_RUNNER | `GcReachabilityLabRunner` (reachability kısmı GERÇEKTEN çalıştırılıyor) |
| young/old generation | IMPLEMENTED_BUT_NOT_INTERACTIVE | `docs/garbage-collection.md` (kavramsal, GC algoritma ayarları demo edilemez) |
| stop-the-world | IMPLEMENTED_BUT_NOT_INTERACTIVE | `docs/garbage-collection.md` |
| JIT | MISSING | repoda hiç yok |
| class loading | MISSING | repoda hiç yok |
| String pool | IMPLEMENTED_RUNNER | `StringPoolLabRunner` |
| Integer cache | IMPLEMENTED_RUNNER | `IntegerCacheLabRunner` |
| memory model (JMM) | IMPLEMENTED_BUT_NOT_INTERACTIVE + IMPLEMENTED_INTERACTIVE (dolaylı) | `docs/jvm-memory-model.md` (kavramsal) + `volatile`/`synchronized` lab'ları happens-before'ı FİİLEN gösteriyor |

## JPA/Hibernate

| Konu | Durum | Kanıt |
|---|---|---|
| N+1 | **IMPLEMENTED_INTERACTIVE** | `/api/labs/n-plus-one/*` |
| fetch types (LAZY/EAGER) | **IMPLEMENTED_INTERACTIVE** | `/api/labs/fetch/*` |
| EntityGraph | **IMPLEMENTED_INTERACTIVE** | `/api/labs/n-plus-one/good` (`EntityGraphOrderService`) |
| native query | MISSING | repoda `nativeQuery=true` hiç kullanılmamış |
| transactional readOnly | IMPLEMENTED_BUT_NOT_INTERACTIVE | birçok `@Transactional(readOnly=true)` kullanımı var (ör. `LazyInitializationDemoService`) ama "readOnly'nin GERÇEK etkisi" (flush/dirty-checking atlanması) için AYRI bir BAD/GOOD karşılaştırması yok |
| dirty checking | **IMPLEMENTED_INTERACTIVE** | `/api/labs/persistence/*` |
| optimistic locking | **IMPLEMENTED_INTERACTIVE** | `/api/labs/optimistic/*` |
| pessimistic locking | **IMPLEMENTED_INTERACTIVE** | `/api/labs/pessimistic/*` |

## Database

| Konu | Durum | Kanıt |
|---|---|---|
| ACID | IMPLEMENTED_BUT_NOT_INTERACTIVE | `docs/DB_LABS.md` (kavramsal), somut kanıtı zaten transaction/isolation lab'ları |
| CAP | MISSING | hiçbir doc'ta yok |
| isolation anomalies (dirty/non-repeatable/phantom) | **IMPLEMENTED_INTERACTIVE** | `/api/labs/isolation/*` |
| lost update | **IMPLEMENTED_INTERACTIVE** | `/api/labs/optimistic/bad`, `/api/labs/pessimistic/bad` |

## Web/Security

| Konu | Durum | Kanıt |
|---|---|---|
| REST/HTTP methods/idempotency | IMPLEMENTED_BUT_NOT_INTERACTIVE | `docs/http-and-security.md` (kavramsal) |
| 401 vs 403 | **IMPLEMENTED_INTERACTIVE** | `/api/labs/security/{protected,admin-only}` — GERÇEK status kodları |
| authentication vs authorization | **IMPLEMENTED_INTERACTIVE** | aynı lab |
| JWT | **IMPLEMENTED_INTERACTIVE** | `/api/labs/security/login` |
| filters/interceptors | **IMPLEMENTED_INTERACTIVE** | `JwtAuthenticationFilter` — DEBUGGER_LABS #41'de breakpoint manifesti var |
| @Controller/@RestController | IMPLEMENTED_BUT_NOT_INTERACTIVE | kavramsal fark, TÜM projede zaten `@RestController` kullanılıyor |

## Resilience

| Konu | Durum | Kanıt |
|---|---|---|
| retry | **IMPLEMENTED_INTERACTIVE** | `/api/labs/resilience/retry*` |
| timeout | **IMPLEMENTED_INTERACTIVE** | `/api/labs/resilience/timeout` |
| circuit breaker | **IMPLEMENTED_INTERACTIVE** | `/api/labs/resilience/circuit-breaker` + `/external/config` |
| fallback | **IMPLEMENTED_INTERACTIVE** | `/api/labs/resilience/fallback` |
| bulkhead | **IMPLEMENTED_INTERACTIVE** | `/api/labs/resilience/bulkhead` |
| rate limiter | **IMPLEMENTED_INTERACTIVE** | `/api/labs/resilience/rate-limiter` |

## Design Patterns

| Konu | Durum | Kanıt |
|---|---|---|
| Factory | **IMPLEMENTED_INTERACTIVE** | `/api/labs/patterns/factory/*` |
| Strategy | **IMPLEMENTED_INTERACTIVE** | `/api/labs/patterns/strategy/*` |
| Builder | **IMPLEMENTED_INTERACTIVE** | `/api/labs/patterns/builder/*` |
| Singleton | IMPLEMENTED_BUT_NOT_INTERACTIVE | `patterns.singleton.ClassicSingleton`, sadece `DesignPatternsTest`'te |
| Observer | **IMPLEMENTED_INTERACTIVE** | `/api/labs/patterns/observer/*` |
| Adapter | **IMPLEMENTED_INTERACTIVE** | `/api/labs/patterns/adapter/*` |
| Decorator | IMPLEMENTED_BUT_NOT_INTERACTIVE | `patterns.decorator.*`, sadece `DesignPatternsTest`'te |
| Proxy | **IMPLEMENTED_INTERACTIVE** | `/api/labs/patterns/proxy/*` |
| Template Method | IMPLEMENTED_BUT_NOT_INTERACTIVE | `patterns.templatemethod.*`, sadece `DesignPatternsTest`'te |

---

## ÖZET

- **IMPLEMENTED_INTERACTIVE:** ~38 madde (Kategori A'nın büyük çoğunluğu, deadlock dahil).
- **IMPLEMENTED_RUNNER:** ~14 madde (Kategori B'nin tamamı, bazı Collections alt maddeleri dahil).
- **IMPLEMENTED_BUT_NOT_INTERACTIVE:** ~16 madde — bunların çoğu (IoC, JDK/JRE/JVM,
  young/old generation, stop-the-world, ACID, REST semantics, @Controller farkı, ConcurrentHashMap,
  Future API, CopyOnWriteArrayList, Singleton/Decorator/Template Method pattern'leri, readOnly'nin
  gerçek etkisi) SAF KAVRAMSAL bilgi ya da kullanıcının Kategori A/B/C listesinde EXPLICIT
  istenmemiş konular — HTTP/Runner'a zorlamak anlamsız üretim olurdu. Ama BUNLAR dürüstçe
  "DONE" DEĞİL, "NOT INTERACTIVE" olarak işaretlendi.
- **MISSING:** 5 madde — **LinkedList** (ayrı gösterim yok, ArrayDeque zaten Queue/Stack'i
  kapsıyor), **native query** (hiç `nativeQuery=true` kullanımı yok), **CAP teoremi** (hiçbir
  doc'ta yok), **JIT** (hiç yok), **class loading** (hiç yok). Bunlar GERÇEKTEN eksik — kullanıcı
  isterse eklenebilir, ama şu ana kadar dokunulmadı (düşük öncelik: ya çok küçük/spesifik bir
  API detayı, ya da JVM iç mekanizması olup canlı bir HTTP/Runner demosu ile gösterilmesi doğal
  olmayan saf teorik bilgi).

**Bu denetimin SONUCUNDA gerçek bir eksik bulunup DÜZELTİLDİ: Deadlock.** Diğer MISSING
maddeler kullanıcının Kategori A/B/C spesifikasyonunun dışında kalan, daha küçük/teorik
konulardır — kullanıcı özellikle isterse ayrıca eklenir.
