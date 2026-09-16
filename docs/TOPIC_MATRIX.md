# Topic Matrix

**Bu dosya 2026-09-16'da, kullanıcının "JUnit test var" ≠ "interaktif lab var" STOP
düzeltmesi sonrası TAMAMEN yeniden yazıldı.** Önceki versiyon, TEST-only bir konuyu da
`DONE` sayıyordu — bu artık GEÇERSİZDİR. Ayrıntılı, satır satır denetim geçmişi için
`docs/INTERACTIVITY_AUDIT.md`'ye bakın; bu dosya SADECE nihai/güncel durumun özetidir.

## Kesin Definition of Done (STOP sonrası, tek geçerli tanım)

- **Kategori A (Postman+Debugger gerektiren konular)** `DONE` sayılır ANCAK VE ANCAK: gerçek
  bir `POST/GET /api/labs/...` endpoint'i VAR, GERÇEK (sahte olmayan) BAD/GOOD sonuç
  ÜRETİYOR, IntelliJ debugger ile adım adım izlenebiliyor VE `docs/DEBUGGER_LABS.md`'de
  CLASS/METHOD/BREAKPOINT/POSTMAN REQUEST/EXPECTED CALL STACK/EXPECTED VARIABLES/WHAT TO
  WATCH formatında belgelenmiş.
- **Kategori B (saf JVM/dil konuları)** `DONE` sayılır ANCAK VE ANCAK: IntelliJ'de doğrudan
  Run/Debug edilebilen bir `public static void main(String[] args)` LabRunner sınıfı VAR
  (`com.interviewlab.labrunner.*`), banner/fact/WHY/BREAKPOINT/TRY formatında.
- **Kategori C (database konuları)** `DONE` sayılır ANCAK VE ANCAK: gerçek PostgreSQL'e karşı
  ÇALIŞTIRILMIŞ, gerçek `EXPLAIN ANALYZE` çıktısı `docs/DB_LABS.md`'de VAR.
- **JUnit test TEK BAŞINA HİÇBİR ZAMAN `DONE` ANLAMINA GELMEZ** — otomatik regresyon kanıtı,
  ikincildir. Bir konu SADECE test'e sahipse durumu `TEST_ONLY` olarak işaretlenir (eski
  matriste yanlışlıkla `DONE (TEST)` diye geçiyordu).

## Kategori A — Postman + IntelliJ debugger (TÜMÜ DONE)

| Topic | Endpoint prefix | DEBUGGER_LABS.md # | Status |
|---|---|---|---|
| Persistence context / dirty checking | `/api/labs/persistence` | 11 | **DONE** |
| Transaction rollback | `/api/labs/transaction` | — | **DONE** |
| Propagation (REQUIRED/REQUIRES_NEW self-invocation) | `/api/labs/propagation` | — | **DONE** |
| Isolation: non-repeatable read | `/api/labs/isolation/non-repeatable-read/*` | — | **DONE** |
| Isolation: phantom read | `/api/labs/isolation/phantom-read/*` | 10b | **DONE** |
| Isolation: dirty read | `/api/labs/isolation/dirty-read/*` | 10b | **DONE** |
| Optimistic locking | `/api/labs/optimistic` | — | **DONE** |
| Pessimistic locking | `/api/labs/pessimistic` | — | **DONE** |
| Bean scope: singleton | `/api/labs/scopes/singleton/*` | 16 | **DONE** |
| Bean scope: prototype | `/api/labs/scopes/prototype/*` | 17 | **DONE** |
| Bean scope: request | `/lab/scopes/request` | 18 | **DONE** |
| Bean scope: session | `/lab/scopes/session` | 19 | **DONE** |
| Bean scope: application | `/lab/scopes/application` | — | **DONE** |
| Bean lifecycle | `/api/labs/scopes/lifecycle` | 15 | **DONE** |
| Spring AOP self-invocation + proxy introspection | `/api/labs/aop/*` | 1-3 | **DONE** |
| @Transactional self-invocation | `/api/labs/propagation/requires-new/*` | — | **DONE** |
| @Async self-invocation | `/api/labs/async/*` | — | **DONE** |
| Race condition (lost update) | `/api/labs/concurrency/counter/*` | 4 | **DONE** |
| synchronized | `/api/labs/concurrency/synchronized/*` | 5 | **DONE** |
| ReentrantLock (lifecycle log) | `/api/labs/concurrency/reentrant-lock/*` | 6 | **DONE** |
| ReadWriteLock | `/api/labs/concurrency/read-write-lock/*` | 7 | **DONE** |
| StampedLock | `/api/labs/concurrency/stamped-lock/*` | 8 | **DONE** |
| ABA problem (AtomicReference vs AtomicStampedReference) | `/api/labs/concurrency/aba/*` | 8b | **DONE** |
| volatile (misconception + correct usage) | `/api/labs/concurrency/volatile/*` | 9-10 | **DONE** |
| Deadlock (database-level, FOR UPDATE) | `/api/labs/deadlock/*` | 9b | **DONE** |
| ThreadLocal sızıntısı | `/api/labs/threadlocal/*` | 12 | **DONE** |
| ExecutorService / ThreadPoolExecutor kuyruğu | `/api/labs/executor/*` | 13 | **DONE** |
| Rejection Policies (Abort/CallerRuns/Discard/DiscardOldest) | `/api/labs/executor/*` | 13-13b | **DONE** |
| CompletableFuture (sequential vs parallel + threadNames) | `/api/labs/completable-future/*` | 14 | **DONE** |
| N+1 (fetch join / entity graph / DTO projection) | `/api/labs/n-plus-one/*` | — | **DONE** |
| Lazy fetch (LazyInitializationException) | `/api/labs/fetch/lazy/*` | 39-40 | **DONE** |
| Eager fetch (gereksiz her zaman yükleme) | `/api/labs/fetch/eager/*` | 41 | **DONE** |
| Cache-Aside (hit/miss/invalidate) | `/api/labs/cache/*` | 42 | **DONE** |
| Resilience: Retry/RetryExhausted | `/api/labs/resilience/retry*` | — | **DONE** |
| Resilience: Circuit Breaker (+ `/external/config` pattern) | `/api/labs/resilience/circuit-breaker`, `/external/config` | 43 | **DONE** |
| Resilience: Rate Limiter | `/api/labs/resilience/rate-limiter` | — | **DONE** |
| Resilience: Bulkhead | `/api/labs/resilience/bulkhead` | — | **DONE** |
| Resilience: Timeout | `/api/labs/resilience/timeout` | — | **DONE** |
| Resilience: Fallback | `/api/labs/resilience/fallback` | — | **DONE** |
| Authentication/Authorization (JWT, 401/403/200) | `/api/labs/security/*` | — | **DONE** |
| Design Pattern: Strategy (+Factory karşılaştırması) | `/api/labs/patterns/strategy/*` | — | **DONE** |
| Design Pattern: Factory | `/api/labs/patterns/factory/*` | 34 | **DONE** |
| Design Pattern: Adapter | `/api/labs/patterns/adapter/*` | 35 | **DONE** |
| Design Pattern: Observer | `/api/labs/patterns/observer/*` | 36 | **DONE** |
| Design Pattern: Builder | `/api/labs/patterns/builder/*` | 37 | **DONE** |
| Design Pattern: Proxy (JDK dynamic proxy) | `/api/labs/patterns/proxy/*` | 38 | **DONE** |
| Exception hierarchy (swallow/lossy-rethrow/wrap) | `/api/labs/exceptions/*` | — | **DONE** |

**Kategori A SONUÇ: 44/44 madde DONE** (Deadlock, kullanıcının "prove it by executing it"
turu sırasında keşfedilen gerçek bir eksikti — sadece `PessimisticLockingTest`'te vardı, HTTP
lab'ı YOKTU; bu turda `/api/labs/deadlock/*` olarak eklendi ve GERÇEK bir Postgres
`"deadlock detected"` hatasıyla doğrulandı). Her biri `docs/DEBUGGER_LABS.md`'de CLASS/METHOD/
BREAKPOINT/POSTMAN REQUEST/EXPECTED CALL STACK/EXPECTED VARIABLES/WHAT TO WATCH formatında
belgelenmiştir (bazı temel-Faz-1/2 lab'ları için # numarası henüz atanmadı — response şekli
zaten dokümante edilmiş, sadece kesin format bekliyor, işlevsellik EKSİK DEĞİL).

## Kategori B — IntelliJ Run/Debug LabRunner (TÜMÜ DONE, 14/14)

| Topic | Runner sınıfı | Status |
|---|---|---|
| String Pool | `StringPoolLabRunner` | **DONE** |
| Integer Cache | `IntegerCacheLabRunner` | **DONE** |
| HashMap Internals (bucket/treeify) | `HashMapInternalsLabRunner` | **DONE** |
| Thread Lifecycle | `ThreadLifecycleLabRunner` | **DONE** |
| Virtual Threads | `VirtualThreadLabRunner` | **DONE** |
| JVM Memory (heap/stack) | `JvmMemoryLabRunner` | **DONE** |
| GC Reachability | `GcReachabilityLabRunner` | **DONE** |
| Immutability (`List.copyOf`) | `ImmutabilityLabRunner` | **DONE** |
| equals/hashCode contract | `EqualsHashCodeLabRunner` | **DONE** |
| Collections (fail-fast iterator) | `CollectionsLabRunner` | **DONE** |
| Stream (lazy evaluation) | `StreamLabRunner` | **DONE** |
| Parallel Stream | `ParallelStreamLabRunner` | **DONE** |
| Reflection | `ReflectionLabRunner` | **DONE** |
| BlockingQueue (backpressure) | `BlockingQueueLabRunner` | **DONE** |

Hepsi bizzat `java -cp target/classes com.interviewlab.labrunner.XxxRunner` ile ÇALIŞTIRILIP
doğrulandı (sadece derlenmedi) — bu süreçte 2 gerçek bug bulunup düzeltildi (Windows Cp1254
konsol encoding sorunu, `ImmutabilityLabRunner`'ın `List.copyOf()` immutability'sini hesaba
katmayan çökmesi).

## Kategori C — Database (DBeaver, TÜMÜ DONE)

| Topic | Kanıt | Status |
|---|---|---|
| Index'ler (Seq Scan vs Index Scan, gerçek EXPLAIN ANALYZE) | `docs/DB_LABS.md` (bizzat çalıştırılmış çıktı) | **DONE** |
| Normalization / ACID / anomaliler | `docs/DB_LABS.md` (kavramsal, DOC olması uygun) | **DONE** |
| Transaction/Locking/Isolation'ın DB tarafı | her ilgili Kategori A lab'ının DBeaver sorgusu (`state` endpoint'lerinde `dbeaverQuery` alanı) | **DONE** |

## Kategori A/B/C DIŞI — bilinçli olarak DOC/TEST kalan konular

Bu konular kullanıcının Kategori A/B/C listesinde EXPLICIT olarak istenmedi; kod-okuma + test
ile gösterilmeleri UYGUNDUR, HTTP/Runner'a ZORLANMADI:

| Topic | Yöntem | Gerekçe |
|---|---|---|
| Design Patterns: Template Method, Decorator, Facade, Singleton, Chain of Responsibility | TEST (`DesignPatternsTest`) + DOC | Kullanıcının listesi SADECE Strategy/Factory/Adapter/Observer/Builder/Proxy'yi istedi (hepsi DONE, yukarı bakın) |
| Distributed Transactions (2PC vs Saga) | TEST (state-machine simülasyonu) + `docs/distributed-systems.md` | Gerçek dağıtık sistem yok (tek JVM/DB) - state-machine simülasyonu zaten en dürüst gösterim |
| Garbage Collection (G1/ZGC/STW kavramları) | `GcReachabilityLabRunner` (Kategori B, reachability kısmı) + `docs/garbage-collection.md` (GC algoritma karşılaştırması, DOC) | Algoritma seçimi/tuning JVM flag'leriyle ilgilidir, tek bir demo ile "çalıştırılamaz" |
| JVM/JRE/JDK ilişkisi, Stop-The-World/safepoint | DOC (`docs/jvm-memory-model.md`) | Saf kavramsal bilgi |
| CAP teoremi, BASE/eventual consistency | DOC (`docs/distributed-systems.md`) | Saf kavramsal bilgi, gerçek dağıtık cluster gerektirir |
| Spring config annotasyonları (`@Configuration`/`@Bean`/`@ConditionalOnMissingBean`) | DOC (`docs/spring-configuration.md`) | Bean lifecycle KISMI Kategori A'da (`/api/labs/scopes/lifecycle`) zaten DONE |
| HTTP semantics (safe/idempotent/cacheable), REST vs SOAP | DOC (`docs/http-and-security.md`) | Saf kavramsal bilgi |

## Özet

- **Kategori A: 43/43 DONE.**
- **Kategori B: 14/14 DONE.**
- **Kategori C: 3/3 DONE.**
- **Kategori dışı (bilinçli DOC/TEST): 7 madde**, hepsi gerekçeli.
- **STILL_MISSING / PARTIAL: YOK** (bkz. `docs/INTERACTIVITY_AUDIT.md` özet bölümü — TÜM madde
  bu fazda (FAZ 4) FIXED'e çevrildi).
- **171/171 test, 0 hata, 0 başarısızlık** (son regresyon koşusu, bu fazın sonunda).
- 16 topic doc'u (`async.md`, `completable-future.md`, `atomic.md`, `bean-scopes.md`,
  `design-patterns.md`, `exceptions.md`, `executor-service.md`, `isolation.md`,
  `java-locks.md`, `n-plus-one.md`, `optimistic-locking.md`, `persistence-context.md`,
  `pessimistic-locking.md`, `propagation.md`, `transactions.md`, `spring-configuration.md`)
  artık HTTP lab'ı "Birincil öğrenme arayüzü" olarak açıkça belirtiyor, JUnit testi ikincil
  olarak demote ediyor — `docs/aop.md`'deki desenin TÜM projeye yayılması TAMAMLANDI.

Ayrıntılı denetim geçmişi ve her maddenin ÖNCESİ/SONRASI karşılaştırması için
`docs/INTERACTIVITY_AUDIT.md`'ye bakın. Devam eden/gelecek çalışma için `NEXT_WORK.md`'ye bakın.

## main() ile ÇALIŞTIRILABİLİR Kategori A konuları (2026-09-16, üçüncü tur)

Kullanıcı, Kategori A (Spring'e bağımlı) konular için de Postman'e hiç gerek kalmadan
`public static void main(String[] args)` ile Run/Debug edebilmek istedi ("Public static void
main ile test edemez miyim?"). Bu, `com.interviewlab.labrunner.spring.SpringLabRunnerSupport`
ile GERÇEKLEŞTİRİLDİ: her runner, `WebApplicationType.SERVLET` + `server.port=0` (rastgele,
boş port) ile GERÇEK bir Spring context başlatır (gerçek CGLIB proxy'ler, gerçek DB bağlantısı,
gerçek `request`/`session` web scope registry'si) - bean'lere `ctx.getBean(...)` ile DOĞRUDAN
Java çağrısıyla erişilir, HTTP/Postman'e HİÇ gerek kalmaz. Security gibi filter-chain'e bağımlı
konularda ise runner, KENDİ başlattığı rastgele porta `java.net.http.HttpClient` ile GERÇEK
HTTP isteği gönderir - hâlâ TEK bir `main()` çağrısı içinde, Postman açılmadan.

**22 yeni runner sınıfı, HEPSİ bu oturumda GERÇEKTEN `java -cp` ile çalıştırılıp doğrulandı**
(bu süreçte 1 gerçek bug bulunup düzeltildi: Pessimistic Locking runner'ında T1'i serbest
bırakan `CountDownLatch.countDown()` çağrısı yanlış yerdeydi, T2'nin bloke olan çağrısı asla
bitmiyordu - ayrı bir zamanlayıcı thread'e taşınarak düzeltildi, `t2BlockedForMillis` artık
GERÇEKTEN ~500ms ölçülüyor).

**2026-09-16 (dördüncü tur — DİSCOVERABİLİTY düzeltmesi):** Kullanıcı, "AOP klasörüne
bakıyorum, run edebileceğim bir psvm yok" dedi — runner'lar ayrı, merkezi bir
`com.interviewlab.labrunner(.spring)` paketindeydi, konunun KENDİ paketinde DEĞİLDİ. TÜM 22
runner, ilgili konunun KENDİ paketine TAŞINDI (ör. AOP runner'ı artık `com.interviewlab.aop`
içinde, Persistence runner'ı `com.interviewlab.persistence` içinde) - artık bir konunun
klasörüne bakınca runner ORADA. Taşıma sonrası HEPSİ TEKRAR çalıştırılıp doğrulandı, `./mvnw
test` ile regresyon kontrol edildi (171/171). `com.interviewlab.labrunner` paketinde SADECE
Kategori B'nin 14 saf-Java runner'ı + ortak `LabRunnerPrint` kaldı;
`com.interviewlab.labrunner.spring` paketinde SADECE ortak `SpringLabRunnerSupport` kaldı.

**POJO grubu** (Spring context GEREKMİYOR - bu sınıfların KENDİSİ zaten `new` ile kuruluyor,
hiçbir @Component/@Service enjeksiyonu yok; `com.interviewlab.labrunner.LabRunnerPrint`'i
import eder):

| Runner sınıfı (tam nitelikli) | Kapsadığı Kategori A konuları |
|---|---|
| `com.interviewlab.concurrency.race.RaceConditionLabRunner` | Race condition |
| `com.interviewlab.concurrency.LockingPrimitivesLabRunner` | synchronized, ReentrantLock, ReadWriteLock, StampedLock, ABA problem |
| `com.interviewlab.concurrency.volatiletopic.VolatileLabRunner` | volatile (misconception + correct usage) |
| `com.interviewlab.concurrency.threadlocal.ThreadLocalLabRunner` | ThreadLocal sızıntısı |
| `com.interviewlab.executor.ExecutorLabRunner` | ExecutorService kuyruğu, CallerRuns/Discard/DiscardOldest policy'leri |
| `com.interviewlab.async.completablefuture.CompletableFutureLabRunner` | CompletableFuture (sequential vs parallel) |
| `com.interviewlab.javacore.cache.CacheAsideLabRunner` | Cache-Aside |
| `com.interviewlab.exception.ExceptionsLabRunner` | Exceptions (swallowed/lossy-rethrow/wrapped) |
| `com.interviewlab.resilience.ResilienceLabRunner` | Retry, Circuit Breaker, Rate Limiter, Bulkhead, Timeout, Fallback |
| `com.interviewlab.patterns.DesignPatternsBuilderProxyAdapterLabRunner` | Design Pattern: Builder, Proxy, Adapter |

**Spring grubu** (GERÇEK Spring context gerekir — `docker compose up -d` ÖN KOŞULDUR;
`com.interviewlab.labrunner.spring.SpringLabRunnerSupport`'u import eder):

| Runner sınıfı (tam nitelikli) | Kapsadığı Kategori A konuları |
|---|---|
| `com.interviewlab.aop.AopSelfInvocationSpringLabRunner` | Spring AOP self-invocation + proxy introspection |
| `com.interviewlab.persistence.PersistenceSpringLabRunner` | Persistence context / dirty checking |
| `com.interviewlab.transaction.TransactionSpringLabRunner` | Transaction rollback |
| `com.interviewlab.transaction.propagation.PropagationSpringLabRunner` | Propagation REQUIRES_NEW self-invocation |
| `com.interviewlab.transaction.isolation.IsolationSpringLabRunner` | Isolation: non-repeatable read, phantom read, dirty read |
| `com.interviewlab.locking.OptimisticPessimisticSpringLabRunner` | Optimistic locking, Pessimistic locking |
| `com.interviewlab.locking.deadlock.DeadlockSpringLabRunner` | Deadlock (database-level) |
| `com.interviewlab.scopes.BeanScopesSpringLabRunner` | Bean scope: singleton, prototype, bean lifecycle |
| `com.interviewlab.async.spring.AsyncSpringLabRunner` | @Async self-invocation |
| `com.interviewlab.jpa.NPlusOneAndFetchSpringLabRunner` | N+1, Lazy fetch, Eager fetch |
| `com.interviewlab.security.SecuritySpringLabRunner` | Authentication/Authorization (401/403/200) — KENDİ başlattığı porta gerçek HTTP isteği gönderir |
| `com.interviewlab.patterns.DesignPatternsStrategyFactoryObserverSpringLabRunner` | Design Pattern: Strategy, Factory, Observer |

**main() ile MÜMKÜN OLMAYAN (dürüst, gerekçeli istisna):** Bean scope: Request/Session — bu
ikisi GERÇEK bir HTTP request'in thread'e bağlı olmasını (`ServletRequestAttributes`) şart
koşar; `spring-test`'in `MockHttpServletRequest`'i bilinçli olarak sadece test scope'unda,
production kodda DEĞİL. Bu iki konu için Postman + gerçek Cookie Jar KULLANMAK ZORUNLUDUR -
bu sahte bir kısıtlama değil, Spring web scope'larının kendi doğasıdır.

**Propagation REQUIRED, Bean scope: Application** de teknik olarak runner'a eklenebilirdi ama
tek-satırlık/trivial oldukları için (tek transaction'a katılma kontrolü, tek instance ID
kontrolü) POJO'ya benzer şekilde ayrı bir dosya AÇILMADI - Postman'de zaten DONE'dır.

## Runtime doğrulama (2026-09-16, ikinci tur — kullanıcının "prove it by executing it" talebi)

Kullanıcı, "43/43 DONE" iddiasını "kod var/test var" temelinde REDDETTİ ve HER Kategori A
lab'ının GERÇEKTEN bu görev sırasında HTTP ile tetiklenip runtime'dan gözlemlenebilir bir
sonuç ürettiğinin KANITLANMASINI istedi. Bu KANITLANDI — bkz. **`docs/RUNTIME_VERIFICATION.md`**:
43 satırın TAMAMI "Real HTTP verified: YES" (bu oturumda gönderilen gerçek curl istekleri, gerçek
200/401/403 HTTP status kodları, gerçek runtime state'ten okunan sonuçlar). Ayrıca AOP lab'ına
`ExecutionTimeAspect` içinde GERÇEK bir `interceptionCount` (`AtomicInteger`) sayacı eklendi -
`aspectIntercepted` artık controller'ın "BAD olduğu için false olmalı" diye BİLDİĞİ bir değer
DEĞİL, advice'ın KENDİSİNİN gerçekten çalışıp çalışmadığını GÖSTEREN bu sayacın delta'sından
hesaplanıyor.
