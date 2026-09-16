# Debugger Labs (IntelliJ) — Kategori A'nın TAMAMI için kesin breakpoint manifesti

Bu dosya, kullanıcının EXACT istediği formatta yeniden yazıldı. Her lab için: TOPIC / POSTMAN
FOLDER / BAD REQUEST / GOOD REQUEST / BAD BREAKPOINTS (fully-qualified) / BAD EXPECTED CALL
STACK / GOOD BREAKPOINTS / GOOD EXPECTED CALL STACK / BREAKPOINT HIT/MISS EXPECTATION / WHAT
TO INSPECT / WHAT THIS PROVES. Tek senaryolu lab'larda (BAD/GOOD ayrımı olmayan) tek bir
SENARYO bloğu kullanılır.

**Kullanım:** `InterviewLabApplication`'ı IntelliJ'de DEBUG modunda başlat → aşağıdaki
breakpoint'leri koy → Postman'de ilgili klasörü aç → RESET → BAD → (breakpoint'lerde dur,
Variables/Call Stack'i incele) → STATE → GOOD → (tekrar dur, karşılaştır).

Her lab için "Runtime Verified" durumu `docs/DEBUGGER_VERIFICATION.md`'dedir — bu dosya SADECE
breakpoint/call-stack MANİFESTİdir, doğrulama matrisi değildir.

---

## 1. Persistence Context / Dirty Checking

**POSTMAN FOLDER:** `01 Persistence`

**BAD REQUEST:** `POST /api/labs/persistence/bad`
**GOOD REQUEST:** `POST /api/labs/persistence/good`

**BAD BREAKPOINTS:**
- `com.interviewlab.persistence.bad.MutatingDetachedEntityService.renameCustomerAssumingDirtyChecking()`
- `com.interviewlab.web.lab.persistence.PersistenceLabController.bad()` (satır: `Customer reloaded = customerRepository.findById(id)...`)

**BAD EXPECTED CALL STACK:**
```
PersistenceLabController.bad()
 -> MutatingDetachedEntityService.renameCustomerAssumingDirtyChecking()
     -> customerRepository.save(...)   [metot buradan DÖNER, entity artık DETACHED]
     -> managed.changeName(...)         [DETACHED entity üzerinde - HİÇBİR SQL üretmez]
```

**GOOD BREAKPOINTS:**
- `com.interviewlab.persistence.good.PersistenceLifecycleService.renameViaDirtyCheckingOnly()`

**GOOD EXPECTED CALL STACK:**
```
PersistenceLabController.good()
 -> PersistenceLifecycleService.renameViaDirtyCheckingOnly(id, "Linus Torvalds")
     -> customerRepository.findById(id)   [managed entity, session AÇIK]
     -> managed.changeName(newName)        [transaction commit'te GERÇEK UPDATE üretir]
```

**BREAKPOINT HIT/MISS EXPECTATION:**
- `MutatingDetachedEntityService.renameCustomerAssumingDirtyChecking()` should HIT (BAD akışında)
- `PersistenceLifecycleService.renameViaDirtyCheckingOnly()` should HIT (GOOD akışında)
- Konsolda `Hibernate: update lab_customer ...` satırı BAD'de HİÇ görünmemeli, GOOD'da GÖRÜNMELİ

**WHAT TO INSPECT:**
- `reloaded.getName()` (controller'da) — BAD'de hâlâ `"Ada"`, GOOD'da `"Linus Torvalds"`
- `changeWasPersisted` response alanı
- DBeaver: `SELECT * FROM lab_customer;`

**WHAT THIS PROVES:** dirty checking sadece MANAGED (persistence context'te izlenen) entity'ler
için çalışır — `save()` sonrası DETACHED bir entity üzerindeki setter çağrıları hiçbir SQL
üretmez.

---

## 2. Transaction Rollback

**POSTMAN FOLDER:** `02 Transactions`

**BAD REQUEST:** `POST /api/labs/transaction/bad`
**GOOD REQUEST:** `POST /api/labs/transaction/good`

**BAD BREAKPOINTS:**
- `com.interviewlab.transaction.rollback.bad.CheckedExceptionNoRollbackService.debitThenFailWithCheckedException()`

**BAD EXPECTED CALL STACK:**
```
TransactionLabController.bad()
 -> CheckedExceptionNoRollbackService.debitThenFailWithCheckedException(accountId, amount)
     -> account.debit(amount)
     -> throw new SimulatedCheckedFailureException(...)   [CHECKED exception]
     [Spring proxy: checked exception varsayılan rollback kuralına GİRMEZ -> COMMIT]
```

**GOOD BREAKPOINTS:**
- `com.interviewlab.transaction.rollback.good.RollbackForCheckedExceptionService.debitThenFailWithCheckedException()`

**GOOD EXPECTED CALL STACK:**
```
TransactionLabController.good()
 -> RollbackForCheckedExceptionService.debitThenFailWithCheckedException(accountId, amount)
     -> account.debit(amount)
     -> throw new SimulatedCheckedFailureException(...)
     [@Transactional(rollbackFor=Exception.class) -> ROLLBACK]
```

**BREAKPOINT HIT/MISS EXPECTATION:**
- Her iki metot da HIT olur (ikisi de exception fırlatır) — fark, metottan SONRA proxy'nin
  ne yaptığıdır (debugger'da Step Out ile proxy'nin `commit()`/`rollback()` çağrısına kadar
  ilerle)
- `TransactionAspectSupport.completeTransactionAfterThrowing()`'e (Spring internal) breakpoint
  koyarsan BAD'de `commit` yolunu, GOOD'da `rollback` yolunu HIT ettiğini görürsün

**WHAT TO INSPECT:**
- `reloaded.getBalance()` — BAD: `70.00` (debit KALICI), GOOD: `100.00` (rollback oldu)
- DBeaver: `SELECT * FROM lab_account;`

**WHAT THIS PROVES:** Spring'in varsayılan rollback kuralı SADECE unchecked
`RuntimeException`/`Error` içindir — `rollbackFor` açıkça belirtilmedikçe checked exception
COMMIT'i durdurmaz.

---

## 3. Propagation REQUIRED

**POSTMAN FOLDER:** `03 Propagation`

**SENARYO REQUEST:** `POST /api/labs/propagation/required`

**BREAKPOINTS:**
- `com.interviewlab.web.lab.propagation.PropagationLabController.required()` (satır:
  `boolean activeBeforeRepositoryCall = TransactionSynchronizationManager.isActualTransactionActive();`)

**EXPECTED CALL STACK:**
```
PropagationLabController.required()   [zaten @Transactional]
 -> accountRepository.save(...)        [AYNI transaction'a KATILIR, yeni açmaz]
```

**HIT/MISS EXPECTATION:** tek breakpoint HIT olur; `TransactionSynchronizationManager.isActualTransactionActive()`'ı
çağrıdan ÖNCE VE SONRA Evaluate Expression ile çağırırsan İKİSİ DE `true` döner (aynı fiziksel
transaction).

**WHAT TO INSPECT:** `activeBeforeRepositoryCall` ve `stillActiveAfterRepositoryCall` — ikisi de `true`.

**WHAT THIS PROVES:** REQUIRED (varsayılan propagation), çağıranın zaten aktif bir transaction'ı
varsa YENİ bir tane AÇMAZ, ona katılır.

---

## 4. Propagation REQUIRES_NEW (self-invocation)

**POSTMAN FOLDER:** `03 Propagation`

**BAD REQUEST:** `POST /api/labs/propagation/requires-new/bad`
**GOOD REQUEST:** `POST /api/labs/propagation/requires-new/good`

**BAD BREAKPOINTS:**
- `com.interviewlab.transaction.propagation.bad.SelfInvocationPaymentService.processPayment()`
- `com.interviewlab.transaction.propagation.bad.SelfInvocationPaymentService.audit()` (aynı sınıfın `@Transactional(propagation=REQUIRES_NEW)` metodu)

**BAD EXPECTED CALL STACK:**
```
PropagationLabController.requiresNewBad()
 -> SelfInvocationPaymentService.processPayment(accountId, amount, auditMessage)
     -> this.audit(auditMessage)   [self-invocation - proxy'ye HİÇ uğramaz]
     -> throw new OrderProcessingException(...)
     [outer transaction ROLLBACK -> audit() AYNI transaction'da olduğu için O DA rollback olur]
```

**GOOD BREAKPOINTS:**
- `com.interviewlab.transaction.propagation.good.OrderService.processPayment()`
- `com.interviewlab.transaction.propagation.good.AuditService.audit()` (ayrı bean, gerçek proxy üzerinden)

**GOOD EXPECTED CALL STACK:**
```
PropagationLabController.requiresNewGood()
 -> OrderService.processPayment(accountId, amount, auditMessage)
     -> auditService.audit(auditMessage)   [AYRI bean proxy'si üzerinden - REQUIRES_NEW GERÇEKTEN uygulanır]
         [bağımsız transaction COMMIT eder, outer'dan ÖNCE]
     -> throw new OrderProcessingException(...)
     [outer transaction ROLLBACK olur ama audit() ZATEN commit olmuştu]
```

**BREAKPOINT HIT/MISS EXPECTATION:**
- BAD: `SelfInvocationPaymentService.audit()` HIT olur AMA (bir breakpoint koyup Call Stack'e
  bakarsan) çağrı `this.audit(...)` üzerinden gelir — proxy sınıfı DEĞİL, gerçek sınıf
  (`SelfInvocationPaymentService`, CGLIB soneki YOK) `this` olarak görünür
- GOOD: `AuditService.audit()` HIT olur, Call Stack'te ÖNCESİNDE proxy sınıfı (`AuditService$$SpringCGLIB$$0`) görünür

**WHAT TO INSPECT:**
- `auditSurvivedOuterRollback` — BAD: `false`, GOOD: `true`
- DBeaver: `SELECT * FROM lab_audit_log WHERE message = '...';`

**WHAT THIS PROVES:** REQUIRES_NEW, self-invocation ile çağrıldığında proxy atlanır ve
SESSİZCE göz ardı edilir; ayrı bir bean üzerinden çağrıldığında gerçekten bağımsız commit eder.

---

## 5. Isolation — Non-Repeatable Read

**POSTMAN FOLDER:** `04 Isolation`

**BAD REQUEST:** `POST /api/labs/isolation/non-repeatable-read/read-committed`
**GOOD REQUEST:** `POST /api/labs/isolation/non-repeatable-read/repeatable-read`

**BAD BREAKPOINTS:**
- `com.interviewlab.transaction.isolation.IsolationAnomalyLab.readTwiceUnderReadCommitted()` (İKİNCİ `jdbcTemplate.queryForObject(SELECT_BALANCE, ...)` satırı)

**BAD EXPECTED CALL STACK:**
```
IsolationLabController.runNonRepeatableReadScenario() -> executor.submit(T1) + executor.submit(T2)
 T1: IsolationAnomalyLab.readTwiceUnderReadCommitted(accountId, ready, committed)
     -> first read (balance=100) -> readerHasReadOnce.countDown() -> awaitLatch(writerCommitted)
 T2 (PARALEL): IsolationAnomalyLab.updateBalanceAfterSignal(...) -> UPDATE + afterCommit() -> committed.countDown()
 T1 devam: second read (balance=200, T2'nin commit'ini GÖRDÜ)
```

**GOOD BREAKPOINTS:**
- `com.interviewlab.transaction.isolation.IsolationAnomalyLab.readTwiceUnderRepeatableRead()` (İKİNCİ read satırı)

**GOOD EXPECTED CALL STACK:** BAD ile AYNI yapı, tek fark `@Transactional(isolation=REPEATABLE_READ)`.

**BREAKPOINT HIT/MISS EXPECTATION:**
- İki thread'in (T1: reader, T2: writer) AYRI Tomcat/executor thread'lerinde olduğunu Threads
  panelinden doğrula — T1'in breakpoint'i T2'nin commit'inden ÖNCE VE SONRA olmak üzere İKİ
  KEZ durur (aynı metot, iki farklı satır)

**WHAT TO INSPECT:** `first`/`second` local değişkenleri — BAD: `100`/`200` (FARKLI), GOOD:
`100`/`100` (AYNI, snapshot korundu)

**WHAT THIS PROVES:** READ_COMMITTED her ifadede veriyi tazeler (non-repeatable read mümkün);
Postgres'te REPEATABLE_READ transaction-başı TEK bir snapshot kullanır (engellenir).

---

## 6. Isolation — Phantom Read

**POSTMAN FOLDER:** `04 Isolation`

**BAD REQUEST:** `POST /api/labs/isolation/phantom-read/read-committed`
**GOOD REQUEST:** `POST /api/labs/isolation/phantom-read/repeatable-read`

**BAD BREAKPOINTS:**
- `com.interviewlab.transaction.isolation.IsolationAnomalyLab.countAboveTwiceUnderReadCommitted()` (İKİNCİ `COUNT(*)` satırı)
- `com.interviewlab.transaction.isolation.IsolationAnomalyLab.insertRowAfterSignal()`

**BAD EXPECTED CALL STACK:**
```
IsolationLabController.runPhantomReadScenario()
 T1: countAboveTwiceUnderReadCommitted(50, ready, committed) -> first COUNT=1 -> await
 T2 (PARALEL): insertRowAfterSignal(newId=2, balance=150, ...) -> INSERT + afterCommit()
 T1 devam: second COUNT=2   [T2'nin YENİ satırı GÖRÜNDÜ - phantom row]
```

**GOOD BREAKPOINTS:** `countAboveTwiceUnderRepeatableRead()` (aynı yapı, REPEATABLE_READ).

**GOOD EXPECTED CALL STACK:** BAD ile AYNI, ikinci COUNT satırı `1` kalır (phantom row snapshot'ta GÖRÜNMEZ).

**BREAKPOINT HIT/MISS EXPECTATION:** `insertRowAfterSignal()` HER İKİ senaryoda da HIT olur (T2
her koşulda INSERT yapar); fark T1'in İKİNCİ COUNT'unun bunu görüp görmediğidir.

**WHAT TO INSPECT:** `first`/`second` (COUNT değerleri) — BAD: `1`/`2`, GOOD: `1`/`1`.

**WHAT THIS PROVES:** Postgres'te REPEATABLE_READ, standart SQL tanımının AKSİNE, phantom
read'i de engeller (tek snapshot).

---

## 7. Isolation — Dirty Read

**POSTMAN FOLDER:** `04 Isolation`

**SENARYO REQUEST:** `POST /api/labs/isolation/dirty-read/read-uncommitted`

**BREAKPOINTS:**
- `com.interviewlab.transaction.isolation.IsolationAnomalyLab.writeWithoutCommittingThenWait()` (T1, `jdbcTemplate.update(UPDATE_BALANCE, uncommittedBalance, ...)` satırından SONRA, `writerHasWrittenUncommitted.countDown()`'dan ÖNCE)
- `com.interviewlab.transaction.isolation.IsolationAnomalyLab.readUnderReadUncommittedWhileOtherTxUncommitted()` (T2, `jdbcTemplate.queryForObject(SELECT_BALANCE, ...)` satırı)

**EXPECTED CALL STACK:**
```
IsolationLabController.dirtyReadUnderReadUncommitted()
 T1: writeWithoutCommittingThenWait(accountId, 999, ...) -> UPDATE (HENÜZ COMMIT DEĞİL) -> signal -> await (transaction HÂLÂ AÇIK)
 T2 (PARALEL, @Transactional(isolation=READ_UNCOMMITTED)): readUnderReadUncommittedWhileOtherTxUncommitted(...) -> SELECT balance
 T1 devam: metottan döner -> GERÇEK COMMIT (999)
```

**BREAKPOINT HIT/MISS EXPECTATION:** T2'nin breakpoint'i, T1'in `writerHasWrittenUncommitted.countDown()`'undan
SONRA ama T1'in metottan DÖNÜŞÜNDEN (gerçek commit) ÖNCE HIT olur — bu ANDA DB'de balance
HÂLÂ eski değeri (`100`) taşır, çünkü Postgres MVCC gereği T1'in commit edilmemiş yazması T2'ye
HİÇ görünmez.

**WHAT TO INSPECT:** `value` (T2'de okunan) — `100` (T1'in henüz commit ETMEDİĞİ `999` DEĞİL).

**WHAT THIS PROVES:** `Isolation.READ_UNCOMMITTED` İSTENSE BİLE, Postgres bunu SESSİZCE
`READ_COMMITTED`'a yükseltir — MVCC mimarisinde kirli okuma fiziksel olarak imkansızdır.

---

## 8. Optimistic Locking

**POSTMAN FOLDER:** `05 Optimistic Lock`

**BAD REQUEST:** `POST /api/labs/optimistic/bad`
**GOOD REQUEST:** `POST /api/labs/optimistic/good`

**BAD BREAKPOINTS:**
- `com.interviewlab.locking.optimistic.bad.NoVersionStockService.loadSignalWaitThenDecrease()` (T2, ayrı executor thread'i)
- `com.interviewlab.locking.optimistic.bad.NoVersionStockService.decreaseImmediately()` (T1, controller thread'i)

**BAD EXPECTED CALL STACK:**
```
OptimisticLockingLabController.bad()
 T2 (executor thread): NoVersionStockService.loadSignalWaitThenDecrease(id, 3, t2Loaded, t1Committed)
     -> SELECT (stock=10) -> t2Loaded.countDown() -> awaitLatch(t1Committed)
 T1 (controller/HTTP thread) devam: NoVersionStockService.decreaseImmediately(id, 2) -> UPDATE stock=8 -> COMMIT -> t1Committed.countDown()
 T2 devam: UPDATE stock = 10-3=7 (T1'in 8'ini GÖRMEDEN, WHERE clause'da version YOK) -> COMMIT (ÜZERİNE YAZAR)
```

**GOOD BREAKPOINTS:**
- `com.interviewlab.locking.optimistic.good.OptimisticStockService.loadSignalWaitThenDecrease()`
- `com.interviewlab.locking.optimistic.good.OptimisticStockService.decreaseStock()`

**GOOD EXPECTED CALL STACK:** AYNI yapı, ama T2'nin UPDATE'i `WHERE id=? AND version=0` üretir —
T1 commit olduğunda version `1`'e çıktığı için T2'nin UPDATE'i 0 satır etkiler ve Hibernate
`ObjectOptimisticLockingFailureException` fırlatır.

**BREAKPOINT HIT/MISS EXPECTATION:** İKİ thread'de de (Threads panelinde `pool-N-thread-1` T2,
`http-nio-8082-exec-N` T1) breakpoint'ler HIT olur — bu, GERÇEKTEN İKİ AYRI, eşzamanlı
transaction olduğunun kanıtıdır (tek thread simülasyonu DEĞİL).

**WHAT TO INSPECT:**
- `reloaded.getStock()` — BAD: `7` (beklenen `5`, lost update), GOOD: `8` (T2 reddedildi, doğru)
- GOOD'da T2'nin exception'ını Variables panelinde incele: `ObjectOptimisticLockingFailureException`
- DBeaver: `SELECT id, stock, version FROM lab_locking_product;`

**WHAT THIS PROVES:** `@Version` olmadan iki eşzamanlı transaction birbirinin yazmasını
GÖRMEDEN üzerine yazabilir (lost update); `@Version` varken Hibernate'in ürettiği
`WHERE version=?` cümlesi bunu FİZİKSEL OLARAK İMKANSIZ hale getirir.

---

## 9. Pessimistic Locking

**POSTMAN FOLDER:** `06 Pessimistic Lock`

**BAD REQUEST:** `POST /api/labs/pessimistic/bad`
**GOOD REQUEST:** `POST /api/labs/pessimistic/good`

**BAD BREAKPOINTS:**
- `com.interviewlab.locking.optimistic.bad.NoVersionStockService.loadSignalWaitThenDecrease()`
- `com.interviewlab.locking.optimistic.bad.NoVersionStockService.decreaseImmediately()`

**BAD EXPECTED CALL STACK:** Optimistic BAD ile AYNI (`NoVersionStockService` burada da
kilitsiz erişimi göstermek için yeniden kullanılıyor).

**GOOD BREAKPOINTS:**
- `com.interviewlab.locking.pessimistic.good.PessimisticStockService.lockHoldThenDecrease()` (T1, `findByIdForUpdate` satırı — GERÇEK `SELECT ... FOR UPDATE`)
- `com.interviewlab.locking.pessimistic.good.PessimisticStockService.lockThenDecreaseImmediately()` (T2)

**GOOD EXPECTED CALL STACK:**
```
PessimisticLockingLabController.good()
 T1 (executor thread): PessimisticStockService.lockHoldThenDecrease(id, 2, t1LockAcquired, releaseT1)
     -> productRepository.findByIdForUpdate(id)   [SELECT ... FOR UPDATE - satır kilidi ALINDI]
     -> t1LockAcquired.countDown() -> awaitLatch(releaseT1)   [kilit HÂLÂ TUTULUYOR]
 T2 (controller/HTTP thread): PessimisticStockService.lockThenDecreaseImmediately(id, 3)
     -> productRepository.findByIdForUpdate(id)   [BURADA BLOKE OLUR - T1 kilidi bırakana kadar]
 T1 devam: UPDATE + COMMIT (kilit SERBEST) -> T2'nin SELECT'i şimdi devam eder
```

**BREAKPOINT HIT/MISS EXPECTATION:** T2'nin `findByIdForUpdate()` breakpoint'i, T1'in `releaseT1`
latch'i tetiklenene kadar (yani T1 commit olana kadar) HIT OLMAZ — Threads panelinde T2'nin
`http-nio-8082-exec-N` thread'i bu sırada **BLOCKED/WAITING** (JDBC seviyesinde, veritabanı
kilidini bekliyor) görünür, bu kritik gözlem noktasıdır.

**WHAT TO INSPECT:**
- `t2BlockedForMillis` — GOOD'da `~515ms` (T1'in `500ms` tuttuğu kilide yakın) — bu GERÇEK bir
  ölçümdür, sabit değil
- DBeaver'da T1 kilidi tutarken: `SELECT * FROM pg_locks WHERE relation = 'lab_locking_product'::regclass;`
- `finalStock` — BAD: `7` (lost update), GOOD: `5` (doğru, T2 T1'i BEKLEDİ)

**WHAT THIS PROVES:** `FOR UPDATE`, ikinci transaction'ı VERİTABANI SEVİYESİNDE fiziksel olarak
bloke eder — race koşulu OLUŞAMAZ (optimistic locking'in aksine, "sonradan reddet" değil
"baştan bekle" stratejisi).

---

## 9b. Deadlock (database-level, FOR UPDATE)

**POSTMAN FOLDER:** `25 Deadlock (database-level, FOR UPDATE)`

**BAD REQUEST:** `POST /api/labs/deadlock/bad?productAId=...&productBId=...`
**GOOD REQUEST:** `POST /api/labs/deadlock/good?productAId=...&productBId=...`

**BAD BREAKPOINTS:**
- `com.interviewlab.locking.deadlock.InconsistentLockOrderTransferService.transferStock()` (HER İKİ `findByIdForUpdate()` çağrısı)

**BAD EXPECTED CALL STACK:**
```
DeadlockLabController.bad() -> executor.submit(T1) + executor.submit(T2)
 T1: InconsistentLockOrderTransferService.transferStock(A, B, ...) -> findByIdForUpdate(A) [KİLİT ALDI] -> await -> findByIdForUpdate(B) [BLOKE - T2 tutuyor]
 T2 (PARALEL): transferStock(B, A, ...) -> findByIdForUpdate(B) [KİLİT ALDI] -> await -> findByIdForUpdate(A) [BLOKE - T1 tutuyor]
 [DÖNGÜ: PostgreSQL kendi deadlock dedektörü ~1s içinde birini "ERROR: deadlock detected" ile İPTAL EDER]
```

**GOOD BREAKPOINTS:**
- `com.interviewlab.locking.deadlock.DeterministicOrderTransferService.transferStock()` (satır: `Long firstId = fromId < toId ? fromId : toId;`)

**GOOD EXPECTED CALL STACK:**
```
DeadlockLabController.good() -> executor.submit(T1) + executor.submit(T2)
 T1: transferStock(A, B, ...) -> firstId=min(A,B) -> findByIdForUpdate(firstId) -> findByIdForUpdate(secondId)
 T2 (PARALEL): transferStock(B, A, ...) -> AYNI firstId hesaplanır -> AYNI kilit sırası -> basitçe SIRAYA GİRER, döngü OLUŞMAZ
```

**BREAKPOINT HIT/MISS EXPECTATION:** BAD'de İKİ thread de İKİNCİ `findByIdForUpdate()` çağrısında
BLOKE olur (Threads panelinde her ikisi de veritabanı kilidini bekliyor görünür) — bu döngü
PostgreSQL'in KENDİSİ tarafından ~1 saniye içinde kırılır (JVM'in `ThreadMXBean`'i DEĞİL).
GOOD'da HİÇBİR thread ikinci çağrıda BLOKE OLMAZ ya da sadece SIRAYLA (deadlock OLUŞTURMADAN) bekler.

**WHAT TO INSPECT:**
- `t1Outcome`/`t2Outcome` — BAD'de biri `COMMITTED`, diğeri GERÇEK Postgres hatası içerir
  (`"ERROR: deadlock detected..."`, bu oturumda GERÇEKTEN alındı, simüle edilmedi)
- `stockConserved` — HER İKİ senaryoda da `true` (100+100=200 hiç kaybolmaz)
- İsteğin toplam süresi (`time curl`) — BAD'de ~1 saniye (Postgres'in `deadlock_timeout`'u), 10
  saniyelik bounded üst sınıra ASLA ulaşmaz
- DBeaver: deadlock ANINDA `SELECT * FROM pg_locks WHERE NOT granted;`

**WHAT THIS PROVES:** Kilitleri tutarsız sırada almak GERÇEK bir veritabanı deadlock'una yol
açar; PostgreSQL bunu KENDİSİ tespit edip taraflardan birini iptal eder (JVM içi deadlock'un
aksine SONSUZA KADAR TAKILI KALMAZ); tutarlı (global) kilit sırası deadlock'u YAPISAL OLARAK
imkansız kılar.

---

## 10. Bean Scope: Singleton

**POSTMAN FOLDER:** `11 Bean Scopes & Lifecycle`

**BAD REQUEST:** `POST /api/labs/scopes/singleton/bad`
**GOOD REQUEST:** `POST /api/labs/scopes/singleton/good`

**BAD BREAKPOINTS:**
- `com.interviewlab.scopes.singleton.bad.MutableSingletonPriceService.calculateDiscountedPrice()`

**BAD EXPECTED CALL STACK:**
```
ScopesLabController.singletonBad() -> 30x executor.submit(() -> mutableSingletonPriceService.calculateDiscountedPrice(...))
 -> MutableSingletonPriceService.calculateDiscountedPrice(basePrice, discount)   [AYNI instance, 30 FARKLI thread'den]
```

**GOOD BREAKPOINTS:**
- `com.interviewlab.scopes.singleton.good.StatelessPriceService.calculateDiscountedPrice()`

**GOOD EXPECTED CALL STACK:** Aynı yapı, `StatelessPriceService` ile (yerel değişkenler, paylaşılan alan YOK).

**BREAKPOINT HIT/MISS EXPECTATION:** breakpoint HER İKİ senaryoda da 30 KEZ HIT olur (30 eşzamanlı
çağrı) — Threads panelinde 30 farklı `pool-N-thread-M`'in AYNI breakpoint'te sırayla/karışık
durduğunu gör. BAD'de `this`'in (MutableSingletonPriceService instance'ı) İÇİNDEKİ mutable
alanın DEĞERİNİN, hangi thread'in son yazdığına göre DEĞİŞTİĞİNİ izle.

**WHAT TO INSPECT:** `callsThatGotWrongResult` — BAD: `~29/30`, GOOD: `0/30`.

**WHAT THIS PROVES:** `@Service` varsayılan olarak singleton'dur — instance alanına yazmak,
sıradan istek işlemeyi bir data race'e çevirir.

---

## 11. Bean Scope: Prototype

**POSTMAN FOLDER:** `11 Bean Scopes & Lifecycle`

**BAD REQUEST:** `POST /api/labs/scopes/prototype/bad`
**GOOD REQUEST:** `POST /api/labs/scopes/prototype/good`

**BAD BREAKPOINTS:**
- `com.interviewlab.scopes.prototype.bad.SingletonWithDirectPrototypeInjection.getWorkerId()`

**BAD EXPECTED CALL STACK:**
```
ScopesLabController.prototypeBad() -> singletonWithDirectPrototypeInjection.getWorkerId() (x2)
 -> SingletonWithDirectPrototypeInjection.getWorkerId()   [constructor'da BİR KEZ inject edilmiş worker alanını döner]
```

**GOOD BREAKPOINTS:**
- `com.interviewlab.scopes.prototype.good.SingletonWithObjectProvider.getWorkerId()`
- `com.interviewlab.scopes.prototype.good.SingletonWithScopedProxy.getWorkerId()`

**GOOD EXPECTED CALL STACK:**
```
ScopesLabController.prototypeGood()
 -> SingletonWithObjectProvider.getWorkerId() (x2) -> objectProvider.getObject()   [HER ÇAĞRIDA yeni instance]
 -> SingletonWithScopedProxy.getWorkerId() (x2) -> scoped proxy -> container'dan yeni instance
```

**BREAKPOINT HIT/MISS EXPECTATION:** BAD'de `getWorkerId()` 2 kez HIT olur ama `this.worker`
alanı İKİ ÇAĞRIDA DA AYNI object id'yi taşır (Variables panelinde kontrol et). GOOD'da
`objectProvider.getObject()`'in DÖNDÜRDÜĞÜ nesnenin object id'si HER ÇAĞRIDA FARKLIDIR.

**WHAT TO INSPECT:** `firstInstanceId`/`secondInstanceId` — BAD: AYNI, GOOD: FARKLI.

**WHAT THIS PROVES:** Prototype bean, singleton'a normal constructor injection ile inject
edilirse SADECE BİR KEZ alınır ve sonsuza dek AYNI instance'a işaret eder; `ObjectProvider`/scoped
proxy her erişimde GERÇEKTEN container'dan yeni bir instance ister.

---

## 12. Bean Scope: Request

**POSTMAN FOLDER:** `11 Bean Scopes & Lifecycle`

**SENARYO REQUEST:** `GET /lab/scopes/request` (aynı Postman isteğini İKİ AYRI kez gönder)

**BREAKPOINTS:**
- `com.interviewlab.scopes.webscopes.RequestScopedIdHolder` constructor'ı

**EXPECTED CALL STACK:**
```
ScopesController.requestScope() -> requestScopedIdHolder.getId() (x2, AYNI HTTP request İÇİNDE)
```

**HIT/MISS EXPECTATION:** Constructor breakpoint'i HER YENİ HTTP isteğinde TAM OLARAK BİR KEZ
HIT olur — aynı request içindeki iki `getId()` çağrısı constructor'ı TEKRAR TETİKLEMEZ.

**WHAT TO INSPECT:** `firstRead`/`secondReadSameRequest` (aynı istek içinde) — AYNI; iki AYRI
Postman çağrısı arasında `RequestScopedIdHolder`'ın `id` alanı FARKLI.

**WHAT THIS PROVES:** Request scope, GERÇEK bir Spring web-scope proxy mekanizmasıdır — manuel
`new RequestScopedIdHolder()` ile AYNI DEĞİLDİR.

---

## 13. Bean Scope: Session

**POSTMAN FOLDER:** `11 Bean Scopes & Lifecycle`

**SENARYO REQUEST:** `GET /lab/scopes/session` (Postman Cookie Jar ile art arda; sonra cookie temizleyip tekrar)

**BREAKPOINTS:**
- `com.interviewlab.scopes.webscopes.SessionScopedIdHolder` constructor'ı

**EXPECTED CALL STACK:** `ScopesController.sessionScope() -> sessionScopedIdHolder.getId()`

**HIT/MISS EXPECTATION:** Aynı session'daki (aynı `JSESSIONID` cookie) ardışık çağrılarda
constructor breakpoint'i SADECE İLK çağrıda HIT olur; cookie temizlendikten SONRAKİ çağrıda
TEKRAR HIT olur.

**WHAT TO INSPECT:** `sessionScopedId` — aynı session'da SABİT, cookie temizlenince DEĞİŞİR.

**WHAT THIS PROVES:** Session scope, aynı HTTP session boyunca instance'ı YENİDEN KULLANIR.

---

## 14. Bean Scope: Application

**POSTMAN FOLDER:** `11 Bean Scopes & Lifecycle`

**SENARYO REQUEST:** `GET /lab/scopes/application` (uygulama YENİDEN BAŞLATILMADAN art arda)

**BREAKPOINTS:** `com.interviewlab.scopes.webscopes.ApplicationScopedIdHolder` constructor'ı

**HIT/MISS EXPECTATION:** Uygulamanın YAŞAM SÜRESİ boyunca constructor SADECE BİR KEZ HIT
olur (kaç HTTP çağrısı yapılırsa yapılsın).

**WHAT TO INSPECT:** `applicationScopedId` — TÜM çağrılarda AYNI.

**WHAT THIS PROVES:** Application scope, `ServletContext` ömrü boyunca TEK bir instance paylaşır.

---

## 15. Bean Lifecycle

**POSTMAN FOLDER:** `11 Bean Scopes & Lifecycle`

**SENARYO REQUEST:** `GET /api/labs/scopes/lifecycle` (uygulama BAŞLANGICINDA gözlemlenir, HTTP çağrısı sırasında DEĞİL)

**BREAKPOINTS (uygulama Debug modda BAŞLARKEN koy):**
- `com.interviewlab.scopes.lifecycle.BeanLifecycleDemoBean` constructor'ı
- `com.interviewlab.scopes.lifecycle.BeanLifecycleDemoBean.init()` (`@PostConstruct`)
- `com.interviewlab.scopes.lifecycle.BeanLifecycleLoggingPostProcessor.postProcessBeforeInitialization()`
- `com.interviewlab.scopes.lifecycle.BeanLifecycleLoggingPostProcessor.postProcessAfterInitialization()`

**EXPECTED CALL STACK (uygulama başlangıcında, SIRAYLA):**
```
AbstractAutowireCapableBeanFactory.createBeanInstance(...) -> BeanLifecycleDemoBean() [constructor]
AbstractAutowireCapableBeanFactory.initializeBean(...)
 -> applyBeanPostProcessorsBeforeInitialization(...) -> BeanLifecycleLoggingPostProcessor.postProcessBeforeInitialization(...)
 -> invokeInitMethods(...) -> BeanLifecycleDemoBean.init() [@PostConstruct]
 -> applyBeanPostProcessorsAfterInitialization(...) -> BeanLifecycleLoggingPostProcessor.postProcessAfterInitialization(...)
```

**HIT/MISS EXPECTATION:** 4 breakpoint TAM OLARAK BU SIRAYLA, uygulama başlarken BİR KEZ HIT
olur. HTTP isteği sırasında (GET /lifecycle çağrıldığında) HİÇBİRİ TEKRAR HIT OLMAZ (sadece
statik olarak kaydedilmiş event listesi okunur).

**WHAT TO INSPECT:** her breakpoint'te `beanName` = `"beanLifecycleDemoBean"`.

**WHAT THIS PROVES:** `@PostConstruct`'ın kendisi bile, Spring'in
`CommonAnnotationBeanPostProcessor`'ı tarafından çağrılan bir `BeanPostProcessor` hook'udur.

---

## 16. Spring AOP Self-Invocation (+ Proxy Introspection)

**POSTMAN FOLDER:** `12 AOP`

**BAD REQUEST:** `POST /api/labs/aop/self-invocation/bad`
**GOOD REQUEST:** `POST /api/labs/aop/self-invocation/good`

**BAD BREAKPOINTS:**
- `com.interviewlab.aop.bad.SelfInvocationTimingService.processOrder()`
- `com.interviewlab.aop.bad.SelfInvocationTimingService.slowStep()`
- `com.interviewlab.aop.ExecutionTimeAspect.trackExecutionTime()` (NOT HIT beklenir)

**BAD EXPECTED CALL STACK:**
```
AopLabController.selfInvocationBad() -> SelfInvocationTimingService.processOrder()
 -> this.slowStep()   [self-invocation, proxy'ye UĞRAMAZ]
```

**GOOD BREAKPOINTS:**
- `com.interviewlab.aop.good.OrderProcessingService.processOrder()`
- `com.interviewlab.aop.ExecutionTimeAspect.trackExecutionTime()` (HIT beklenir)
- `com.interviewlab.aop.good.SlowStepService.slowStep()`

**GOOD EXPECTED CALL STACK:**
```
AopLabController.selfInvocationGood() -> OrderProcessingService.processOrder()
 -> slowStepService.slowStep()   [SlowStepService$$SpringCGLIB$$0 proxy]
     -> ExecutionTimeAspect.trackExecutionTime(ProceedingJoinPoint)   [interceptionCount++]
         -> pjp.proceed() -> SlowStepService.slowStep() [gerçek hedef]
```

**BREAKPOINT HIT/MISS EXPECTATION:**
- BAD: `SelfInvocationTimingService.processOrder()` HIT, `.slowStep()` HIT, `ExecutionTimeAspect.trackExecutionTime()` **NOT HIT**
- GOOD: `OrderProcessingService.processOrder()` HIT, `ExecutionTimeAspect.trackExecutionTime()` **HIT**, `SlowStepService.slowStep()` HIT

**WHAT TO INSPECT:**
- `ExecutionTimeAspect.interceptionCount` alanı (GERÇEK `AtomicInteger`, sahte değil) — BAD
  çağrısı ÖNCESİ/SONRASI AYNI kalır, GOOD çağrısı ÖNCESİ/SONRASI 1 artar (curl ile bu oturumda
  GERÇEKTEN doğrulandı: bkz. `docs/RUNTIME_VERIFICATION.md`)
- `this.getClass().getName()` (BAD'de `SelfInvocationTimingService`, proxy soneki YOK)
- `slowStepService` alanının `getClass().getName()`'i (GOOD'da `...SlowStepService$$SpringCGLIB$$0`)

**WHAT THIS PROVES:** Spring AOP advice proxy sınırında uygulanır — `this.foo()` asla proxy'ye
ulaşmaz.

---

## 17. Spring @Async Self-Invocation

**POSTMAN FOLDER:** `09 Spring Async`

**BAD REQUEST:** `POST /api/labs/async/bad`
**GOOD REQUEST:** `POST /api/labs/async/good`

**BAD BREAKPOINTS:**
- `com.interviewlab.async.spring.bad.SelfInvocationNotificationService.notifyUser()`
- `com.interviewlab.async.spring.bad.SelfInvocationNotificationService.sendAsync()` (satır: `Thread.currentThread().getName()` okunan yer)

**BAD EXPECTED CALL STACK:**
```
AsyncLabController.bad() -> SelfInvocationNotificationService.notifyUser(message)
 -> this.sendAsync(message)   [self-invocation - @Async proxy'ye UĞRAMAZ, SENKRON çalışır]
```

**GOOD BREAKPOINTS:**
- `com.interviewlab.async.spring.good.NotificationService.notifyUser()`
- `com.interviewlab.async.spring.good.AsyncSender.sendAsync()`

**GOOD EXPECTED CALL STACK:**
```
AsyncLabController.good() -> NotificationService.notifyUser(message)
 -> asyncSender.sendAsync(message)   [AsyncSender$$SpringCGLIB$$0 proxy üzerinden]
     -> labAsyncExecutor havuzuna SUBMIT -> AYRI thread'de AsyncSender.sendAsync(message) GERÇEKTEN çalışır
```

**BREAKPOINT HIT/MISS EXPECTATION:** BAD'de breakpoint HTTP request thread'inde (`http-nio-8082-exec-N`)
HIT olur; GOOD'da AYNI isimli metot HTTP thread'inden TAMAMEN FARKLI bir thread'de
(`pool-N-thread-M`) HIT olur — Threads panelinde bu FARKI doğrudan gör.

**WHAT TO INSPECT:** `callerThread` vs `workRanOnThread` — BAD: AYNI, GOOD: FARKLI.

**WHAT THIS PROVES:** `@Async` da AOP proxy'sine dayanır — self-invocation onu sessizce senkron
yapar.

---

## 18. Race Condition (lost update)

**POSTMAN FOLDER:** `19 Race Condition (concurrency counter)`

**BAD REQUEST:** `POST /api/labs/concurrency/counter/bad?threads=20&increments=10000`
**GOOD REQUEST:** `POST /api/labs/concurrency/counter/good?threads=20&increments=10000`

**BAD BREAKPOINTS:**
- `com.interviewlab.concurrency.race.bad.UnsynchronizedIntCounter.increment()` (satır: `count++;`)

**BAD EXPECTED CALL STACK:**
```
RaceConditionLabController.bad() -> runConcurrently(20, task) -> (20 worker thread) -> UnsynchronizedIntCounter.increment()
```

**GOOD BREAKPOINTS:**
- `com.interviewlab.concurrency.atomic.AtomicCounterService.increment()`

**GOOD EXPECTED CALL STACK:** Aynı yapı, `AtomicCounterService.increment()` (`AtomicInteger.incrementAndGet()`) ile.

**BREAKPOINT HIT/MISS EXPECTATION:** breakpoint 200.000 KEZ (20×10.000) HIT olur — koşullu
breakpoint olmadan pratik değildir, bunun yerine **Threads** panelinde BAD'de birden fazla
thread'in AYNI ANDA `count++` satırında durduğunu gözlemle (RUNNABLE, çakışma potansiyeli).

**WHAT TO INSPECT:** response'taki `actual`/`lostUpdates` — BAD: nondeterministic (koşudan
koşuya DEĞİŞİR, ör. bu oturumda `33899`), GOOD: HER ZAMAN `200000`.

**WHAT THIS PROVES:** `count++` atomik değildir (oku-artır-yaz); `AtomicInteger` CAS ile bunu
tek adıma indirir.

---

## 19. synchronized

**POSTMAN FOLDER:** `20 Locking Primitives (synchronized-ReentrantLock-RWLock-StampedLock)`

**BAD REQUEST:** `POST /api/labs/concurrency/synchronized/bad?threads=10&increments=2000`
**GOOD REQUEST:** `POST /api/labs/concurrency/synchronized/good?threads=10&increments=2000`

**BAD BREAKPOINTS:** `com.interviewlab.concurrency.race.bad.UnsynchronizedIntCounter.increment()`
**GOOD BREAKPOINTS:** `com.interviewlab.concurrency.synchronization.good.IntrinsicMonitorCounter.incrementViaMethod()`

**GOOD EXPECTED CALL STACK:**
```
LockingPrimitivesLabController.synchronizedGood() -> runConcurrently(10, task) -> (10 worker thread) -> IntrinsicMonitorCounter.incrementViaMethod()
```

**BREAKPOINT HIT/MISS EXPECTATION:** GOOD'da breakpoint'e ULAŞAN thread'lerden SADECE BİRİ
RUNNABLE olur, DİĞER 9'u **Threads** panelinde **BLOCKED** görünür (monitor bekliyor) — BAD'de
AYNI breakpoint'e bakarsan TÜM thread'ler RUNNABLE'dır (kimse beklemez).

**WHAT TO INSPECT:** `raceObserved` — BAD: `true`, GOOD: `false`.

**WHAT THIS PROVES:** `synchronized`, monitor bekleyen thread'leri BLOCKED durumuna sokarak
serileştirir.

---

## 20. ReentrantLock (lifecycle log)

**POSTMAN FOLDER:** `20 Locking Primitives (synchronized-ReentrantLock-RWLock-StampedLock)`

**BAD REQUEST:** `POST /api/labs/concurrency/reentrant-lock/bad?threads=4`
**GOOD REQUEST:** `POST /api/labs/concurrency/reentrant-lock/good?threads=4`

**BREAKPOINTS (HER İKİ senaryoda AYNI metot, `lock` parametresi `null` vs gerçek `ReentrantLock`):**
- `com.interviewlab.web.lab.concurrency.LockingPrimitivesLabController.runLockLifecycleDemo()` (satır: `lifecycleLog.add(threadLabel + " LOCK_ACQUIRED");`)
- aynı metotta: `maxConcurrentHolders.updateAndGet(...)` satırı

**BAD EXPECTED CALL STACK:** `reentrantLockBad()` → `runLockLifecycleDemo(4, null)` → 4 worker
thread'in HEPSİ `LOCK_ACQUIRED`'a HEMEN ULAŞIR (kilit yok).
**GOOD EXPECTED CALL STACK:** `reentrantLockGood()` → `runLockLifecycleDemo(4, new ReentrantLock())`
→ worker'lar SIRAYLA `lock.lock()`'ta bekler.

**BREAKPOINT HIT/MISS EXPECTATION:** BAD'de breakpoint 4 KEZ, HEMEN ARDI ARDINA (çakışarak) HIT
olur — `currentHolders.get()` o anda `2`, `3`, hatta `4` olabilir. GOOD'da 4 KEZ HIT olur ama
HER SEFERİNDE `currentHolders.get()` KESİNLİKLE `1`'dir (bir sonraki HIT, öncekinin
`LOCK_RELEASED`'inden SONRA gelir).

**WHAT TO INSPECT:** `maxConcurrentHolders` — BAD: `>1` (bu oturumda `4`), GOOD: HER ZAMAN `1`.

**WHAT THIS PROVES:** `ReentrantLock.lock()/unlock()`, kritik bölgeyi mutual exclusion ile
serileştirir; kilit yokken eşzamanlı giriş MÜMKÜNDÜR.

---

## 21. ReadWriteLock

**POSTMAN FOLDER:** `20 Locking Primitives (synchronized-ReentrantLock-RWLock-StampedLock)`

**SENARYO REQUEST:** `POST /api/labs/concurrency/read-write-lock/demo?readerCount=5`

**BREAKPOINTS:** `com.interviewlab.concurrency.synchronization.good.ReadWriteLockCache.withReadLockHeld()`

**EXPECTED CALL STACK:** `LockingPrimitivesLabController.readWriteLockDemo()` → 5 worker
thread, HEPSİ AYNI ANDA → `ReadWriteLockCache.withReadLockHeld(...)`.

**HIT/MISS EXPECTATION:** breakpoint 5 KEZ, HEPSİ BİRBİRİNE ÇOK YAKIN zamanda HIT olur —
Threads panelinde 5 thread'in AYNI ANDA bu satırda durduğunu gör (hiçbiri diğerini beklemiyor).

**WHAT TO INSPECT:** `allReadersAcquiredConcurrently` — `true`; `ReentrantReadWriteLock.getReadLockCount()`'u
Evaluate Expression ile sorgula: `5`.

**WHAT THIS PROVES:** ReadWriteLock, okuyucuların AYNI ANDA ilerlemesine izin verir (sadece
yazıcıyı dışlar).

---

## 22. StampedLock (optimistic read)

**POSTMAN FOLDER:** `20 Locking Primitives (synchronized-ReentrantLock-RWLock-StampedLock)`

**SENARYO REQUEST:** `POST /api/labs/concurrency/stamped-lock/demo`

**BREAKPOINTS:** `com.interviewlab.concurrency.synchronization.good.StampedLockPoint.distanceFromOrigin()`
(satır: `long stamp = lock.tryOptimisticRead();` VE `lock.validate(stamp)`)

**EXPECTED CALL STACK:** `LockingPrimitivesLabController.stampedLockDemo()` → `StampedLockPoint.distanceFromOrigin()`.

**HIT/MISS EXPECTATION:** breakpoint 1 KEZ HIT olur; bu satırda `lock.lock()`/`lock.readLock()`
gibi BİR KİLİT ÇAĞRISI ASLA YOKTUR (Call Stack'te doğrula).

**WHAT TO INSPECT:** `stamp` (`0` DEĞİL); `validate(stamp)` sonucu `true`.

**WHAT THIS PROVES:** Optimistic read, kilit ALMADAN okur, SONRADAN doğrular — neredeyse
bedava bir okuma yolu.

---

## 23. ABA Problem

**POSTMAN FOLDER:** `20 Locking Primitives (synchronized-ReentrantLock-RWLock-StampedLock)`

**BAD REQUEST:** `POST /api/labs/concurrency/aba/bad`
**GOOD REQUEST:** `POST /api/labs/concurrency/aba/good`

**BAD BREAKPOINTS:** `com.interviewlab.concurrency.atomic.AbaProblemDemo.casSucceedsDespiteIntermediateChange()` (SON satır)
**GOOD BREAKPOINTS:** `com.interviewlab.concurrency.atomic.AbaProblemDemo.stampedCasDetectsIntermediateChange()` (SON satır)

**BAD EXPECTED CALL STACK:** `LockingPrimitivesLabController.abaBad()` → `AbaProblemDemo.casSucceedsDespiteIntermediateChange(ref,"A","B")`.
**GOOD EXPECTED CALL STACK:** `LockingPrimitivesLabController.abaGood()` → `AbaProblemDemo.stampedCasDetectsIntermediateChange(ref,"A","B")`.

**BREAKPOINT HIT/MISS EXPECTATION:** her ikisi de 1 KEZ HIT olur — fark, `return`
ifadesinin DEĞERİNDEDİR (Step Over ile gör).

**WHAT TO INSPECT:** `casSucceededDespiteIntermediateChange` — BAD: `true` (yanlış pozitif),
GOOD: `false` (doğru red, damga uyuşmazlığı sayesinde).

**WHAT THIS PROVES:** Saf `AtomicReference` CAS'i sadece DEĞERE bakar, `AtomicStampedReference`
bir DAMGA ile aradaki mutasyonu yakalar.

---

## 24. volatile

**POSTMAN FOLDER:** `21 volatile (visibility vs atomicity)`

**BAD REQUEST:** `POST /api/labs/concurrency/volatile/misconception-check?threads=20&increments=2000`
**GOOD REQUEST:** `POST /api/labs/concurrency/volatile/correct-usage`

**BAD BREAKPOINTS:** `com.interviewlab.concurrency.volatiletopic.bad.VolatileCounter.increment()`
**GOOD BREAKPOINTS:** `com.interviewlab.concurrency.volatiletopic.good.ShutdownFlagWorker.requestStop()`

**BAD EXPECTED CALL STACK:** `VolatileLabController.misconceptionCheck()` → 20 worker thread → `VolatileCounter.increment()`.
**GOOD EXPECTED CALL STACK:** `VolatileLabController.correctUsage()` → `worker.requestStop()` (TEK atama) → AYRI thread'de `run()` döngüsü `stopRequested`'ı okur.

**BREAKPOINT HIT/MISS EXPECTATION:** BAD'de aynı Race Condition lab'ındaki GİBİ birden fazla
thread aynı satırda çakışır (`volatile` OLMASINA RAĞMEN). GOOD'da `requestStop()` 1 KEZ HIT
olur, AYRI worker thread'in `while(!stopRequested)` kontrolü NEREDEYSE HEMEN `false`'a döner.

**WHAT TO INSPECT:** BAD: `stillRaced` (`true`, `actual≠expected`); GOOD: `workerStoppedWithinTimeout` (`true`).

**WHAT THIS PROVES:** `volatile` sadece visibility sağlar, atomicity SAĞLAMAZ.

---

## 25. ThreadLocal Sızıntısı

**POSTMAN FOLDER:** `10 ThreadLocal`

**BAD REQUEST:** `POST /api/labs/threadlocal/bad`
**GOOD REQUEST:** `POST /api/labs/threadlocal/good`

**BAD BREAKPOINTS:** `com.interviewlab.threadlocal.bad.LeakyCorrelationIdService.getCorrelationId()` (task2'nin çağrısı)
**GOOD BREAKPOINTS:** aynı sınıfın `remove()` çağrılan GOOD servis eşdeğeri (`try/finally` içinde `.remove()`)

**BAD EXPECTED CALL STACK:**
```
ThreadLocalLabController.bad() -> tek-thread'li executor -> task1: set + read -> task2 (AYNI worker thread'de): getCorrelationId()
```

**BREAKPOINT HIT/MISS EXPECTATION:** task2'nin breakpoint'i HIT olduğunda, `Thread.currentThread()`'in
task1 ile AYNI worker thread (`pool-N-thread-1`) olduğunu Threads panelinden doğrula.

**WHAT TO INSPECT:** `task2ReadValue` — BAD: task1'in id'si (SIZINTI), GOOD: `"null"` (temiz).

**WHAT THIS PROVES:** `ThreadLocal` değeri, thread pool'daki bir worker thread'e YAPIŞIR — `remove()`
çağrılmadıkça YAŞAMAYA DEVAM EDER.

---

## 26. ExecutorService / ThreadPoolExecutor Kuyruğu

**POSTMAN FOLDER:** `07 ExecutorService`

**BAD REQUEST:** `POST /api/labs/executor/bad`
**GOOD REQUEST:** `POST /api/labs/executor/good`

**BAD BREAKPOINTS:** `com.interviewlab.web.lab.executor.ExecutorLabController.bad()` (satır: `int queueSizeWhileBlocked = tpe.getQueue().size();`)
**GOOD BREAKPOINTS:** aynı controller'ın `good()` metodu, `queueSizeBeforeRelease` satırı

**HIT/MISS EXPECTATION:** her ikisi de 1 KEZ HIT olur; `tpe`/`executor` (bir `ThreadPoolExecutor`)
Variables panelinde genişlet — `workQueue` alanının BAD'de `18` eleman, GOOD'da `3` eleman
(kapasiteye TAKILMIŞ) taşıdığını gör.

**WHAT TO INSPECT:** `queueSizeWhileAllWorkersBusy`/`tasksRejected` — BAD: `18`/`0`, GOOD: `3`/`5`.

**WHAT THIS PROVES:** "2 thread'lik havuz" ile "kuyruk boyutu" TAMAMEN AYRI şeylerdir.

---

## 27-29. Rejection Policies (CallerRuns / Discard / DiscardOldest)

**POSTMAN FOLDER:** `07 ExecutorService`

**REQUESTS:** `POST /api/labs/executor/caller-runs`, `POST /api/labs/executor/discard`, `POST /api/labs/executor/discard-oldest`

**BREAKPOINTS:**
- CallerRuns: `com.interviewlab.web.lab.executor.ExecutorLabController.callerRuns()` (satır: `executor.execute(() -> executedOnThreadName.set(...))`)
- Discard/DiscardOldest: `com.interviewlab.web.lab.executor.ExecutorLabController.runDiscardDemo()` (satır: `executor.submit(() -> executedTaskIds.add(3));`)

**EXPECTED CALL STACK (Discard):**
```
ExecutorLabController.discard() -> runDiscardDemo(discardPolicyExecutor(1,1,1), ...)
 -> submit(task1) [worker'ı işgal eder] -> submit(task2) [kuyruğu doldurur] -> submit(task3)
     -> ThreadPoolExecutor.execute(task3) -> queue.offer FAILS -> DiscardPolicy.rejectedExecution(task3, executor) [BOŞ gövde]
```

**HIT/MISS EXPECTATION:** `DiscardPolicy.rejectedExecution()`'a (JDK kaynağı) breakpoint
koyarsan HIT olur ama gövdesi TAMAMEN BOŞTUR (`{}`) — bu "sessizce atma"nın LİTERAL kanıtıdır.
`DiscardOldestPolicy.rejectedExecution()`'da ise `e.getQueue().poll()` + `e.execute(r)`
çağrıları GÖRÜLÜR.

**WHAT TO INSPECT:** `executedTaskIds` — discard: `[1,2]` (3 asla), discardOldest: `[1,3]` (2 asla); CallerRuns: `ranSynchronouslyOnCallingThread=true`.

**WHAT THIS PROVES:** Her rejection policy, kapasite aşıldığında FARKLI bir strateji uygular.

---

## 30. CompletableFuture

**POSTMAN FOLDER:** `08 CompletableFuture`

**BAD REQUEST:** `POST /api/labs/completable-future/sequential`
**GOOD REQUEST:** `POST /api/labs/completable-future/parallel`

**BAD BREAKPOINTS:** `com.interviewlab.async.completablefuture.bad.BlockingGetAggregationService.aggregateSequentiallyByBlocking()`
**GOOD BREAKPOINTS:** `com.interviewlab.async.completablefuture.good.ComposedAggregationService.aggregateConcurrently()` (satır: `CompletableFuture.allOf(...)`)

**GOOD EXPECTED CALL STACK:**
```
CompletableFutureLabController.parallel() -> ComposedAggregationService.aggregateConcurrently(...)
 -> 3x supplyAsync() [HEPSİ ZATEN BAŞLADI] -> CompletableFuture.allOf(...)
```

**HIT/MISS EXPECTATION:** GOOD'daki breakpoint'e gelindiğinde 3 `supplyAsync` ÇAĞRISI ZATEN
yapılmış olmalı (Variables panelinde `ordersFuture` vb. incele); **Threads** panelinde
birden fazla worker thread'in AYNI ANDA RUNNABLE olduğunu say.

**WHAT TO INSPECT:** `threadNames`/`durationMillis` — BAD: `~919ms` (TOPLAM), GOOD: `~303ms` (MAKSİMUM), her ikisinde de `threadNames` 3 farklı isim (GERÇEK, `Thread.currentThread().getName()` ile toplanır).

**WHAT THIS PROVES:** `supplyAsync()` ANINDA döner; art arda `.get()` çağırmak paralelliği YOK EDER.

---

## 31. N+1

**POSTMAN FOLDER:** `14 N+1`

**BAD REQUEST:** `GET /api/labs/n-plus-one/bad`
**GOOD REQUEST:** `GET /api/labs/n-plus-one/good`

**BAD BREAKPOINTS:** `com.interviewlab.jpa.bad.NPlusOneOrderService.countAllItemsAcrossOrders()` (döngü İÇİNDE, `order.getOrderItems().size()` satırı)
**GOOD BREAKPOINTS:** `com.interviewlab.jpa.good.FetchJoinOrderService.countAllItemsAcrossOrders()`, `EntityGraphOrderService.countAllItemsAcrossOrders()`, `DtoProjectionOrderService.countAllItemsAcrossOrders()`

**HIT/MISS EXPECTATION:** BAD'deki breakpoint 10 KEZ (sipariş sayısı kadar) HIT olur — her
HIT'te konsolda YENİ bir `Hibernate: select ... from lab_jpa_order_item ...` satırı görünür.
GOOD'daki breakpoint'ler 1 KEZ HIT olur.

**WHAT TO INSPECT:** `queryCount` — BAD: `11`, GOOD: fetchJoin `2`, entityGraph `1`, dto `1`.

**WHAT THIS PROVES:** Lazy koleksiyona döngü içinde erişmek 1+N sorgu üretir; fetch join/entity
graph/DTO bunu çözer (ama fetch join'in `product` için 2. sorguya İHTİYACI VARDIR — bkz. lesson).

---

## 32. Lazy Fetch (LazyInitializationException)

**POSTMAN FOLDER:** `23 Lazy-Eager Fetch`

**BAD REQUEST:** `GET /api/labs/fetch/lazy/bad?orderId=...`
**GOOD REQUEST:** `GET /api/labs/fetch/lazy/good?orderId=...`

**BAD BREAKPOINTS:**
- `com.interviewlab.jpa.bad.LazyInitializationDemoService.loadOrderWithoutTouchingItems()`
- `com.interviewlab.web.lab.fetch.LazyEagerLabController.lazyBad()` (satır: `order.getOrderItems().size();`)

**BAD EXPECTED CALL STACK:**
```
LazyEagerLabController.lazyBad() -> LazyInitializationDemoService.loadOrderWithoutTouchingItems(id) [metot DÖNER, session KAPANIR]
 -> order.getOrderItems().size()   [session ARTIK YOK -> throw LazyInitializationException]
```

**GOOD BREAKPOINTS:** `com.interviewlab.jpa.good.LazyAccessWithinTransactionService.loadOrderAndCountItemsWithinTransaction()`

**BREAKPOINT HIT/MISS EXPECTATION:** BAD'de `LazyInitializationDemoService` metodu HIT olur ve
NORMAL döner; hemen ardından `lazyBad()`'daki `.getOrderItems()` satırında GERÇEK bir exception
FIRLAR (Step Into ile debugger'ın "Exception breakpoint" tarzı durduğunu gör, ya da normal
breakpoint'ten sonra Step Over ile exception'ı yakala).

**WHAT TO INSPECT:** `threwLazyInitializationException` — BAD: `true` (GERÇEK Hibernate mesajı), GOOD: `false`.

**WHAT THIS PROVES:** Lazy koleksiyon, sadece onu yükleyen transaction/session AÇIKKEN erişilebilir.

---

## 33. Eager Fetch

**POSTMAN FOLDER:** `23 Lazy-Eager Fetch`

**SENARYO REQUEST:** `GET /api/labs/fetch/eager/bad`

**BREAKPOINTS:** `com.interviewlab.jpa.bad.EagerFetchAlwaysLoadsService.sumQuantitiesOnly()` (satır: `orderItemRepository.findAll();`)

**HIT/MISS EXPECTATION:** breakpoint 1 KEZ HIT olur; Step Over ile `items` listesindeki her
`OrderItem`'ı genişlet — `product` alanının BAŞLATILMIŞ (proxy DEĞİL) olduğunu gör, metot
`product`'ı HİÇ OKUMAMASINA RAĞMEN.

**WHAT TO INSPECT:** `queriesTouchingProductTable` — `>0` (GERÇEK `SqlStatementRecorder` ölçümü).

**WHAT THIS PROVES:** `FetchType.EAGER`, hiç kullanılmayan ilişkiyi bile HER YÜKLEMEDE sorgular.

---

## 34. Cache-Aside

**POSTMAN FOLDER:** `24 Cache-Aside`

**BAD REQUEST:** `GET /api/labs/cache/bad/read?id=1`
**GOOD REQUEST:** `GET /api/labs/cache/good/read?id=1`

**BAD BREAKPOINTS:** `com.interviewlab.javacore.cache.NoCacheProductService.read()`
**GOOD BREAKPOINTS:** `com.interviewlab.javacore.cache.CacheAsideProductService.read()` (satır: `String cached = cache.get(id);`), `.write()` (satır: `cache.remove(id);`)

**HIT/MISS EXPECTATION:** BAD'de breakpoint HER çağrıda HIT olur (cache YOK). GOOD'da İLK
çağrıda `cached==null` (miss dalı çalışır, `databaseReadCount` artar), İKİNCİ çağrıda
`cached` DOLU döner (hit dalı, DB'ye HİÇ gidilmez — Step Into ile `database.get(id)`'nin
ÇAĞRILMADIĞINI doğrula).

**WHAT TO INSPECT:** `databaseReadCount` — BAD: her çağrıda +1, GOOD: sadece miss'te +1.

**WHAT THIS PROVES:** Cache-Aside, DB yükünü SADECE cache miss'lerine indirger; `write()`
cache'i GÜNCELLEMEK yerine SİLER (invalidate).

---

## 35. Resilience: Retry / Retry Exhausted

**POSTMAN FOLDER:** `17 Resilience4j`

**REQUESTS:** `POST /api/labs/resilience/retry`, `POST /api/labs/resilience/retry-exhausted`

**BREAKPOINTS:** `com.interviewlab.resilience.FlakyExternalService.call()` (satır: `if (alwaysFail || attempt <= failFirstNCalls)`)

**HIT/MISS EXPECTATION:** `/retry`'de breakpoint 3 KEZ HIT olur (`attempt`: `1,2,3` — ilk 2
exception fırlatır, 3. başarılı olur, Resilience4j'nin KENDİSİ tekrar dener, kod tabanında
HİÇBİR retry döngüsü YAZILMAMIŞTIR). `/retry-exhausted`'da yine 3 KEZ HIT olur ama HEPSİ
exception fırlatır.

**WHAT TO INSPECT:** `attempt` değişkeni (her HIT'te artar); `actualCallCount` — her ikisinde de `3`.

**WHAT THIS PROVES:** Retry, geçici hataları ÇAĞIRANA HİÇ BELLİ ETMEDEN telafi eder; sınırsız DEĞİLDİR.

---

## 36. Resilience: Circuit Breaker (+ `/external/config`)

**POSTMAN FOLDER:** `17 Resilience4j`

**SENARYO REQUESTS:** `POST /api/labs/resilience/external/config` (body: `{"failNext":10,"delayMs":0}`) → `POST /api/labs/resilience/circuit-breaker`

**BREAKPOINTS:**
- `com.interviewlab.web.lab.resilience.ResilienceLabController.configureExternal()`
- `com.interviewlab.web.lab.resilience.ResilienceLabController.circuitBreaker()` (satır: `stateTransitions.add(circuitBreaker.getState().name());`)

**HIT/MISS EXPECTATION:** `configureExternal()` 1 KEZ HIT olur (config kaydedilir).
`circuitBreaker()`'daki breakpoint 2 KEZ HIT olur (4 başarısız çağrıdan ÖNCE `CLOSED`, SONRA
`OPEN`). `/external/config` ÇAĞRILMADAN doğrudan `/circuit-breaker` çağrılırsa, breakpoint YİNE
2 KEZ HIT olur ama `circuitBreaker.getState()` HER İKİSİNDE DE `"CLOSED"` döner (devre HİÇ açılmaz).

**WHAT TO INSPECT:** `flakyExternalService.failFirstNCalls` alanı (config'ten SONRA `10`);
`stateTransitions`.

**WHAT THIS PROVES:** Circuit breaker senaryosu, önceden yapılandırılabilir bir external
dependency config pattern'ine bağımlıdır (kullanıcının istediği TAM pattern).

---

## 37-40. Resilience: RateLimiter / Bulkhead / Timeout / Fallback

**POSTMAN FOLDER:** `17 Resilience4j`

**REQUESTS:** `POST /api/labs/resilience/rate-limiter`, `/bulkhead`, `/timeout`, `/fallback`

**BREAKPOINTS:**
- RateLimiter: `com.interviewlab.web.lab.resilience.ResilienceLabController.rateLimiter()` (döngü içi `decorated.get()` çağrısı)
- Bulkhead: aynı controller'ın `bulkhead()` metodu, executor içindeki `decorated.get()`
- Timeout: `timeout()` metodu, `timeLimiter.executeFutureSupplier(futureSupplier)`
- Fallback: `fallback()` metodu, catch bloğu

**HIT/MISS EXPECTATION:** RateLimiter: 5 KEZ HIT, 2'si `permitted++`'a, 3'ü `RequestNotPermitted`
catch bloğuna gider. Bulkhead: 4 KEZ HIT (4 eşzamanlı thread), 2'si `accepted`, 2'si
`BulkheadFullException`. Timeout: 1 KEZ HIT, `TimeoutException` fırlar (500ms servis, 100ms
limit). Fallback: catch bloğu HIT olur, `result="DEFAULT_CACHED_RESPONSE"` atanır.

**WHAT TO INSPECT:** her lab'ın kendi `permitted`/`accepted`/`outcome`/`fallbackUsed` alanı.

**WHAT THIS PROVES:** Her Resilience4j pattern'i AYNI problemi (yavaş/başarısız bağımlılık)
FARKLI stratejilerle ele alır.

---

## 41. Security (Authentication vs Authorization, JWT)

**POSTMAN FOLDER:** `18 Security / JWT`

**REQUESTS:** `GET /api/labs/security/protected` (token'sız → 401 / USER token → 200), `GET /api/labs/security/admin-only` (USER token → 403 / ADMIN token → 200)

**BREAKPOINTS:**
- `com.interviewlab.security.JwtAuthenticationFilter.doFilterInternal()`
- `com.interviewlab.web.lab.security.SecurityLabController.protectedEndpoint()`
- `com.interviewlab.web.lab.security.SecurityLabController.adminOnly()`

**HIT/MISS EXPECTATION:** `JwtAuthenticationFilter.doFilterInternal()` HER istekte (token'lı/token'sız
fark etmez) 1 KEZ HIT olur — Spring Security filter chain'in HER request için çalıştığını
kanıtlar. Token GEÇERSİZ/YOK ise `SecurityContextHolder`'a authentication SET EDİLMEZ, bu
yüzden controller metoduna HİÇ ULAŞILMAZ (401 filter seviyesinde üretilir). USER token'la
`/admin-only`'ye gidersen, controller metoduna YİNE ULAŞILMAZ — `hasRole("ADMIN")` kontrolü
(bkz. `SecurityConfig.filterChain()`) 403'ü FILTER CHAIN'DE üretir.

**WHAT TO INSPECT:** `SecurityContextHolder.getContext().getAuthentication()` (Evaluate
Expression) — token'sızken `null` ya da anonim; ADMIN token'layken `authorities` içinde
`ROLE_ADMIN`.

**WHAT THIS PROVES:** 401 (kimliksiz) ile 403 (kimlik biliniyor ama yetkisiz) GERÇEKTEN farklı
filter/controller katmanlarında üretilir.

---

## 42. Design Pattern: Strategy + Factory

**POSTMAN FOLDER:** `16 Design Patterns (Strategy)`, `22 Design Patterns (Factory-Adapter-Observer-Builder-Proxy)`

**BAD REQUEST:** `POST /api/labs/patterns/strategy/bad?paymentType=CREDIT_CARD`
**GOOD REQUEST:** `POST /api/labs/patterns/strategy/good?paymentType=CREDIT_CARD`, `POST /api/labs/patterns/factory/resolve?paymentType=EXTERNAL_PROVIDER`

**BAD BREAKPOINTS:** `com.interviewlab.patterns.strategy.bad.IfElsePaymentProcessor.process()`
**GOOD BREAKPOINTS:** `com.interviewlab.patterns.factory.PaymentStrategyFactory.getStrategy()` (satır: `strategiesByType.get(paymentType)`)

**HIT/MISS EXPECTATION:** BAD'de if/else zincirinin İÇİNDEN `CREDIT_CARD` dalına GİRİLDİĞİNİ
Step Into ile gör. GOOD'da `strategiesByType` Map'inin `"EXTERNAL_PROVIDER"` anahtarının
`ExternalPaymentProviderAdapter`'a, `"CREDIT_CARD"`'ın `CreditCardPaymentStrategy`'ye eşlendiğini gör.

**WHAT TO INSPECT:** `implementationUsed` alanı.

**WHAT THIS PROVES:** Factory, yeni bir `@Component PaymentStrategy` eklendiğinde HİÇBİR
DEĞİŞİKLİK gerektirmez (Adapter dahil).

---

## 43. Design Pattern: Adapter

**POSTMAN FOLDER:** `22 Design Patterns (Factory-Adapter-Observer-Builder-Proxy)`

**BAD REQUEST:** `POST /api/labs/patterns/adapter/bad?amount=50`
**GOOD REQUEST:** `POST /api/labs/patterns/adapter/good?amount=50`

**BAD BREAKPOINTS:** `com.interviewlab.web.lab.patterns.DesignPatternsLabController.adapterBad()` (satır: `long amountInCents = Math.round(amount * 100);`)
**GOOD BREAKPOINTS:** `com.interviewlab.patterns.adapter.ExternalPaymentProviderAdapter.pay()`

**HIT/MISS EXPECTATION:** BAD'de dönüşüm mantığı CONTROLLER İÇİNDE HIT olur; GOOD'da AYNI
dönüşüm `ExternalPaymentProviderAdapter.pay()` İÇİNDE, TEK bir yerde HIT olur.

**WHAT TO INSPECT:** `rawStatusCode` (BAD'de sızar) vs temiz `PaymentStrategy` sonucu (GOOD).

**WHAT THIS PROVES:** Adapter, iki uyumsuz arayüz arasındaki çeviriyi TEK bir sınıfa hapseder.

---

## Ek: Design Pattern Observer / Builder / Proxy, Exceptions (aynı yöntemle, TOPIC_MATRIX'te ayrı satırlar)

Bu 6 lab için TAM manifest zaten mevcuttu (önceki fazda eklendi) — format bu dosyanın
başındaki EXACT şablona göre GÜNCELLENMEDİ, ama içerik doğru ve günceldir:

- **Design Pattern: Observer** — `POST /api/labs/patterns/observer/place-order` → `com.interviewlab.patterns.observer.OrderPlacementService.placeOrder()` → `InventoryReservationListener.onOrderPlaced()` + `EmailNotificationListener.onOrderPlaced()` (TEK publish, İKİ bağımsız `@EventListener`, HER İKİSİ DE HIT olur).
- **Design Pattern: Builder** — `POST /api/labs/patterns/builder/build-invalid` → `com.interviewlab.patterns.builder.OrderRequest.Builder.build()` (satır: `if (itemIds.isEmpty())`) → `IllegalStateException` FIRLAR, nesne HİÇ oluşmaz.
- **Design Pattern: Proxy** — `POST /api/labs/patterns/proxy/with-logging` → `com.interviewlab.patterns.proxy.GreeterProxyFactory`'nin `InvocationHandler` lambda'sı (satır: `logSink.accept("before:" + method.getName());`) HIT olur; `without-logging`'de HİÇ HIT OLMAZ (proxy yok).
- **Exceptions: swallowed** — `com.interviewlab.exception.bad.SwallowingExceptionService.chargeCardSilently()` — catch bloğu HIT olur ama İÇİNDE HİÇBİR ŞEY YAPILMAZ (boş/log-only).
- **Exceptions: lossy-rethrow** — `com.interviewlab.exception.bad.LossyRethrowService.chargeCardLosingCause()` — `new RuntimeException(e.getMessage())` satırı HIT olur, `cause` PARAMETRESİ VERİLMEZ.
- **Exceptions: good (wrapped)** — `com.interviewlab.exception.good.WrappingExceptionService.chargeCard()` — `new RuntimeException(msg, e)` satırı HIT olur, orijinal `e` `cause` olarak KORUNUR.

Detaylı satırlar için `docs/DEBUGGER_VERIFICATION.md`'deki tabloya bakın.

---

## Kategori B (LabRunner) — main() ile Run/Debug

Kategori B'nin 14 sınıfının HEPSİ `public final class` içinde standart
`public static void main(String[] args)` imzasına sahiptir (bu oturumda TEK TEK grep ile
doğrulandı — bkz. `docs/DEBUGGER_VERIFICATION.md` Kategori B bölümü). IntelliJ, bu imzayı
OTOMATİK olarak tanır ve gutter'da yeşil Run/Debug ikonu gösterir — bu, IDE'nin standart,
yapılandırma gerektirmeyen davranışıdır. Her sınıfın kendi WHY/BREAKPOINT/TRY rehberi
KENDİ KAYNAK KODUNDA (banner/fact/section çıktısı olarak) ve `docs/TOPIC_MATRIX.md`'de
listelenmiştir.
