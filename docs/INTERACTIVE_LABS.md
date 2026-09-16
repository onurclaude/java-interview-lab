# Interactive Labs

Bu dosya, `/api/labs/*` altındaki her lab'ı Postman + DBeaver + konsol logu ile bizzat
çalıştırman için adım adım talimat içerir. Otomatik testler (`./mvnw test`) davranışın
*kanıtıdır*; bu dosyadaki adımlar aynı davranışı *kendi gözlerinle görmen* içindir.

**Ön koşullar** (bkz. README.md "Interactive Lab Modu"):
1. `docker compose up -d` — Postgres `healthy` olmalı.
2. `InterviewLabApplication`'ı IntelliJ'den (veya `./mvnw spring-boot:run` ile) çalıştır.
3. `postman/Java-Interview-Lab.postman_collection.json` + `...environment.json`'ı Postman'e import et.
4. DBeaver'ı `localhost:5434/interviewlab` (user/pass: `interviewlab`/`interviewlab`) ile bağla.

Her lab aynı sözleşmeye sahiptir: **RESET → BAD → STATE → GOOD** (bazı lab'larda BAD/GOOD
yerine konuya özgü isimler vardır, örn. propagation'da `required`/`requires-new/bad`/`requires-new/good`).

---

# LAB 01 — Persistence Context / Dirty Checking

Kod: `com.interviewlab.persistence.*` — Doc: [persistence-context.md](persistence-context.md)

### Amaç

`save()` sonrası bir entity DETACHED olunca dirty checking'in neden çalışmadığını, ve tek bir
`@Transactional` sınırı içinde kalan managed bir entity'de neden çalıştığını görmek.

### 1. Reset

```http
POST /api/labs/persistence/reset
```

### 2. BAD çalıştır

```http
POST /api/labs/persistence/bad
```

**Beklenen:** `changeWasPersisted: false` — `nameInDatabase` hâlâ `"Ada"`, `"Ada Lovelace"`
DEĞİL. Konsolda `LAB: PERSISTENCE CONTEXT — BAD` bloğunu oku.

### 3. DB'de bak

```sql
SELECT * FROM lab_customer;
```

`name` sütununun hâlâ `Ada` olduğunu doğrula.

### 4. GOOD çalıştır

```http
POST /api/labs/persistence/good
```

**Beklenen:** `changeWasPersisted: true`, `nameInDatabase: "Linus Torvalds"`, ve
`updateStatementsIssued` içinde gerçek bir `update lab_customer ...` ifadesi.

### 5. Kendine sor

"`save()` her zaman veriyi kalıcı hale getirir mi?"

### Cevap

Hayır. `save()` sadece entity'yi persistence context'e ekler/onunla senkronize eder; asıl
garanti bir *transaction sınırı* içinde managed kalmaktır. `save()` dönünce entity DETACHED
olursa (kendi transaction'ı zaten kapandığı için), sonraki alan değişiklikleri hiçbir zaman
flush edilmez.

---

# LAB 02 — Transaction Rollback

Kod: `com.interviewlab.transaction.rollback.*` — Doc: [transactions.md](transactions.md)

### Amaç

Spring'in varsayılan rollback kuralının SADECE unchecked exception'ları kapsadığını, checked
exception'ların `rollbackFor` olmadan transaction'ı COMMIT ettiğini görmek.

### 1. Reset → 2. BAD

```http
POST /api/labs/transaction/reset
POST /api/labs/transaction/bad
```

**Beklenen:** `transactionResult: "COMMITTED"`, `finalBalance: 70.00` (100 - 30) — checked
exception fırlatılmasına RAĞMEN debit kalıcı oldu.

### 3. DB'de bak

```sql
SELECT * FROM lab_account;
```

### 4. GOOD çalıştır

```http
POST /api/labs/transaction/good
```

**Beklenen:** `transactionResult: "ROLLED_BACK"`, `finalBalance: 100.00` — `rollbackFor =
Exception.class` checked exception'ı da rollback kuralına dahil etti.

### 5. Kendine sor

"`@Transactional`, fırlatılan HER exception'da rollback yapar mı?"

### Cevap

Hayır — sadece unchecked `RuntimeException`/`Error`'da, varsayılan olarak. Checked
exception'lar için `rollbackFor` açıkça belirtilmelidir.

---

# LAB 03 — Propagation (REQUIRED / REQUIRES_NEW / self-invocation)

Kod: `com.interviewlab.transaction.propagation.*` — Doc: [propagation.md](propagation.md)

### Amaç

REQUIRED'ın mevcut transaction'a katıldığını, REQUIRES_NEW'in bağımsız bir transaction
açtığını, ve self-invocation'ın (`this.audit(...)`) REQUIRES_NEW'i neden sessizce devre dışı
bıraktığını görmek.

### 1. Reset → 2. REQUIRED

```http
POST /api/labs/propagation/reset
POST /api/labs/propagation/required
```

**Beklenen:** `transactionActiveBeforeRepositoryCall: true` ve
`transactionActiveAfterRepositoryCall: true` — tek, sürekli bir transaction.

### 3. REQUIRES_NEW — BAD (self-invocation)

```http
POST /api/labs/propagation/requires-new/bad
```

**Beklenen:** `auditSurvivedOuterRollback: false` — audit kaydı, outer transaction (kasıtlı
olarak) rollback olunca KAYBOLDU, çünkü `this.audit(...)` proxy'yi hiç görmedi.

### 4. DB'de bak

```sql
SELECT * FROM lab_audit_log;
```

Bu mesajı taşıyan bir satır YOK.

### 5. REQUIRES_NEW — GOOD

```http
POST /api/labs/propagation/requires-new/good
```

**Beklenen:** `auditSurvivedOuterRollback: true` — ayrı bir bean (`AuditService`) üzerinden
çağrıldığı için proxy'den geçti, gerçekten bağımsız commit etti.

### 6. Kendine sor

"Self-invocation neden REQUIRES_NEW'i bozuyor?"

### Cevap

Spring AOP, proxy tabanlıdır — proxy sadece bean'e DIŞARIDAN gelen çağrıları intercept eder.
`this.audit(...)`, JVM seviyesinde sıradan bir metod çağrısıdır, proxy'ye hiç uğramaz; bu
yüzden `REQUIRES_NEW` sessizce göz ardı edilir.

---

# LAB 04 — Optimistic Locking

Kod: `com.interviewlab.locking.optimistic.*` — Doc: [optimistic-locking.md](optimistic-locking.md)

### Amaç

Lost update'i ve `@Version`'ın onu nasıl imkansız kıldığını, GERÇEK, latch'lerle
koordine edilmiş iki eşzamanlı transaction ile görmek.

### 1. Reset → 2. BAD çalıştır

```http
POST /api/labs/optimistic/reset
POST /api/labs/optimistic/bad
```

**Beklenen:** `lostUpdateOccurred: true`, `finalStock` beklenen `10-2-3=5` DEĞİL, `7`
(T2'nin -3'ü, T1'in -2'sini görmeden kendi bayat taban değeri üzerinden yazdı).

### 3. DB'de bak

```sql
SELECT id, stock FROM lab_no_version_product;
```

### 4. GOOD çalıştır

```http
POST /api/labs/optimistic/good
```

**Beklenen:** `transaction2Result: "ObjectOptimisticLockingFailureException (BEKLENEN)"`,
`lostUpdateOccurred: false`.

### 5. Kendine sor

"`@Version`, veritabanı satırını locklar mı?"

### Cevap

Hayır. Optimistic locking fiziksel bir row lock DEĞİLDİR. `@Version`, her UPDATE'in WHERE
cümlesine `AND version = ?` ekletir; eşleşen satır sayısı 0 ise (başka biri version'ı zaten
artırmışsa), Hibernate bunu `ObjectOptimisticLockingFailureException` olarak raporlar - hiçbir
zaman bir DB kilidi almaya gerek yoktur.

---

# LAB 05 — Pessimistic Locking

Kod: `com.interviewlab.locking.pessimistic.*` — Doc: [pessimistic-locking.md](pessimistic-locking.md)

### Amaç

`SELECT ... FOR UPDATE`'in ikinci bir transaction'ı GERÇEKTEN veritabanı seviyesinde
bloke ettiğini görmek — sessizce çakışmak yerine.

### 1. Reset → 2. BAD çalıştır (hiçbir kilit yok)

```http
POST /api/labs/pessimistic/reset
POST /api/labs/pessimistic/bad
```

**Beklenen:** `lostUpdateOccurred: true` — LAB 04'teki gibi, kilit olmadan aynı race.

### 3. GOOD çalıştır

```http
POST /api/labs/pessimistic/good
```

**Beklenen:** `t2ActuallyBlocked: true`, `t2BlockedForMillis` ≈ 500 (T1'in lock'u tuttuğu
süre), `lostUpdateOccurred: false`, `finalStock: 5` (10 - 2 - 3, HER İKİ azaltma da doğru
şekilde uygulandı).

### 4. Konsolu oku

```
T1 acquired DB row lock (FOR UPDATE)
T2 attempting row lock - now BLOCKING until T1 releases it...
T1 COMMIT (releasing lock)
T2 acquired lock after waiting ~500ms, T2 COMMIT
```

### 5. Kendine sor

"Optimistic locking ile pessimistic locking arasındaki temel fark nedir?"

### Cevap

Optimistic locking asla bloklamaz — okur, dener, çakışırsa (retry edilebilir) bir exception
alır. Pessimistic locking gerçekten bloklar — ikinci transaction, kilit serbest kalana kadar
fiziksel olarak bekler. Düşük çekişmede optimistic daha iyi throughput verir; tek bir "hot
row" üzerinde yüksek çekişmede pessimistic genellikle kazanır (boşa retry yok).

---

# LAB 06 — Isolation Levels

Kod: `com.interviewlab.transaction.isolation.*` — Doc: [isolation.md](isolation.md)

### Amaç

PostgreSQL'in `READ_COMMITTED` ile `REPEATABLE_READ` arasındaki GERÇEK, ölçülebilir farkı
görmek — non-repeatable read.

### 1. Reset → 2. READ_COMMITTED altında çalıştır

```http
POST /api/labs/isolation/reset
POST /api/labs/isolation/non-repeatable-read/read-committed
```

**Beklenen:** `transaction1FirstRead: 100`, `transaction1SecondRead: 200`,
`observed: "NON_REPEATABLE_READ"` — T1'in ikinci okuması, T2'nin arada commit ettiği değeri gördü.

### 3. Reset → 4. REPEATABLE_READ altında çalıştır

```http
POST /api/labs/isolation/reset
POST /api/labs/isolation/non-repeatable-read/repeatable-read
```

**Beklenen:** `transaction1FirstRead: 100`, `transaction1SecondRead: 100`,
`observed: "SNAPSHOT_ISOLATION_PREVENTED_IT"` — T1, T2'nin commit'ini HİÇ görmedi.

### 5. Kendine sor

"REPEATABLE_READ, SQL standardının gerektirdiğinden daha mı güçlü?"

### Cevap

Evet, PostgreSQL'de. SQL standardı REPEATABLE_READ altında phantom read'lere izin verir;
PostgreSQL bunu tam snapshot isolation olarak uygular ve phantom'ları da engeller — standardın
gerektirdiğinden daha güçlü bir garanti.

---

# LAB 07 — ExecutorService / Rejection Policies

Kod: `com.interviewlab.executor.*` — Doc: [executor-service.md](executor-service.md)

### Amaç

`Executors.newFixedThreadPool(n)`'in arkasındaki gizli sınırsız kuyruğu, ve sınırlı bir
`ThreadPoolExecutor` + rejection policy'nin bunu nasıl gözlemlenebilir backpressure'a
çevirdiğini görmek.

```http
POST /api/labs/executor/reset
POST /api/labs/executor/bad
```

**Beklenen:** `queueSizeWhileAllWorkersBusy: 18` (20 görev - 2 çalışan işçi), `tasksRejected: 0`
— hiçbir şey reddedilmedi, kuyruk sessizce büyüdü.

```http
POST /api/labs/executor/good
```

**Beklenen:** `tasksAccepted: 5` (2 çalışan + 3 kuyruk kapasitesi), `tasksRejected: 5` —
kapasite dolunca `RejectedExecutionException` fırlatıldı.

```http
POST /api/labs/executor/caller-runs
```

**Beklenen:** `ranSynchronouslyOnCallingThread: true` — reddedilen 3. görev, HTTP request
thread'inin KENDİSİNDE çalıştı.

### Kendine sor

"`newFixedThreadPool(n)` neden 'sınırlı' değildir?"

### Cevap

Thread sayısı sınırlıdır, ama arkasındaki `LinkedBlockingQueue` sınırsızdır - `n` thread'in
tüketebileceğinden daha hızlı gelen görevler asla reddedilmez, sadece kuyrukta sınırsız
birikir (heap tükenene kadar).

---

# LAB 08 — CompletableFuture

Kod: `com.interviewlab.async.completablefuture.*` — Doc: [completable-future.md](completable-future.md)

### Amaç

Art arda `supplyAsync(...).get()` çağırmanın paralellikten hiçbir kazanç sağlamadığını,
önce başlatıp sonra birleştirmenin gerçekten eşzamanlı çalıştığını gerçek duvar-saati
süresiyle görmek.

```http
POST /api/labs/completable-future/reset
POST /api/labs/completable-future/sequential
```

**Beklenen:** `durationMillis` ≈ 900 (3 x 300ms'nin TOPLAMI).

```http
POST /api/labs/completable-future/parallel
```

**Beklenen:** `durationMillis` ≈ 300 (3 görevin MAKSİMUMU) — sequential'dan ~3 kat hızlı.

### Kendine sor

"`supplyAsync()` çağırmak, işi otomatik olarak paralel mi yapar?"

### Cevap

Hayır - paralellik, TÜM `supplyAsync()` çağrıları herhangi biri bloklanmadan ÖNCE
başlatılırsa elde edilir. Her birinden hemen sonra `.get()` çağırmak, onları sıralı hale
getirir.

---

# LAB 09 — Spring @Async

Kod: `com.interviewlab.async.spring.*` — Doc: [async.md](async.md)

### Amaç

`@Async`'in self-invocation'da neden sessizce devre dışı kaldığını görmek — `@Transactional`
ve `@Around` ile aynı proxy kök nedeni.

```http
POST /api/labs/async/reset
POST /api/labs/async/bad
```

**Beklenen:** `ranOnSameThreadAsCaller: true` — "asenkron" iş aslında senkron çalıştı.

```http
POST /api/labs/async/good
```

**Beklenen:** `ranOnDifferentThreadThanCaller: true` — iş gerçekten `labAsyncExecutor`
havuzunda farklı bir thread'de çalıştı.

```http
POST /api/labs/async/void-exception
```

**Beklenen:** `exceptionCapturedByHandler: true` — sadece özel bir
`AsyncUncaughtExceptionHandler` sayesinde görülebildi.

### Kendine sor

"void dönen bir `@Async` metod exception fırlatırsa, çağıran bunu yakalayabilir mi?"

### Cevap

Hayır - `Future` yok, yakalayacak hiçbir şey yok. Sadece özel bir
`AsyncUncaughtExceptionHandler` kaydedilmişse görülebilir, aksi halde sessizce kaybolur.

---

# LAB 10 — ThreadLocal

Kod: `com.interviewlab.concurrency.threadlocal.*` — Doc: [java-locks.md](java-locks.md)

### Amaç

Havuzlanmış bir thread'de temizlenmeyen bir `ThreadLocal` değerinin, tamamen alakasız bir
sonraki göreve nasıl sızdığını görmek.

```http
POST /api/labs/threadlocal/reset
POST /api/labs/threadlocal/bad
```

**Beklenen:** `leaked: true` — Görev 2, hiçbir şey ayarlamamasına rağmen Görev 1'in
correlation-id'sini okudu.

```http
POST /api/labs/threadlocal/good
```

**Beklenen:** `leaked: false`, `task2ReadValue: "null"`.

### Kendine sor

"`ThreadLocal` değeri ne zaman temizlenir?"

### Cevap

Asla otomatik olarak — sadece açıkça `remove()` çağrılırsa. Aksi halde, değeri ayarlayan
"görev" bitse bile, Thread nesnesi (özellikle havuzlanmış bir thread) yaşadığı sürece yaşar.

---

# LAB 11 — Bean Scopes & Lifecycle

Kod: `com.interviewlab.scopes.*` — Doc: [bean-scopes.md](bean-scopes.md)

### Amaç

Singleton'daki paylaşılan mutable state'in gerçek bir data race'e yol açtığını, prototype
bean'in singleton'a normal injection ile "yakalandığını", ve gerçek bean lifecycle sırasını
görmek.

```http
POST /api/labs/scopes/reset
POST /api/labs/scopes/singleton/bad
```

**Beklenen:** `dataRaceObserved: true` — 30 eşzamanlı çağrıdan çoğu (genellikle ~29) yanlış
sonuç aldı.

```http
POST /api/labs/scopes/singleton/good
```

**Beklenen:** `dataRaceObserved: false` — 0 yanlış sonuç.

```http
POST /api/labs/scopes/prototype/bad
```

**Beklenen:** `sameInstanceBothTimes: true`.

```http
POST /api/labs/scopes/prototype/good
```

**Beklenen:** `objectProviderProducedDifferentInstances: true`,
`scopedProxyProducedDifferentInstances: true`.

```http
GET /api/labs/scopes/lifecycle
```

**Beklenen sıra:** `1_CONSTRUCTOR → 2_BEAN_POST_PROCESSOR_BEFORE_INIT → 3_POST_CONSTRUCT →
3_5_BEAN_POST_PROCESSOR_AFTER_INIT`.

Request/session/application scope için (cookie gerektirir, Postman'in cookie jar'ını kullan):
`GET /lab/scopes/request`, `/lab/scopes/session`, `/lab/scopes/application`.

### Kendine sor

"Bir `@Service`'in varsayılan scope'u nedir, ve bu neden tehlikeli olabilir?"

### Cevap

Singleton - her `ApplicationContext` başına tam bir instance, tüm eşzamanlı request'ler
tarafından paylaşılır. Bir instance alanına çağrı-başına veri yazmak sıradan istek işlemeyi
bir data race'e çevirir.

---

# LAB 12 — Spring AOP

Kod: `com.interviewlab.aop.*` — Doc: [aop.md](aop.md)

### Amaç

`@Around` advice'ının self-invocation'da neden hiç çalışmadığını görmek.

```http
POST /api/labs/aop/reset
POST /api/labs/aop/bad
```

**Beklenen:** `aspectFired: false` — `this.slowStep()` proxy'yi hiç görmedi.

```http
POST /api/labs/aop/good
```

**Beklenen:** `aspectFired: true`, `aspectRecordedDurationAfter` gerçek bir milisaniye değeri.

### Kendine sor

"Bir `@Aspect`, aynı sınıftan `this.methodX()` ile yapılan çağrıları yakalar mı?"

### Cevap

Hayır - tüm Spring AOP advice tipleri (`@Around` dahil) proxy tabanlıdır; self-invocation
proxy'yi hiç görmez.

---

# LAB 13 — Exceptions

Kod: `com.interviewlab.exception.*` — Doc: [exceptions.md](exceptions.md)

### Amaç

Bir checked exception'ı yutmanın ve cause'unu kaybederek yeniden fırlatmanın, tanı bilgisini
nasıl yok ettiğini görmek.

```http
POST /api/labs/exceptions/reset
POST /api/labs/exceptions/swallowed
```

**Beklenen:** `gatewayActuallyDeclined: true`, `methodReportedSuccess: true` — başarısızlık
tamamen gizlendi.

```http
POST /api/labs/exceptions/lossy-rethrow
```

**Beklenen:** `hasOriginalCause: false`.

```http
POST /api/labs/exceptions/good
```

**Beklenen:** `causePreserved: true`, `causeMessage` orijinal decline kodunu içeriyor.

### Kendine sor

"Bir exception'ı yakalayıp yeniden fırlatırken en kritik hata nedir?"

### Cevap

Orijinal exception'ı `cause` olarak eklemeyi unutmak - bu, tanı için gereken TEK bilgiyi
(ör. bir decline kodu, alt katman hata detayı) sonsuza dek kaybeder.

---

# LAB 14 — N+1

Kod: `com.interviewlab.jpa.*` — Doc: [n-plus-one.md](n-plus-one.md)

### Amaç

N+1 sorgu problemini ve üç farklı çözümün (fetch join, entity graph, DTO projection)
GERÇEKTE kaç sorguya indiği arasındaki şaşırtıcı farkı gerçek SQL sayısıyla görmek.

```http
POST /api/labs/n-plus-one/reset
GET  /api/labs/n-plus-one/bad
```

**Beklenen:** `queryCount: 11` (1 parent + 10 lazy child).

```http
GET /api/labs/n-plus-one/good
```

**Beklenen (şaşırtıcı):** `fetchJoin.queryCount: 2` (1 DEĞİL!), `entityGraph.queryCount: 1`,
`dtoProjection.queryCount: 1`.

### Kendine sor

"JOIN FETCH her zaman tam olarak 1 sorguya iner mi?"

### Cevap

Hayır, bu proje bunu varsaydı ve YANLIŞ çıktığını gerçek SQL log'undan keşfetti: fetch join,
`orderItems`'ı düzeltir ama `OrderItem.product` hâlâ statik `FetchType.EAGER` olduğu için
Hibernate onu AYRI bir sorguyla yükler (2 toplam). `@EntityGraph`'ın 1 sorguya inmesinin
nedeni farklı: JPA'nın "fetch graph" kuralı, graph'ta listelenmeyen ilişkileri (product gibi)
statik EAGER olsalar bile bu sorgu için LAZY'ye düşürür - plain bir JOIN FETCH'in yapmadığı
bir optimizasyon.

---

---

# LAB 16 — Resilience4j

Kod: `com.interviewlab.resilience.*`, `com.interviewlab.web.lab.resilience.*` — Doc: [resilience.md](resilience.md)

### Amaç

Retry/Circuit Breaker/Rate Limiter/Bulkhead/Timeout/Fallback'in AYNI problemi
("bağımlılık başarısız/yavaş") farklı açılardan nasıl ele aldığını, gerçek Resilience4j
durum geçişleriyle görmek.

```http
POST /api/labs/resilience/reset
POST /api/labs/resilience/retry
```

**Beklenen:** `outcome: "SUCCEEDED_AFTER_RETRIES"`, `actualCallCount: 3`.

```http
POST /api/labs/resilience/circuit-breaker
```

**Beklenen:** `stateTransitions: ["CLOSED","OPEN"]`,
`underlyingServiceCalledDuringOpenState: false` — devre AÇIKKEN gerçek servis HİÇ ÇAĞRILMADI.

```http
POST /api/labs/resilience/rate-limiter
POST /api/labs/resilience/bulkhead
POST /api/labs/resilience/timeout
POST /api/labs/resilience/fallback
```

Her biri kendi mekanizmasını kanıtlıyor - tam response örnekleri için `docs/resilience.md`.

### Kendine sor

"Circuit Breaker ile Rate Limiter arasındaki fark nedir?"

### Cevap

Rate Limiter, bağımlılığın SAĞLIĞINDAN bağımsız, ÖNCEDEN belirlenmiş bir hız tavanını
uygular. Circuit Breaker, GÖZLEMLENEN başarısızlığa REAKTİF tepki verir - sağlıklıyken
hiçbir şey yapmaz, başarısızlık eşiği aşılınca fail-fast'e geçer.

---

---

# LAB 17 — Security / JWT

Kod: `com.interviewlab.security.*`, `com.interviewlab.web.lab.security.*` — Doc: [http-and-security.md](http-and-security.md)

### Amaç

401 (kimliksiz) ile 403'ün (kimlik biliniyor, yetki yok) GERÇEK farklı HTTP status
kodları olduğunu, ve JWT'nin şifreli DEĞİL sadece imzalı olduğunu gerçek bir Spring
Security + JWT kurulumuyla görmek.

```http
GET /api/labs/security/protected
```

**Beklenen:** `401` (token yok).

```http
POST /api/labs/security/login?username=ada&role=USER
```

**Beklenen:** Bir `token`, VE `decodedPayloadWithoutSecret` alanında - secret HİÇ
kullanılmadan, sadece base64 decode ile okunmuş - `{"sub":"ada","roles":["USER"],...}`.

```http
GET /api/labs/security/protected
Authorization: Bearer <token>
```

**Beklenen:** `200`, `authenticatedAs: "ada"`.

```http
GET /api/labs/security/admin-only
Authorization: Bearer <USER token>
```

**Beklenen:** `403` (kimlik biliniyor ama `ROLE_ADMIN` yok).

```http
POST /api/labs/security/login?username=grace&role=ADMIN
GET  /api/labs/security/admin-only  (ADMIN token ile)
```

**Beklenen:** `200`.

### Kendine sor

"JWT şifreli midir?"

### Cevap

Hayır. `decodedPayloadWithoutSecret` alanı bunu doğrudan kanıtlıyor - secret key hiç
kullanılmadan, sadece base64 decode ile payload okunabildi. İmza sadece "değiştirilmedi"
garantisi verir, "gizlidir" değil.

---

## Sırada ne var

Faz 1 (LAB 01-06), Faz 2 (LAB 07-15), ve Faz 3'ün interactive lab'ları (LAB 16 -
Resilience4j, LAB 17 - Security/JWT) tamamlandı ve gerçek Postgres/HTTP ile doğrulandı.
Kalan Faz 3 konuları (çoğu DOC/TEST olarak zaten tamamlandı - bkz. TOPIC_MATRIX.md) için
[TOPIC_MATRIX.md](TOPIC_MATRIX.md)'teki yol haritasına, [PROJECT_STATUS.md](../PROJECT_STATUS.md)'ye
ve [../NEXT_WORK.md](../NEXT_WORK.md)'e bakın.
