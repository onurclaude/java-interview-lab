# START HERE

Sıfırdan senior Java/Spring mülakat hazırlığı için bu projeyi çalışma sıran. Her adımda:
oku → çalıştır (Postman ya da `./mvnw test`) → DB'de/logda/debugger'da doğrula → dokümandaki
"Kendine sor" sorusunu kendi cümlelerinle cevapla.

## 0. Ortamı ayağa kaldır (bir kere)

```bash
docker compose up -d               # Postgres :5434, healthy olana kadar bekle
docker compose ps                  # "healthy" gördüğünden emin ol
```

IntelliJ'de `InterviewLabApplication.java` → Run (uygulama `:8082`'de açılır, Flyway
migration'ları otomatik çalışır). Postman'e `postman/Java-Interview-Lab.postman_collection.json`
+ `postman/Java-Interview-Lab-Local.postman_environment.json`'ı import et. DBeaver'ı
`localhost:5434/interviewlab` (user/pass: `interviewlab`/`interviewlab`) ile bağla.

## 1. İlk geçiş — Interactive lab'lar (Faz 1 + Faz 2)

Aşağıdaki sıra, konuların birbiri üzerine inşa edildiği sıradır. Her biri için:
`docs/INTERACTIVE_LABS.md`'deki ilgili LAB bölümünü aç, Postman'den RESET→BAD→STATE→GOOD
sırasını çalıştır, `docs/DB_LABS.md`'deki SQL ile DBeaver'dan doğrula.

1. **Persistence Context** (LAB 01) — `/api/labs/persistence`
2. **Transaction Rollback** (LAB 02) — `/api/labs/transaction`
3. **Propagation** (LAB 03) — `/api/labs/propagation`
4. **Isolation Levels** (LAB 06) — `/api/labs/isolation`
5. **Optimistic Locking** (LAB 04) — `/api/labs/optimistic`
6. **Pessimistic Locking** (LAB 05) — `/api/labs/pessimistic`
7. **ExecutorService / Rejection Policies** (LAB 07) — `/api/labs/executor`
8. **CompletableFuture** (LAB 08) — `/api/labs/completable-future`
9. **Spring @Async** (LAB 09) — `/api/labs/async`
10. **ThreadLocal** (LAB 10) — `/api/labs/threadlocal`
11. **Bean Scopes & Lifecycle** (LAB 11) — `/api/labs/scopes`
12. **Spring AOP** (LAB 12) — `/api/labs/aop`
13. **Exceptions** (LAB 13) — `/api/labs/exceptions`
14. **N+1** (LAB 14) — `/api/labs/n-plus-one`
15. **Design Patterns: Strategy** (LAB 15) — `/api/labs/patterns`
16. **Resilience4j** (LAB 16) — `/api/labs/resilience` — Retry/CircuitBreaker/RateLimiter/Bulkhead/Timeout/Fallback
17. **Security / JWT** (LAB 17) — `/api/labs/security` — 401 vs 403, JWT'nin şifreli olmadığının kanıtı

Bunlardan sonra `GET /api/labs` ile tüm lab listesini bir kez daha gözden geçir.

## 2. İkinci geçiş — Deterministik test'lerle kanıtlanan concurrency primitifleri

Bunlar HTTP'ye değil teste taşındı (bilinçli seçim - bkz. `docs/TOPIC_MATRIX.md` Faz 2b).
Her test sınıfını IntelliJ'den TEK TEK Debug modda çalıştır, konsoldaki `LAB`/thread
loglarını oku:

1. `ThreadLifecycleTest` — Thread.State geçişleri
2. `RaceConditionTest` — race condition, CountDownLatch ile deterministik
3. `SynchronizationTest` — synchronized/ReentrantLock/ReadWriteLock/StampedLock
4. `VolatileAndAtomicTest` — volatile, AtomicInteger/CAS, ABA problemi
5. `DeadlockLivelockStarvationTest` — gerçek deadlock (`ThreadMXBean`), zorunlu livelock, fairness
6. `ThreadCreationBoundsTest`, `ThreadLocalTest`

Ardından saf mantık konuları (timing/concurrency içermez, daha hızlı):
`CollectionsConcurrencyTest`, `ImmutabilityTest`, `EqualsHashCodeTest`, `OptionalTest`,
`StreamApiTest` (parallel stream dahil).

## 3. Üçüncü geçiş — Java Core / JVM (Faz 3)

Kod: `src/main/java/com/interviewlab/javacore/`, `src/test/java/com/interviewlab/javacore/`.
`docs/DEBUGGER_LABS.md`'deki breakpoint rehberini kullanarak Debug modda çalıştır:

1. **static / class-level state** — `JavaCoreTest` (Shared*StaticListService bad/good)
2. **String Pool** (`==` vs `equals()` vs `intern()`) — debugger lab #2
3. **Integer Cache** (-128..127, JLS 5.1.7) — `JavaCoreTest`
4. **HashMap internals** (collision, `CollidingKey`) — debugger lab #9
5. **Virtual Threads vs Platform Threads** — debugger lab #10
6. **final/finally/try-with-resources** — `TryFinallyTest`
7. **Reflection** — `ReflectionTest`
8. **Modern Java 21 switch** — `SwitchStylesTest`
9. **JVM Memory Model / JMM** — `docs/jvm-memory-model.md`
10. **Garbage Collection** — `docs/garbage-collection.md`
11. **BlockingQueue** — `CollectionsConcurrencyTest`
12. **Cache eviction (LRU) + Cache-Aside** — `CacheTest`
13. **Distributed Transactions: 2PC vs Saga** — `OrderSagaOrchestratorTest`, `docs/distributed-systems.md`
14. **Spring Configuration** — `docs/spring-configuration.md`
15. **HTTP Semantics & Security (JWT)** — `docs/http-and-security.md`

## 4. Notlarını denetle

`docs/NOTES_CORRECTIONS.md`'yi baştan sona oku - eski notlarında yanlış/eskimiş 11 madde
(Green Threads, JRE terminology, GC collectors, HashMap internals, clustered index, CAP,
BASE, HTTP 401/403, Spring proxy limitations, `readOnly`, cache strategies) NOTE
SAID/CORRECT VERSION/WHY/INTERVIEW ANSWER formatında düzeltildi.

## 5. Pekiştirme

- `docs/interview-scenarios.md` — 50+ zincirlenmiş mülakatçı/aday soru-cevabı.
- `docs/cheat-sheet.md` — tek satırlık hızlı referans.
- Her `docs/*.md` dosyasının sonundaki "Common Interview Questions" ve "30 Saniyelik
  Mülakat Cevabı" bölümlerini KOD'A BAKMADAN, kendi cümlelerinle cevapla.

## 6. Kalan (tamamen opsiyonel, ana kapsam dışı)

Kullanıcının notes-audit listesindeki TÜM ana madde ve daha sonra istenen tüm derinleştirme
maddeleri (Resilience4j JUnit testi, ThreadPoolBulkhead karşılaştırması, çalışan bir Spring
Security/JWT demosu, HashMap treeification'ın reflection ile canlı kanıtı) TAMAMLANDI -
`docs/TOPIC_MATRIX.md`, her konunun tam durumunu satır satır gösterir. `NEXT_WORK.md`'de
kalan tek madde, bilinçli olarak kapsam dışı bırakılan Design Patterns'in kalan 10'u için
HTTP endpoint'i (TEST ile zaten gösteriliyorlar).

## Şu ana kadarki gerçek sayılar (varsayım değil)

- **171 otomatik test, 0 hata, 0 başarısızlık** (`./mvnw test`).
- **17 interactive HTTP lab**, 98 Postman request, 19 klasör.
- **Gerçek Docker Postgres** üzerinde Flyway migration ile yönetilen 9 tablo.
- Doğrulama sırasında **4 gerçek, önceden bilinmeyen bug/yanlış varsayım** bulunup
  düzeltildi: `RaceConditionTest`'teki deadlock (Faz 1), isolation lab'ındaki commit-timing
  race'i (Faz 1 interactive doğrulaması), "fetch join = 1 sorgu" yanlış varsayımı (Faz 2
  N+1 lab'ı), ve `java.base` modülünün `java.util`'i deep reflection'a OPEN etmediği
  (HashMap treeification testi, `pom.xml`'e `--add-opens` eklenerek düzeltildi, Faz 3) -
  hepsi gerçek çalıştırma ile keşfedildi, varsayımla değil.
