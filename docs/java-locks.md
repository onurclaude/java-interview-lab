# Java Concurrency Temelleri: Thread'ler, Race Condition'lar, Lock'lar, volatile, ThreadLocal ve Patolojiler

Kod: `com.interviewlab.concurrency.*`

**Birincil öğrenme arayüzü — Postman + IntelliJ debugger:**
```http
POST /api/labs/concurrency/counter/{reset,bad,good}
POST /api/labs/concurrency/synchronized/{bad,good}
POST /api/labs/concurrency/reentrant-lock/{bad,good}
POST /api/labs/concurrency/read-write-lock/demo
POST /api/labs/concurrency/stamped-lock/demo
POST /api/labs/concurrency/volatile/{misconception-check,correct-usage}
POST /api/labs/threadlocal/{reset,bad,good}
```
Thread yaşam döngüsü için Kategori B runner'ı `com.interviewlab.labrunner.ThreadLifecycleLabRunner`
(IntelliJ'de doğrudan Run/Debug — bkz. `docs/DEBUGGER_LABS.md` #23). Tam breakpoint sırası ve
beklenen değişkenler için `docs/DEBUGGER_LABS.md`'nin ilgili bölümlerine bakın.
`ThreadLifecycleTest`, `ThreadCreationBoundsTest`, `RaceConditionTest`, `SynchronizationTest`,
`VolatileAndAtomicTest`, `ThreadLocalTest`, `DeadlockLivelockStarvationTest` — AYNI davranışın
otomatik regresyon kanıtıdır — ikincildir, birincil değil.

## Thread yaşam döngüsü

`ThreadLifecycleTest`, gerçek bir thread'i her `Thread.State`'de parklar ve doğrudan
gözlemler: **NEW** (oluşturuldu, başlatılmadı) → **RUNNABLE** (busy-loop yapıyor) →
**TIMED_WAITING** (`Thread.sleep`) / **WAITING** (timeout'suz `CountDownLatch.await()`) /
**BLOCKED** (başka bir thread'in tuttuğu bir `synchronized` bloğuna girmeyi bekliyor) →
**TERMINATED** (çalışma tamamlandı).

## Kontrolsüz thread oluşturma

```java
// com.interviewlab.concurrency.thread.bad.UncontrolledThreadCreationService
new Thread(task).start(); // once per call, no bound
```

**Neden yanlış:** her platform thread'i gerçek bir OS thread'i ve bir stack'e (genellikle
~512KB-1MB) mal olur. Burada hiçbir şey, concurrent thread sayısını CPU'nun veya downstream
kaynakların kaldırabileceği seviyeyle sınırlamaz.
`ThreadCreationBoundsTest.shouldLetConcurrentThreadCountGrowUnboundedWithRawThreads()`: aynı
anda 200 task submit edilir, peak concurrency = 200 - tüm burst, sınırsız. Çözüm: bounded
bir pool'a submit et (`BoundedTaskSubmissionService`) - burst ne olursa olsun peak
concurrency pool boyutunda kalır
(`shouldCapConcurrentThreadCountAtPoolSizeWithBoundedExecutor`). Tam production
`ThreadPoolExecutor` ayarları için docs/executor-service.md'ye bakın.

## Race condition

```java
// com.interviewlab.concurrency.race.bad.UnsafeBankAccount
if (balance >= amount) { balance -= amount; }
```

Üç ayrı işlem (oku, karşılaştır, yaz), bir grup olarak atomik değil. İki thread, ikisi de
geri yazmadan önce kontrolü geçebilir - bir lost update.
`RaceConditionTest.shouldLoseUpdatesWithUnsynchronizedWithdraw()`: birçok concurrent
withdrawal altında tam olarak sıfırlanması gereken bir hesap sıfırlanmaz. Tüm metodun
etrafına `synchronized` koymak (`SynchronizedBankAccount`) sorunu tamamen çözer
(`shouldPreventLostUpdatesWithSynchronizedWithdraw`).

## synchronized: metod ile blok

Bir `synchronized` instance metodu ile tüm gövdesinin etrafındaki
`synchronized (this) { ... }`, KANITLANABİLİR şekilde aynı lock'tır -
`SynchronizationTest.shouldTreatSynchronizedMethodAndBlockAsTheSameMonitor()`, birinin
diğerini bloke ettiğini gösterir. Gerçek kodda `this` yerine private bir lock nesnesi tercih
edin, böylece nesnenize bir referansı olan external kod, kazara (ya da kötü niyetle) aynı
monitor üzerinde senkronize olup kontrol edemediğiniz contention'ı genişletemez.

## ReentrantLock: try/finally zorunluluğu

```java
// com.interviewlab.concurrency.synchronization.bad.LockWithoutFinallyService
lock.lock();
if (shouldThrow) { throw new IllegalStateException(...); } // unlock() below never runs
count++;
lock.unlock();
```

`synchronized`'ın aksine, bir `ReentrantLock`, JVM tarafından ASLA otomatik olarak serbest
bırakılmaz. `lock()` ile `unlock()` arasındaki bir exception, onu sonsuza dek tutulu bırakır.
`shouldLeaveLockPermanentlyHeldWhenExceptionSkipsUnlock()`, ikinci bir caller'ın
`tryLock()`'ının sonrasında kalıcı olarak başarısız olduğunu kanıtlar. Her zaman:
`lock.lock(); try { ... } finally { lock.unlock(); }`.

**`synchronized` yerine neden `ReentrantLock`'a başvurulur?** `tryLock()` (timeout'lu veya
timeout'suz, sonsuza kadar bloklamak yerine geri çekilmek için), `lockInterruptibly()`
(iptal edilebilir bekleme), ve bir fairness policy'si (`new ReentrantLock(true)`) -
intrinsic monitor'ün sahip olmadığı yetenekler. Yaygın durum için sade `synchronized`
kullanın; daha basit ve biraz daha ucuzdur.

## ReadWriteLock / StampedLock

`ReentrantReadWriteLock`, herhangi bir sayıda reader'ın read lock'u eşzamanlı olarak
tutmasına izin verir; sadece bir writer'ın exclusivity'ye ihtiyacı vardır -
`shouldAllowMultipleConcurrentReadersButExcludeThemDuringWrite()`, 5 reader'ın gerçekten
aynı anda çalıştığını kanıtlar. `StampedLock`, bunun üzerine lock-free, üçüncü bir
"optimistic read" modu ekler: field'ları oku, sonra arada bir yazma olup olmadığını
`validate(stamp)` ile kontrol et; sadece olduysa gerçek bir read lock'a geri dön - daha ucuz
okumalar, reentrant olmama ve `Condition`'ları desteklememe pahasına.

## volatile: atomicity değil, visibility

```java
// com.interviewlab.concurrency.volatiletopic.bad.VolatileCounter
private volatile int counter;
public void increment() { counter++; } // read-modify-write, still not atomic
```

`volatile`, her thread'in en son yazmayı gördüğünü garanti eder (ve onun etrafındaki
reordering'i engeller) - bileşik bir read-modify-write'ın atomik olması hakkında HİÇBİR ŞEY
söylemez. `shouldLoseIncrementOperationsWithVolatileCounter()`: concurrent increment'ler yine
de kayboluyor. `volatile` için doğru kullanım senaryosu, bir thread tarafından yazılıp
diğerleri tarafından sıradan bir atama olarak okunan tek bir flag/değerdir - bileşik bir
işlem söz konusu değildir - `ShutdownFlagWorker`'ın stop flag'i
(`shouldMakeShutdownFlagVisibleAcrossThreads`).

## Atomic'ler (CAS) — tam yazı için docs/atomic.md'ye bakın

`AtomicInteger.incrementAndGet()`, yukarıdaki counter'ı "volatile artı bir şey" ile değil,
Compare-And-Set aracılığıyla düzeltir (`shouldProduceCorrectCountWithAtomicInteger`).

## ThreadLocal — pooled-thread sızıntısı

```java
// com.interviewlab.concurrency.threadlocal.bad.LeakyCorrelationIdService
CORRELATION_ID.set(id); // no remove()
```

Bir `ThreadLocal` değeri, onu ayarlayan mantıksal task kadar değil, `Thread` nesnesi kadar
yaşar. Pooled bir thread'de (single-thread executor, servlet container), bir sonraki
ilgisiz task, öncekinin değerini devralır -
`shouldLeakThreadLocalStateWhenRemoveIsNotCalled()`, bunu, arka arkaya iki ilgisiz task
çalıştıran bir single-thread executor ile kanıtlar. Çözüm: `try { set(...); ... } finally { remove(); }`
(`CleanCorrelationIdService`, `shouldNotLeakThreadLocalStateWhenRemoveIsCalledInFinally`).

## Deadlock / Livelock / Starvation / Race Condition — yan yana

| | Tanım | Buradaki hatalı örnek | Tespit | Önleme |
|---|---|---|---|---|
| **Race condition** | Sonuç, talihsiz thread interleaving'ine bağlıdır | `UnsafeBankAccount` | Paylaşılan mutable state için code review; load testing altında ortaya çıkar | Tüm read-modify-write'ı senkronize et |
| **Deadlock** | Thread'ler başkalarının ihtiyaç duyduğunu tutar ve asla bırakmaz, bir döngü oluşur | `InconsistentLockOrderTransferService` (`synchronized`, JVM içi) - ayrıca docs/pessimistic-locking.md'deki DB-seviyesi versiyonuna bakın | `ThreadMXBean.findDeadlockedThreads()` (JVM) / DB'nin kendi detector'ı | Tutarlı, global lock sıralaması |
| **Livelock** | Thread'ler birbirine tepki vererek meşgul kalır, asla ilerlemez | `LivelockDemo` kibar worker'lar | Thread'ler sonsuza dek `RUNNABLE` kalır, CPU yüksek, throughput yok; `findDeadlockedThreads()` tarafından RAPORLANMAZ | Simetriyi boz (rastgele backoff) |
| **Starvation** | Bir thread, meşru şekilde beklediği bir kaynaktan tekrar tekrar mahrum bırakılır | Fair olmayan `ReentrantLock` barging'e izin verir | Doğrudan bir API yok; yük altında bir thread'in bekleme süresinin sınırsız artmasından çıkarılır | Fair lock (FIFO), veya bounded kaynak tahsisi |

**Deadlock**: `shouldDeadlockWithInconsistentLockOrder()`, T1'i (önce A sonra B kilitler) ve
T2'yi (önce B sonra A kilitler) eşzamanlı başlatır, ikisi de `synchronized`, ikisi de race
penceresini genişletmek için yapay bir gecikme kullanır, ve `ThreadMXBean.findDeadlockedThreads()`
üzerinden gerçek bir JVM deadlock'unu doğrular. Çözüm:
`DeterministicLockOrderTransferService`, her zaman önce düşük id'yi kilitler
(`shouldNotDeadlockWithDeterministicLockOrder`).

**Livelock**: iki "kibar" worker, önce kendi lock'unu alır, sonra diğerininkini `tryLock()`
yapar - başarısız olursa hemen bırakıp tekrar dener. `CyclicBarrier`'larla lockstep'e
zorlandığında, ikisi de HER round'da başarısız olur
(`shouldWasteEveryForcedLockstepRoundToLivelock`, sadece kötü şans değil, gerçek sıfır
ilerlemeyi kanıtlar). Küçük, rastgele bir backoff simetriyi bozar
(`shouldEventuallySucceedOnceRandomBackoffBreaksTheLockstep`).

**Starvation**: flaky bir wall-clock yarışı yerine, `StarvationDemo`, özelliği doğrudan
`ReentrantLock`'un kendi fairness kontratı üzerinden kanıtlar: **fair** bir lock, her
seferinde, zaten kuyrukta olan bir thread'e daha sonra gelen bir thread'den önce
deterministik olarak hizmet verir
(`shouldAlwaysServeAlreadyQueuedThreadBeforeNewArrivalUnderFairLock`); **fair olmayan** bir
lock böyle bir garanti vermez (`nonFairLockGivesNoOrderingGuaranteeUnlikeFairLock`) - ki bu
tam olarak fair modun ortadan kaldırmak için var olduğu starvation riskidir.

## Sık Sorulan Mülakat Soruları

- **Q:** `volatile int x; x++;` thread-safe midir? — Hayır; visibility sağlar, atomicity
  sağlamaz.
- **Q:** synchronized method ile synchronized(this) bloğu aynı mı? — Evet, birebir aynı
  monitor.
- **Q:** Deadlock nasıl tespit edilir? — JVM içi: `ThreadMXBean.findDeadlockedThreads()`
  veya thread dump. DB seviyesinde: veritabanının kendi deadlock detector'ı.
- **Q:** Livelock deadlock'tan nasıl ayrılır? — Livelock'ta thread'ler RUNNABLE kalır,
  BLOCKED olmazlar; `findDeadlockedThreads()` livelock'u yakalamaz.
- **Q:** ThreadLocal ne zaman leak eder? — Thread pool'da `remove()` çağrılmazsa, bir
  sonraki task eski context'i devralır.

## 30 Saniyelik Mülakat Cevabı

"Race condition'ı bir bank account senaryosunda ürettim: check-then-act pattern'i
synchronized olmadan kullanınca, hesap tam olarak sıfırlanması gereken yerde sıfırlanmadı.
volatile'ın atomicity sağlamadığını da ayrı bir counter testiyle kanıtladım - counter++ hala
increment kaybediyordu, AtomicInteger'a geçince kaybolmadı. Deadlock için de gerçek bir JVM
deadlock'unu ThreadMXBean ile tespit ettim: iki thread ters sırada iki lock almaya çalışınca
gerçekten sonsuza kadar bloklandılar, tespit ettikten sonra deterministic lock ordering ile
düzelttim."

## Takip Soruları

- `synchronized` bloğu içinde exception fırlarsa lock otomatik serbest kalır mı? (Evet,
  `ReentrantLock`'tan farklı olarak.)
- `Thread.interrupt()` bir `BLOCKED` thread'i nasıl etkiler? (Etkilemez - sadece
  `WAITING`/`TIMED_WAITING`/interruptible metodları keser.)
- `LongAdder` `AtomicLong`'dan ne zaman daha iyidir?
