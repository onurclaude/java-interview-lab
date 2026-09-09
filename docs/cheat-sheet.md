# Cheat Sheet

Konu başına gruplanmış, her satırda bir gerçek. Her satırın bu repo'da eşleşen bir testi var
- "neden"i unutursan, yanındaki doc/test tüm hikayeyi anlatır.

## Persistence context (docs/persistence-context.md)
- transient → managed → detached → removed; dirty checking sadece managed entity'lerde çalışır.
- `save/update/delete` her zaman 3 SQL statement anlamına gelmez - flush zamanlamasına ve ID generation stratejisine bağlıdır (SEQUENCE INSERT'i erteleyebilir; IDENTITY erteleyemez).
- `flush()` ≠ `save()`: flush hemen bir DB round trip'i zorlar; save sadece planlar.
- Bir döngü içinde `saveAndFlush()` = tek bir batch'lenmiş commit-time flush yerine N round trip.
- DETACHED bir entity'yi değiştirmek (`save()` sonrası, sarmalayan bir `@Transactional` olmadan) sessizce kaybolur.

## Optimistic locking (docs/optimistic-locking.md)
- `@Version`, WHERE'e `AND version=?` ekler, SET'e `version=version+1` ekler; 0 satır etkilendiğinde → `OptimisticLockException` → Spring'in `ObjectOptimisticLockingFailureException`'ı.
- Hiçbir zaman DB lock'u alınmaz.
- `@Version` yoksa hiçbir koruma yoktur, sadece sessiz bir lost update.
- Exception'ı asla yutma; asla sınırsız retry yapma; asla idempotent olmayan bir side effect'i retry etme; client'ın gönderdiği version'a asla güvenme.

## Pessimistic locking (docs/pessimistic-locking.md)
- `PESSIMISTIC_WRITE` → Postgres'te gerçek bir `SELECT ... FOR UPDATE`; ikinci transaction fiziksel olarak bloklanır.
- Aktif bir transaction gerektirir (aksi halde `TransactionRequiredException`).
- Lock, içindeki herhangi bir yavaş external çağrı dahil, TÜM transaction boyunca tutulur.
- İki satır arasında tutarsız lock sırası → gerçek DB seviyesinde deadlock (Postgres tespit eder ve bir tarafı abort eder). Çözüm: deterministik lock sıralaması.

## Isolation level'ları (docs/isolation.md)
- READ_COMMITTED: dirty read engellenir, non-repeatable read ve phantom read mümkündür.
- Postgres'te REPEATABLE_READ: full snapshot isolation sayesinde phantom read'leri de engeller (SQL standardından daha güçlü).
- READ_UNCOMMITTED Postgres'te gerçekten var olmaz - sessizce READ_COMMITTED'e yükseltilir.
- REPEATABLE_READ/SERIALIZABLE, kaybeden commit'te bir serialization failure (40001) aracılığıyla `@Version` olmadan da lost update'leri engelleyebilir.

## Propagation (docs/propagation.md)
- REQUIRED: katıl veya oluştur. REQUIRES_NEW: askıya al + bağımsız transaction. NESTED: teoride aynı connection, JDBC savepoint.
- NESTED + JPA gerçeği: pratikte hiç çalışmaz - `HibernateJpaDialect`, `SavepointManager` uygulayan bir transaction-data döndürmez, bu yüzden her deneme `NestedTransactionNotSupportedException` ile başarısız olur.
- `JpaTransactionManager.nestedTransactionAllowed` varsayılan olarak `false`'tur, ama `true` yapmak bile yeterli değildir - NESTED sadece saf JDBC (`DataSourceTransactionManager`) ile gerçekten çalışır.

## Transaction proxy / self-invocation (docs/transactions.md)
- `@Transactional`, `@Async` ve her `@Aspect`, Spring AOP proxy'sidir.
- Aynı bean içinden yapılan `this.method()` çağrısı asla proxy'den geçmez - advice'lerin hiçbiri çalışmaz.
- Çözüm: farklı bir bean'e inject edilmiş bir referans üzerinden çağır.
- Varsayılan rollback kuralı: unchecked → rollback; checked → commit, `rollbackFor` belirtilmedikçe.

## Bean scope'ları (docs/bean-scopes.md)
- Spring singleton = her `ApplicationContext` için bir tane, container tarafından zorlanır - GoF pattern'i değildir.
- Singleton üzerinde mutable instance field'ları = paylaşılan state = concurrent request'ler altında data race.
- Sıradan bir constructor field'ı ile inject edilen prototype = SADECE BİR KEZ inject edilir, sonrasında hep aynı instance.
- Çözüm: `ObjectProvider.getObject()` (explicit) veya bir scoped proxy (`proxyMode = TARGET_CLASS`, şeffaf).
- Request/session scope: scope içinde aynı instance, scope instance'ları arasında farklı; application scope tek-context bir uygulamada singleton'a yakındır.

## Java concurrency (docs/java-locks.md, docs/atomic.md)
- İstek başına `new Thread().start()` = sınırsız thread sayısı; bounded bir pool kullan.
- Senkronizasyon olmadan paylaşılan state üzerinde check-then-act = lost update.
- `synchronized` metod ≡ tüm gövdeyi saran `synchronized(this)`.
- `ReentrantLock`, `try { } finally { unlock(); }` gerektirir - `synchronized` gibi otomatik olarak asla serbest bırakılmaz.
- `ReadWriteLock`: çok sayıda concurrent reader, tek bir exclusive writer. `StampedLock`: lock-free bir optimistic-read modu ekler.
- `volatile` = sadece visibility, bileşik işlemler için asla atomicity sağlamaz (`x++` yine race eder).
- `AtomicInteger` = CAS tabanlı, tek-değişkenli bileşik güncellemeler için gerçekten atomik.
- ABA problemi: sıradan CAS A→B→A'yı göremez; `AtomicStampedReference`, bir version stamp aracılığıyla görebilir.
- `finally` içinde `remove()` çağrılmayan bir pooled thread üzerindeki `ThreadLocal`, bir sonraki task'a sızar.
- Deadlock: circular wait, thread'ler BLOCKED, `ThreadMXBean`/DB deadlock detector ile tespit edilebilir. Çözüm: tutarlı lock sırası.
- Livelock: thread'ler RUNNABLE kalır, birbirine tepki verir, asla ilerlemez. Çözüm: rastgele backoff.
- Starvation: fair bir lock FIFO hizmeti garanti eder; fair olmayan bir lock etmez (barging'e izin verir).

## Executor'lar (docs/executor-service.md)
- `Executors.newFixedThreadPool`'un SINIRSIZ bir queue'su vardır - sınırlı thread, sınırsız bekleyen iş.
- Production için bounded bir queue + explicit bir `RejectedExecutionHandler` ile gerçek bir `ThreadPoolExecutor` inşa et.
- `AbortPolicy` exception fırlatır; `CallerRunsPolicy` task'ı caller'ın thread'inde çalıştırır (yerleşik backpressure).
- `shutdown()`, kuyruktaki işin bitmesine izin verir; `shutdownNow()` kuyruktaki işi iptal eder ve çalışan işi interrupt eder.

## CompletableFuture / @Async (docs/completable-future.md, docs/async.md)
- Art arda `supplyAsync(...).get()` = tamamen serileştirilmiş, sıfır paralellik.
- Executor argümanı verilmezse = paylaşılan `ForkJoinPool.commonPool()` - bloklayan iş için asla kullanma.
- Future döndüren bir fonksiyonla `thenApply` = iç içe `CompletableFuture<CompletableFuture<T>>`; `thenCompose` bunu düzleştirir.
- `exceptionally` recover eder; `handle` her iki yolda da çalışır ve recover edebilir; `whenComplete` sadece gözlemler, sonucu asla değiştirmez.
- `@Async` void metodun exception'ı → sadece `AsyncUncaughtExceptionHandler`. `@Async` `CompletableFuture<T>` metodun exception'ı → döndürülen future, normal şekilde incelenebilir.

## AOP (docs/aop.md)
- Before VE after gerektiren her şey için (timing, sonucu sarmalamak) `@Around` + `ProceedingJoinPoint.proceed()`.
- Pointcut, annotation'ın KENDİSİ olabilir: `@annotation(MyAnnotation)`.
- Diğer her Spring proxy'siyle aynı self-invocation kör noktasına tabidir.

## Exception'lar (docs/exceptions.md)
- Bu projenin business hiyerarşisi kasıtlı olarak unchecked'tir: `BusinessException → PaymentException → InsufficientBalanceException`.
- Asla sessizce `catch (Exception e) {}` yapma. Asla orijinalini `cause` olarak geçirmeden yeniden fırlatma.
- `@RestControllerAdvice` + `@ExceptionHandler`, en-spesifikten-başlayarak eşleşir.

## Design pattern'lar (docs/design-patterns.md)
- Strategy+Factory, `if/else` zincirlerinin yerini alır; Template Method bir algoritmanın şeklini sabitlerken tek bir adımın değişmesine izin verir; Observer, Spring event'leri aracılığıyla decouple eder; Decorator, subclass patlamasına gerek kalmadan ayarlamaları kompoze eder; Adapter, üçüncü parti bir interface uyuşmazlığını izole eder; Facade, çoklu-subsystem orkestrasyonunu basitleştirir; Builder, çok-opsiyonel-alanlı construction'ı çözer (record ise tüm-alanlar-zorunlu durumunu çözer); GoF Singleton ≠ Spring singleton; Proxy, `@Transactional`/`@Async`/AOP'yi çalıştıran şeyin ta kendisidir; Chain of Responsibility, monolitik bir validator'ın yerini bağımsız, kompoze edilebilir kurallarla değiştirir.

## Collection'lar (bkz. testler: `CollectionsConcurrencyTest`)
- Concurrent modification altında sıradan `HashMap` → `ConcurrentModificationException` veya daha kötüsü.
- `ConcurrentHashMap`, tek-operasyon güvenliğini çözer, bileşik `containsKey`+`put` race'lerini DEĞİL - `computeIfAbsent` kullan.
- `CopyOnWriteArrayList`: snapshot iterator'lar, CME yok, pahalı yazmalar - okuma-ağırlıklı, yazma-nadir (listener list'leri) için iyidir.

## Immutability, equals/hashCode, Optional, Stream'ler
- Internal mutable bir field'ı döndüren `getX()` = gerçek bir encapsulation yoktur; `Collections.unmodifiableList` bir VIEW'dır (backing list yine değişebilir), gerçek bir kopya (`List.copyOf`) değildir.
- hashCode olmadan equals → HashSet "duplicate" eşit elemanlar barındırabilir. MUTABLE bir field'dan türetilen hashCode → o field insertion'dan sonra değiştiğinde HashSet bir elemanı kaybeder (klasik JPA id tuzağı - çözüm: stable/sabit hashCode).
- Kontrol olmadan `Optional.get()`, süslenmiş bir NPE'den ibarettir. `orElse()` EAGER'dır (her zaman değerlendirilir); `orElseGet()` LAZY'dir.
- `parallelStream().forEach()`'in paylaşılan sıradan bir `ArrayList`'e yazması eleman kaybına yol açabilir - `collect()` kullan. `flatMap`, `List<List<T>>` iç içeliğinden kaçınır.

## N+1 / lazy / fetch stratejileri (docs/n-plus-one.md)
- `@OneToMany` varsayılan olarak LAZY'dir; `@ManyToOne`/`@OneToOne` varsayılan olarak EAGER'dır.
- Bir listeyi yükleyip sonra döngü içinde her elemanın lazy collection'ına dokunmak = 1+N sorgu.
- Fetch join / `@EntityGraph` = tek sorgu, gerçek entity'ler. DTO projection = tek sorgu, hiç entity yok - entity'e ihtiyacın olmadığında en ucuzu.
- EAGER bedava bir çözüm değildir - her yüklemede, koşulsuz, sonsuza dek, her yerde yükler.
- `LazyInitializationException` = session/transaction kapandıktan sonra lazy erişim. Open Session in View, bunu tüm request boyunca tutulan bir connection karşılığında feda eder.
