# Project Status

Live tracker. Updated as each topic reaches Definition of Done (see PROJECT_PLAN.md).

## Definition of Done DEĞİŞTİ İKİNCİ KEZ (2026-09-15 STOP → FAZ 4, 2026-09-16'da TAMAMLANDI)

Kullanıcı, 2026-09-15'teki "Faz 3 TAMAMLANDI" raporunu bir STOP mesajıyla REDDETTİ: "JUnit
test var" ile "interaktif lab var" AYNI ŞEY DEĞİLDİR. Aşağıdaki (2026-09-15 tarihli, ilk
"Definition of Done değişti" notu) hâlâ TEST-tabanlı DONE'ları da kabul ediyordu — bu ARTIK
GEÇERSİZDİR. Kesin, tek geçerli kriter: bir konu SADECE (A) gerçek bir Postman-tetiklenen HTTP
endpoint'i + IntelliJ debugger izlenebilirliği VARSA, (B) IntelliJ'de doğrudan Run/Debug
edilebilen bir `LabRunner.main()` VARSA, ya da (C) gerçek PostgreSQL'e karşı çalıştırılmış bir
DBeaver/`EXPLAIN ANALYZE` kanıtı VARSA `DONE` sayılır — JUnit test TEK BAŞINA ASLA yeterli
değildir. **FAZ 4 (bu STOP'un giderilmesi), 2026-09-16'da TAMAMLANDI** — bkz. aşağıdaki "FAZ 4"
bölümü ve **[docs/TOPIC_MATRIX.md](docs/TOPIC_MATRIX.md)** (nihai, güncel durum) +
**[docs/INTERACTIVITY_AUDIT.md](docs/INTERACTIVITY_AUDIT.md)** (satır satır ÖNCESİ/SONRASI
denetim geçmişi).

## FAZ 1 + FAZ 2 + FAZ 3: TAMAMLANDI (2026-09-15)

- **Faz 1** (Persistence, Transaction, Propagation, Isolation, Optimistic/Pessimistic Locking):
  TAMAMLANDI, gerçek Docker Postgres + HTTP + DB ile doğrulandı.
- **Faz 2** (mevcut repository'nin geri kalanı - ExecutorService, CompletableFuture, @Async,
  ThreadLocal, Bean Scopes+Lifecycle, AOP, Exceptions, N+1, Design Patterns/Strategy, +
  pure concurrency primitifleri ve saf mantık konularının TEST-tabanlı audit'i): TAMAMLANDI.
- **Faz 3** (kullanıcının mülakat notlarından denetlenen ek konular + "kalan ince
  ayrıntılar"): kullanıcının notes-audit listesindeki TÜM ana madde VE Resilience4j JUnit
  testi, ThreadPoolBulkhead karşılaştırması, HashMap treeification'ın reflection ile
  canlı kanıtı, ve çalışan bir Spring Security/JWT demosu (LAB 17) dahil TAMAMLANDI -
  bkz. docs/TOPIC_MATRIX.md.
- **Test durumu**: `./mvnw test` → **171 test, 0 hata, 0 başarısızlık, 0 atlanan, BUILD SUCCESS**
  (34 test sınıfı) - Faz 1 sonundaki 132'den +39.

## FAZ 4: TAMAMLANDI (2026-09-16) — STOP mesajının giderilmesi

Kullanıcının STOP mesajı, spesifik olarak `SelfInvocationTimingService`'i (AOP) örnek göstererek,
JUnit-test-only konuların "interaktif" SAYILMADIĞINI belirtti ve kesin JSON şekilleri, debugger
breakpoint formatı, ve exhaustive Kategori A/B/C konu listesi verdi. Yapılanlar:

1. **`docs/INTERACTIVITY_AUDIT.md` oluşturuldu** — Kategori A/B/C'deki HER konu SIFIRDAN
   denetlendi (Topic/Current impl/Trigger/Run Manually?/Debug Manually?/BAD Obs?/GOOD Obs?/
   Postman-Runner/DB Obs?/Missing Work/Status kolonlarıyla).
2. **AOP tamamen yeniden inşa edildi** — kullanıcının VERDİĞİ TAM JSON şekliyle (`aspectIntercepted`
   GERÇEK `ExecutionTimeAspect` kaydından hesaplanıyor, sahte değil), `AopUtils.isCglibProxy`
   ile gerçek proxy introspection eklendi.
3. **9 tamamen YENİ Kategori A HTTP lab'ı** (önceden SIFIR HTTP yüzeyi vardı): Race Condition,
   Locking Primitives (synchronized/ReentrantLock/ReadWriteLock/StampedLock), volatile, ABA
   problem, Lazy/Eager Fetch, Cache-Aside, Isolation phantom/dirty-read, Design Patterns'ın
   5 yeni pattern'i (Factory/Adapter/Observer/Builder/Proxy) — hepsi backend-gerçekçi
   senaryolarla (PaymentStrategyFactory/ExternalPaymentProviderAdapter/Order event
   Observer/OrderRequest Builder/GreeterProxyFactory JDK proxy), TOY Dog/Cat/Shape ÖRNEĞİ
   KULLANILMADI.
4. **14 Kategori B LabRunner sınıfı** oluşturuldu ve HEPSİ bizzat ÇALIŞTIRILIP doğrulandı (2
   gerçek bug bulunup düzeltildi: Windows Cp1254 konsol encoding, `ImmutabilityLabRunner`
   çökmesi).
5. **Executor/CompletableFuture/Resilience genişletildi**: DiscardPolicy/DiscardOldestPolicy
   (GERÇEK gözlemlenebilir `executedTaskIds` ile), `threadNames`/`distinctThreadCount`,
   `/api/labs/resilience/external/config` (kullanıcının istediği TAM pattern).
6. **`docs/DEBUGGER_LABS.md` TAMAMEN yeniden yazıldı** — 43 giriş, HER BİRİ kesin
   CLASS/METHOD/BREAKPOINT/POSTMAN REQUEST/EXPECTED CALL STACK/EXPECTED VARIABLES/WHAT TO
   WATCH formatında.
7. **Postman koleksiyonu**: 19→25 klasör (6 yeni: Race Condition/Locking Primitives/volatile/
   Design Patterns 2/Lazy-Eager/Cache-Aside, ayrıca AOP klasörü düzeltildi), HER klasöre
   "00 INFO" isteği eklendi (WHAT AM I LEARNING/WHERE TO PUT BREAKPOINTS/WHAT I SHOULD
   PREDICT/WHAT I SHOULD OBSERVE/WHAT INTERVIEW QUESTION THIS ANSWERS formatında).
8. **16 topic doc'u** (`async.md`, `completable-future.md`, `atomic.md`, `bean-scopes.md`,
   `design-patterns.md`, `exceptions.md`, `executor-service.md`, `isolation.md`,
   `java-locks.md`, `n-plus-one.md`, `optimistic-locking.md`, `persistence-context.md`,
   `pessimistic-locking.md`, `propagation.md`, `transactions.md`, `spring-configuration.md`)
   `docs/aop.md`'deki "HTTP lab PRIMARY, JUnit secondary" desenine göre düzeltildi (bir
   Explore subagent ile sistematik olarak taranıp bulundu).
9. **`docs/TOPIC_MATRIX.md` TAMAMEN yeniden yazıldı** — yeni katı standarda göre: Kategori A
   43/43, Kategori B 14/14, Kategori C 3/3, hepsi DONE.

**Her tek eklemeden/değişiklikten SONRA gerçek `curl` ile canlı doğrulama + `./mvnw test`
regresyon kontrolü yapıldı** (bu fazda toplam 10+ kez, HER SEFERİNDE 171/171, 0 hata).
`docs/INTERACTIVITY_AUDIT.md`'deki TÜM STILL_MISSING ve PARTIAL madde bu fazda FIXED'e çevrildi.

## FINAL ACCEPTANCE REPORT (2026-09-15, FAZ 1-3 — bkz. yukarıdaki FAZ 4 için güncel durum)

| Ölçüt | Sayı |
|---|---|
| Interactive HTTP lab (`/api/labs/*`) | **17** |
| Postman collection klasörü / request | **19 klasör / 98 request** |
| Otomatik test sınıfı / test metodu | **34 sınıf / 171 test** |
| Doküman (`docs/*.md` + kök `*.md`) | **30 + 4 = 34** |
| DB lab (gerçek Postgres, DBeaver SQL ile) | **8** (`docs/DB_LABS.md`) — persistence, transaction, propagation, isolation, optimistic, pessimistic, N+1, indexes/EXPLAIN ANALYZE |
| Debugger lab (IntelliJ breakpoint rehberi) | **10** (`docs/DEBUGGER_LABS.md`) |
| Notes corrections | **11/11 madde** (`docs/NOTES_CORRECTIONS.md`) |
| Yeni eklenen konu (Faz 3, mevcut repodan) | **~22** (Java Core/JVM: static, HashMap internals+treeification, String Pool, Integer Cache, Virtual Threads, final/finally, Reflection, modern switch; JVM: memory model, GC; Collections: BlockingQueue; Cache: LRU, Cache-Aside; Distributed: Saga; Docs: Spring config; Resilience4j: Retry/CircuitBreaker/RateLimiter/Bulkhead(semaphore+ThreadPool)/Timeout/Fallback; Security: Spring Security + JWT) |
| Yeni Maven bağımlılığı eklendi | **2** (`resilience4j-spring-boot3`, `spring-boot-starter-security` + `jjwt`) |
| Docker PostgreSQL | **healthy**, port 5434, Flyway ile yönetilen 9 tablo |
| Spring Boot | **healthy**, port 8082, tüm 17 lab canlı doğrulandı (Spring Security dahil, geri kalan 15 lab'ı BOZMADAN) |
| Flyway | **1 migration, başarıyla uygulandı ve doğrulandı** |
| **Final test sonucu** | **`./mvnw test` → 171/171, 0 hata, 0 başarısızlık, BUILD SUCCESS** |

**Interactive doğrulama sırasında bulunan gerçek bug/yanlış varsayım sayısı: 4**
(RaceConditionTest deadlock'u [Faz 1], isolation lab commit-timing race'i [Faz 1], "fetch
join = 1 sorgu" yanlış varsayımı [Faz 2 N+1], ve `java.base` modülünün `java.util`'i deep
reflection için OPEN etmediği - `HashMap` treeification testi bunu `InaccessibleObjectException`
ile ortaya çıkardı, `pom.xml`'e `--add-opens java.base/java.util=ALL-UNNAMED` eklenerek
düzeltildi [Faz 3]) — hepsi gerçek çalıştırma ile keşfedildi.

**Kalan (gerçekten opsiyonel, ana kapsam dışı):** Design Patterns'in kalan 10'u için HTTP
(bilinçli olarak TEST ile bırakıldı). Bkz. NEXT_WORK.md.

Status legend: NOT STARTED / IN PROGRESS / DONE

| # | Topic | Status |
|---|-------|--------|
| 1 | Persistence context | DONE |
| 2 | Flush / dirty checking | DONE |
| 3 | Transaction rollback | DONE |
| 4 | Isolation levels | DONE |
| 5 | Propagation | DONE |
| 6 | Transaction proxy / self-invocation | DONE |
| 7 | Optimistic locking | DONE |
| 8 | Pessimistic locking | DONE |
| 9 | Lost update | DONE (covered under optimistic + isolation) |
| 10 | Deadlock (DB) | DONE |
| 11 | Thread/Runnable/Callable/Future | DONE |
| 12 | Race condition | DONE |
| 13 | synchronized | DONE |
| 14 | ReentrantLock/ReadWriteLock/StampedLock | DONE |
| 15 | volatile | DONE |
| 16 | Atomic primitives | DONE |
| 17 | ThreadLocal | DONE |
| 18 | Deadlock/Livelock/Starvation (pure Java) | DONE |
| 19 | ExecutorService | DONE |
| 20 | ThreadPoolExecutor tuning | DONE |
| 21 | CompletableFuture | DONE |
| 22 | Spring @Async | DONE |
| 23 | Bean scopes | DONE |
| 24 | AOP | DONE |
| 25 | Exception handling | DONE |
| 26 | Design patterns | DONE |
| 27 | Collections / concurrent collections | DONE |
| 28 | Immutability | DONE |
| 29 | equals/hashCode | DONE |
| 30 | Optional | DONE |
| 31 | Stream API | DONE |
| 32 | Lazy loading / LazyInitializationException | DONE |
| 33 | N+1 | DONE |
| 34 | Fetch strategies | DONE |
| 35 | interview-scenarios.md | DONE |
| 36 | cheat-sheet.md | DONE |
| 37 | Final README | DONE |

## Interactive Labs (Faz 1) — `/api/labs/*`

Persistence, Transaction rollback, Propagation, Isolation, Optimistic locking, Pessimistic
locking artık tam interactive: `RESET/BAD/GOOD/STATE` sözleşmesiyle gerçek HTTP endpoint'leri,
öğretici JSON response'lar, okunabilir konsol logları, Postman collection (`postman/`), ve
adım-adım rehber (`docs/INTERACTIVE_LABS.md`). Kod: `com.interviewlab.web.lab.*`.

## Phase 1 Interactive Verification Report (2026-09-15)

Gerçek Docker Postgres + gerçek `spring-boot:run` + gerçek HTTP çağrıları ile doğrulandı
(sadece derleme değil):

| Kontrol | Sonuç |
|---|---|
| Docker Postgres (`docker compose up -d`, port 5434) | **PASS** — `healthy` |
| Flyway migration (`V1__initial_schema.sql`) | **PASS** — "Successfully applied 1 migration", 10 tablo oluştu |
| Hibernate `ddl-auto=validate` | **PASS** — hiçbir `SchemaManagementException` yok, tüm 9 entity mapping'i migration şemasıyla eşleşti |
| Spring Boot startup (`:8082`) | **PASS** — "Started InterviewLabApplication in ~3s" |
| `GET /api/labs` | **PASS** — 6 lab'ı doğru şekilde listeledi |
| Persistence lab (reset/bad/good/state) | **PASS** — DETACHED entity mutasyonu gerçekten sessizce kayboldu; managed entity'de gerçekten UPDATE üretti (`SqlStatementRecorder` ile doğrulandı) |
| Transaction rollback lab | **PASS** — checked exception gerçekten COMMIT etti (balance 70.00); `rollbackFor` gerçekten ROLLBACK etti (balance 100.00) |
| Propagation lab | **PASS** — self-invocation'da audit kaydı gerçekten kayboldu; ayrı bean üzerinden REQUIRES_NEW gerçekten hayatta kaldı |
| Isolation lab | **PASS** *(bir gerçek bug bulunup düzeltildikten sonra — aşağıya bakın)* — READ_COMMITTED gerçekten non-repeatable read gösterdi (100→200); REPEATABLE_READ gerçekten engelledi (100→100) |
| Optimistic locking lab | **PASS** — `@Version` yokken gerçek lost update (stock 10→7, beklenen 5); `@Version` ile gerçek `ObjectOptimisticLockingFailureException` |
| Pessimistic locking lab | **PASS** — kilitsizken gerçek lost update; `FOR UPDATE` ile T2 gerçekten ~521ms bloklandı (T1'in 500ms lock süresine denk), final stock doğru (5) |
| DB doğrulama (`docker exec ... psql`) | **PASS** — her lab'ın DB durumu HTTP response'larıyla birebir eşleşti |
| Regresyon: `./mvnw test` (tüm değişikliklerden sonra) | **PASS** — **132 test, 0 hata, 0 başarısızlık, 0 atlanan** |

**Interactive doğrulama sırasında bulunan ve düzeltilen gerçek bug:** `IsolationLabController`
üzerinden HTTP ile tetiklendiğinde, `IsolationAnomalyLab.updateBalanceAfterSignal`'ın
`doneSignal.countDown()`'ı gerçek COMMIT'ten ÖNCE (transactional metod dönmeden hemen önce)
çağırması, T1'in ikinci okumasının T2'nin commit'i tamamlanmadan önce çalışmasına izin
veriyordu - bu, sıkı zamanlamalı JUnit test ortamında rastlantısal olarak hep doğru sonuç
veriyordu (132 testin hepsi geçiyordu), ama Tomcat worker thread'leri + taze oluşturulmuş bir
executor gibi farklı zamanlama koşullarında GÜVENİLİR ŞEKİLDE YANLIŞ sonuç üretiyordu
(READ_COMMITTED senaryosu her seferinde REPEATABLE_READ gibi davranıyordu). Düzeltme:
`TransactionSynchronizationManager.registerSynchronization(...afterCommit...)` ile
`doneSignal`'ı gerçek commit'ten SONRA countDown etmek - hem yeni interactive lab'ı hem de
mevcut `IsolationLevelsTest`'i (132 testin içinde, hâlâ geçiyor) daha doğru hale getirdi.

**Dev/test port çakışması bulundu ve düzeltildi:** Doğrulama sırasında `docker ps -a`,
`cafe-menu-db`'nin artık port 5433'ü ve `port-ofis-backend`'in port 8080'i kullandığını
gösterdi (ikisi de bu makinedeki ilgisiz başka projeler). Bu projenin dev Postgres'i **5434**'e,
uygulama sunucusu **8082**'ye taşındı - hiçbir konteyner durdurulmadı/silinmedi, sadece bu
projenin kendi portları değiştirildi.

Detaylı adım-adım talimat: [docs/INTERACTIVE_LABS.md](docs/INTERACTIVE_LABS.md). Kalan
konuların yol haritası: [docs/TOPIC_MATRIX.md](docs/TOPIC_MATRIX.md).

## Demo API (legacy, Faz 1 öncesi)
`/lab/*` altındaki eski, sözleşmesiz demo endpoint'leri hâlâ mevcut (geriye dönük uyumluluk
için silinmedi): `com.interviewlab.web.LabDemoController`,
`com.interviewlab.scopes.webscopes.ScopesController` (scopes),
`com.interviewlab.exception.web.PaymentDemoController` (exceptions). Yeni keşif için
`/api/labs/*`'i tercih edin.

## Build log

- Full project scaffold (Maven wrapper, pom.xml, docker-compose.yml, Testcontainers base
  class, SQL statement recorder) — working.
- Fixed during integration: Spring bean-name collisions (`OrderService` used in two
  packages, `Product`/`ProductRepository` used in two packages), a missing `@Component` on
  a simulated third-party SDK class, a livelock-demo synchronization bug (needed a second
  barrier to force true contention), and a flaky wall-clock-based starvation test (replaced
  with a deterministic fairness-contract proof).
- A real, reproducible full-suite hang was root-caused via `jstack` thread dumps (not the
  earlier static-initializer-block theory, which was a red herring): `RaceConditionTest`
  submitted 500 tasks to a fixed 16-thread pool, where each task counts down a
  `taskCount`-sized "ready" latch before waiting on a "start" latch — with `taskCount >
  poolSize`, only 16 tasks could ever run, so `ready` could never reach zero and the main
  thread blocked forever on an un-timed `ready.await()`. Fixed by sizing the pool to
  `taskCount`. Fixing this exposed 9 further real, previously-never-exercised test bugs
  across `SynchronizationTest`, `OptimisticLockingTest`, `PessimisticLockingTest`,
  `PersistenceLifecycleServiceTest`, and `PropagationShowcaseTest` (wrong assertions, a
  same-thread `ReentrantLock` reentrancy blind spot, Postgres/Hibernate 6 emitting `FOR NO
  KEY UPDATE` instead of `FOR UPDATE`, Spring's JPA exception translation wrapping
  `TransactionRequiredException`, a commit-time implicit flush nobody accounted for, and —
  the big one — `Propagation.NESTED` being fundamentally unusable with `HibernateJpaDialect`
  because its transaction-data object never implements `SavepointManager`, confirmed by
  decompiling Spring's own bytecode). All were fixed with verified, evidence-based
  corrections rather than loosened assertions.
- Latest full `./mvnw clean test` run (2026-09-08, port 5433 at the time): **132 tests, 0
  failures, 0 errors, 0 skipped, BUILD SUCCESS** across all 27 concrete test classes.
- 2026-09-15: dev-mode ports moved again — `docker ps -a` on this machine showed `cafe-menu-db`
  now occupying 5433 and `port-ofis-backend` occupying 8080 (both unrelated projects that
  weren't running back on 2026-09-08). Moved this project's dev Postgres to **5434** and the
  app server to **8082** to avoid any collision. Tests are unaffected (they use Testcontainers
  with dynamically-assigned ports via `@DynamicPropertySource`, never these fixed dev ports).

## Known gaps / possible follow-ups
- Design patterns, collections, immutability, equals/hashCode, Optional, and Stream API
  topics are each covered by one focused bad/good pair rather than exhaustively covering
  every sub-variant mentioned in the original spec (e.g. Stream API's "unnecessary stream
  usage" and "nested stream readability" points are covered lightly, in docs/cheat-sheet.md,
  rather than with dedicated bad/good classes).
- Faz 1'in 6 lab'ı (persistence/transaction/propagation/isolation/optimistic/pessimistic)
  `/api/labs/*` altında tam interactive; kalan 31 konu henüz otomatik test olarak DONE ama
  interactive değil - tam denetim ve yol haritası için docs/TOPIC_MATRIX.md'ye bakın (Faz 2:
  mevcut repository'nin geri kalanı; Faz 3: kullanıcının mülakat notlarından denetlenen ~50
  ek konu, henüz projede yok).
- Isolation lab'ının phantom read / dirty read / serialization-failure senaryoları henüz
  `/api/labs/isolation`'a taşınmadı (sadece non-repeatable read taşındı) - `IsolationLevelsTest`
  içinde TEST olarak kalmaya devam ediyor.
