# Mülakat Senaryoları

Bir kıdemli Java/Spring backend mülakatı, birbirine bağlı bir senaryolar dizisi olarak.
Her thread, gerçek bir mülakatçının önceki cevabınız üzerine inşa ettiği tarzda tek bir soru
hattını takip eder. Cevaplar, bu repodaki testlerin size verdiği şekilde yazılmıştır: spesifik,
gerçekten çalıştırdığınız bir şeye dayalı, ders kitabı tanımları değil. Çapraz referanslar, her
cevabı destekleyen kod/doc'lara işaret eder.

---

## Senaryo 1 — Persistence Context ve Flush

**Mülakatçı:** Aynı transaction içinde bir entity'yi kaydedip, güncelleyip, silebilir
misin?

**Aday:** Evet, ve daha ilginci: eğer araya bir flush girmezse, hiçbir SQL bile
gitmeyebilir. `Customer` entity'm `SEQUENCE` id generation kullanıyor, yani insert
ertelenebiliyor. Save → rename → delete yaptığımda, hiç flush çağırmadan, Hibernate pending
insert'i iptal etti ve sıfır SQL statement gönderdi.

**Mülakatçı:** Peki save() çağrıldığında SQL hemen gider mi?

**Aday:** Bağlı. `IDENTITY` generation stratejisiyle evet, çünkü id'yi hemen DB'den
almak zorunda. `SEQUENCE` ile hayır, ertelenebilir - benim entity'mde de öyle.

**Mülakatçı:** Flush ne zaman olur?

**Aday:** Üç durumda: `flush()` explicit çağrısında, transaction commit'inde otomatik,
ya da (default `FlushModeType.AUTO` ile) persistence context'i etkileyecek bir JPQL sorgusu
çalıştırılmadan hemen önce.

**Mülakatçı:** İki transaction aynı row'u değiştirirse ne olur?

**Aday:** Bu, `@Version` olup olmamasına bağlı - buraya optimistic locking giriyor
(bkz. Senaryo 3).

*(bkz. docs/persistence-context.md)*

---

## Senaryo 2 — Dirty Checking ve Detached Entity'ler

**Mülakatçı:** `save()` çağırıp entity'yi sonradan değiştirirsem ne olur?

**Aday:** Duruma göre değişir. Eğer bu tek bir `@Transactional` metod içindeyse, entity
hala managed'dır, dirty checking commit'te devreye girer ve değişikliği yazar - explicit
`save()` çağrısına bile gerek yok.

**Mülakatçı:** Peki hiç `@Transactional` yoksa?

**Aday:** Bunu bizzat kırdım: `save()` kendi transaction'ını açıp kapatıyor, method
return ettiğinde entity DETACHED oluyor. Sonrasında yaptığım `changeName()` hiçbir işe
yaramıyor - dirty checking sadece managed entity'lerde çalışıyor, ben de reload edince eski
ismi gördüm.

**Mülakatçı:** Bunu nasıl fark ettin, bir exception mı aldın?

**Aday:** Hayır, en tehlikeli kısmı da bu - hiçbir exception yok, sessizce hiçbir şey
olmuyor.

*(bkz. docs/persistence-context.md, `MutatingDetachedEntityService`)*

---

## Senaryo 3 — Optimistic Locking

**Mülakatçı:** Optimistic locking nedir?

**Aday:** Bir satırı okuyup güncellerken, DB'de hiçbir lock almadan, `@Version`
sütunuyla "bu satır ben okuduğumdan beri değişti mi?" kontrolü yapan bir mekanizma.

**Mülakatçı:** `@Version` nasıl çalışır?

**Aday:** Hibernate her UPDATE'in WHERE'ine `AND version = ?` ekliyor, SET'ine
`version = version + 1` ekliyor. Ben bunu gerçek SQL logunda gördüm.

**Mülakatçı:** Database row'u gerçekten locklar mı?

**Aday:** Hayır, hiçbir zaman - bu pessimistic locking'den temel farkı.

**Mülakatçı:** `OptimisticLockException` ne zaman oluşur?

**Aday:** WHERE koşulu 0 satır eşleştirdiğinde. Ben iki transaction'ı senkronize ederek
bunu tetikledim: T2 version=0'ı okudu, T1 commit edip version'ı 1'e çıkardı, T2 hala
version=0 ile update denedi ve `ObjectOptimisticLockingFailureException` aldı.

**Mülakatçı:** Retry yapılmalı mı?

**Aday:** Sınırlı sayıda ve backoff ile, ama sadece operasyon idempotent ise. Bir kart
ücretlendirmesi gibi geri alınamaz bir side effect'i kör kör retry etmek çift ücretlendirmeye
yol açabilir.

**Mülakatçı:** @Version olmadan da "optimistic locking'im var" diyebilir miyim?

**Aday:** Hayır - bunu da bizzat gösterdim. Version alanı olmadan aynı senaryoda hiç
exception almadım, ikinci transaction birincinin değişikliğini sessizce ezdi - klasik lost
update.

*(bkz. docs/optimistic-locking.md)*

---

## Senaryo 4 — Pessimistic Locking ve Deadlock

**Mülakatçı:** Pessimistic locking optimistic'ten ne zaman daha iyidir?

**Aday:** Contention yüksekse. Hot bir row'da optimistic locking'i test ettim, çoğu
attempt conflict alıp retry ediyordu - throughput düşüyordu. Pessimistic'te herkes sırayla
bekliyor, retry gerekmiyor.

**Mülakatçı:** `FOR UPDATE` gerçekten ne yapıyor?

**Aday:** Gerçek bir DB-seviyesi row lock alıyor. Bunu `SqlStatementRecorder` ile
ürettiğim SQL'de gördüm - "for update" kelimesi gerçekten SQL'in içinde.

**Mülakatçı:** İki transaction aynı iki satırı ters sırada kilitlerse ne olur?

**Aday:** Deadlock. Bunu da gerçek Postgres'te ürettim - iki transaction A ve B
satırlarını ters sırada kilitlemeye çalışınca, Postgres'in kendi deadlock detector'ı devreye
girdi ve taraflardan birini "deadlock detected" hatasıyla iptal etti.

**Mülakatçı:** Bunu nasıl önlersin?

**Aday:** Deterministic lock ordering - her zaman küçük id'yi önce kilitle, hangi tarafın
"from" hangi tarafın "to" olduğuna bakmaksızın.

*(bkz. docs/pessimistic-locking.md)*

---

## Senaryo 5 — Isolation Level'ları

**Mülakatçı:** READ_COMMITTED ile REPEATABLE_READ arasındaki fark nedir?

**Aday:** READ_COMMITTED'de aynı transaction içinde iki kere okursan, aradaki başka bir
transaction'ın commit'ini görürsün (non-repeatable read). REPEATABLE_READ'de görmezsin, çünkü
Postgres tüm transaction için tek bir snapshot alıyor.

**Mülakatçı:** Phantom read'e ne dersin?

**Aday:** Standart SQL'e göre REPEATABLE_READ phantom read'e izin verir, ama Postgres'in
implementasyonu (tam snapshot isolation) phantom'u da engelliyor - bunu bizzat test ettim,
standardın ötesinde bir garanti.

**Mülakatçı:** READ_UNCOMMITTED'de dirty read görür müsün?

**Aday:** Postgres'te hayır - çünkü Postgres READ_UNCOMMITTED'i hiç implement etmiyor,
sessizce READ_COMMITTED'e yükseltiyor. Bunu da test ettim, uncommitted bir yazı hiçbir zaman
görünmedi.

**Mülakatçı:** Lost update'i @Version olmadan önleyebilir misin?

**Aday:** Evet - REPEATABLE_READ ile. İki transaction aynı satırı okuyup update etmeye
çalışınca, ikinci commit "could not serialize access due to concurrent update" hatasıyla
reddedildi. Yani izolasyon seviyesinin kendisi de bir lost-update önleme mekanizması.

*(bkz. docs/isolation.md)*

---

## Senaryo 6 — Propagation ve Proxy Problemi

**Mülakatçı:** `REQUIRES_NEW` ile `REQUIRED` arasındaki fark nedir?

**Aday:** REQUIRED çağıranın transaction'ına katılır ya da yoksa yenisini açar.
REQUIRES_NEW her zaman mevcut transaction'ı (ve onun connection'ını) suspend edip yepyeni,
bağımsız bir transaction açar.

**Mülakatçı:** Bir payment metodundan `this.audit()` ile REQUIRES_NEW'li bir metod
çağırırsam ne olur?

**Aday:** Bunu tam olarak yaşadım - hiçbir şey olmuyor, yani REQUIRES_NEW hiç devreye
girmiyor. Çünkü `@Transactional` proxy tabanlı, `this.` çağrısı proxy'yi atlıyor, audit()
sadece normal bir Java metod çağrısı olarak, payment'in transaction'ı içinde çalışıyor. Payment
rollback olunca audit de onunla beraber rollback oldu.

**Mülakatçı:** Nasıl düzelttin?

**Aday:** Audit'i ayrı bir bean'e taşıdım ve inject edilen referans üzerinden çağırdım.
Artık gerçekten proxy'den geçiyor, REQUIRES_NEW gerçekten yeni bir transaction açıyor ve outer
rollback olsa bile audit kaydı DB'de kalıyor.

**Mülakatçı:** Bu her zaman güvenli mi?

**Aday:** Hayır - REQUIRES_NEW commit olduktan sonra geri alınamaz. Eğer "audit" yerine
gerçek bir side effect (kart çekimi gibi) olsaydı, outer rollback olsa bile o side effect
kalıcı olurdu - tutarsızlık riski.

**Mülakatçı:** NESTED'i nerede kullanırsın?

**Aday:** Teoride, aynı connection üzerinde bir savepoint istediğimde - REQUIRES_NEW gibi
ayrı bir connection açmadan, sadece "bu kısmı rollback edebileyim" dediğimde. Ama pratikte
JPA/Hibernate ile hiç kullanamadım: `nestedTransactionAllowed=true` ayarlasam bile,
`HibernateJpaDialect`'in döndürdüğü transaction-data nesnesi `SavepointManager` uygulamadığı
için nested metodun gövdesi hiç çalışmıyor - proxy, `NestedTransactionNotSupportedException:
"JpaDialect does not support savepoints"` ile daha en baştan başarısız oluyor. NESTED,
gerçekte sadece saf JDBC tabanlı (`DataSourceTransactionManager`) kodda kullanılabilir bir
seçenek.

*(bkz. docs/transactions.md, docs/propagation.md)*

---

## Senaryo 7 — Bean Scope'ları

**Mülakatçı:** Spring singleton, GoF Singleton ile aynı şey mi?

**Aday:** Hayır. Spring singleton "her ApplicationContext için bir instance" demek,
container tarafından garanti ediliyor. GoF Singleton private constructor ve static
getInstance() ile sınıfın kendisi tarafından garanti ediliyor - farklı katmanlar.

**Mülakatçı:** Singleton bean'de instance field kullanmak neden tehlikeli?

**Aday:** Bunu bizzat gördüm - bir fiyat hesaplama servisinde instance field kullanınca,
50 concurrent request'in bazıları başka bir request'in base price'ından hesaplanmış sonuç
döndürdü. Field'ı local variable'a çevirince sorun kayboldu.

**Mülakatçı:** Prototype bean'i singleton'a normal şekilde inject edersem ne olur?

**Aday:** Injection sadece BİR KERE, singleton oluşturulurken olur. Her çağrıda aynı
instance'ı alırsın - bunu da test ettim, iki çağrı arasında hep aynı id çıktı.

**Mülakatçı:** Nasıl düzeltirsin?

**Aday:** İki yol: `ObjectProvider.getObject()` her çağrıda container'a tekrar sorar; ya
da scoped proxy (`proxyMode = TARGET_CLASS`) - injection normal field gibi görünür ama arkada
her çağrıda gerçek instance'ı getiren bir proxy vardır.

*(bkz. docs/bean-scopes.md)*

---

## Senaryo 8 — Race Condition'lar ve Senkronizasyon

**Mülakatçı:** `if (balance >= amount) balance -= amount;` neden thread-safe değil?

**Aday:** Üç ayrı adım - oku, karşılaştır, yaz - ve bunlar bir grup olarak atomik değil.
İki thread ikisi de kontrolü geçebilir, ikisi de düşer, biri diğerinin etkisini kaybeder. Bunu
bir bank account testinde ürettim - hesap tam sıfırlanması gerekirken sıfırlanmadı.

**Mülakatçı:** `volatile` bunu çözer mi?

**Aday:** Hayır - ayrı bir counter testinde kanıtladım. volatile sadece visibility
sağlıyor, `counter++` hala read-modify-write, atomicity sağlamıyor. Concurrent increment'ler
hala kayboldu.

**Mülakatçı:** Ne çözer?

**Aday:** `synchronized` ya da `AtomicInteger`. AtomicInteger'ı CAS (Compare-And-Set) ile
çözüyor - oku, hesapla, "hala okuduğum değer mi?" kontrolüyle atomik yaz, değilse retry.

**Mülakatçı:** synchronized ile ReentrantLock arasında ne zaman ReentrantLock seçersin?

**Aday:** `tryLock()` ile bloklamadan geri çekilmek istediğimde, `lockInterruptibly()`
ile iptal edilebilir bekleme istediğimde, ya da fairness policy gerektiğinde. Ayrıca
ReentrantLock'ta try/finally şart - synchronized'dan farklı olarak exception fırlarsa lock
otomatik serbest kalmıyor, bunu da bizzat kırıp gösterdim.

*(bkz. docs/java-locks.md)*

---

## Senaryo 9 — Deadlock, Livelock, Starvation

**Mülakatçı:** Deadlock nasıl tespit edilir?

**Aday:** JVM içi deadlock'ta `ThreadMXBean.findDeadlockedThreads()`. Ben iki thread'in
iki lock'u ters sırada almasıyla gerçek bir deadlock ürettim ve bu API ile tespit ettim.

**Mülakatçı:** Livelock deadlock'tan nasıl ayrılır?

**Aday:** Livelock'ta thread'ler BLOCKED değil, RUNNABLE kalırlar - sürekli birbirlerine
tepki verip geri çekiliyorlar ama ilerlemiyorlar. `findDeadlockedThreads()` livelock'u
yakalamaz. Ben bunu iki "kibar" worker'ı CyclicBarrier ile lockstep'e zorlayarak
deterministik şekilde ürettim - her round'da ikisi de başarısız oldu.

**Mülakatçı:** Starvation'ı nasıl kanıtlarsın, bu timing'e bağlı değil mi?

**Aday:** Timing bazlı bir test yazmaya çalıştım ama flaky çıktı. Bunun yerine
ReentrantLock'un fairness kontratını kullandım: fair bir lock'ta, sıraya önce giren thread
her zaman sonradan gelenden önce hizmet alıyor - deterministik. Non-fair'de bu garanti yok,
ki starvation riski tam olarak bu.

*(bkz. docs/java-locks.md)*

---

## Senaryo 10 — Executor'lar ve CompletableFuture

**Mülakatçı:** `Executors.newFixedThreadPool` neden production'da riskli olabilir?

**Aday:** Queue'su unbounded bir LinkedBlockingQueue. Bunu 2 thread'lik pool'a 5000
blocking task atarak gösterdim - hiçbir şey reddedilmedi, queue'da binlerce task birikti.
Kendi ThreadPoolExecutor'ımı bounded queue ve CallerRunsPolicy ile kurdum.

**Mülakatçı:** CallerRunsPolicy ne yapar?

**Aday:** Pool ve queue dolunca, yeni task'ı REDDETMEK yerine submit eden thread'in
kendisinde çalıştırır - bu doğal bir backpressure, work kaybı yok ama caller yavaşlıyor.

**Mülakatçı:** `supplyAsync().get()` art arda çağırmanın sorunu ne?

**Aday:** Her get() bir sonraki supplyAsync'i bloklar, paralellik hiç oluşmaz - toplam
süre üç çağrının toplamı kadar çıkar. Hepsini önce başlatıp sonra allOf ile birleştirince süre
tek çağrı kadar düşer.

**Mülakatçı:** Default supplyAsync hangi pool'u kullanır, sorun ne?

**Aday:** ForkJoinPool.commonPool() - parallelStream ile paylaşılan, JVM genelinde tek
bir pool. Ben bu pool'u bloklayan işlerle doyurup, tamamen ilgisiz bir parallelStream
işleminin ilerleyemediğini gösterdim.

*(bkz. docs/executor-service.md, docs/completable-future.md)*

---

## Senaryo 11 — @Async ve AOP Self-Invocation (Tekrar Eden Tema)

**Mülakatçı:** `@Async` metodları neden bazen "arka planda çalışmıyor"?

**Aday:** Self-invocation - `@Transactional` ile aynı proxy sınırlaması. `this.method()`
proxy'yi atlar, metod senkron çalışır. Thread isimlerini karşılaştırarak kanıtladım: self
invocation'da caller ve "async" metod aynı thread'de çalışıyordu.

**Mülakatçı:** Bu sadece @Async'e mi özgü?

**Aday:** Hayır - aynı şeyi kendi yazdığım bir @Around aspect'te de gördüm.
`this.slowStep()` ile çağırınca aspect hiç devreye girmedi, süre kaydedilmedi. Üçü de
(Transactional, Async, custom Aspect) aynı Spring AOP proxy mekanizmasına dayanıyor.

**Mülakatçı:** void dönen bir @Async metod hata fırlatırsa ne olur?

**Aday:** Caller'ın yakalayacağı hiçbir şey yok - `AsyncUncaughtExceptionHandler`'a
gider. CompletableFuture dönen bir @Async metod ise farklı: Spring hatayı doğrudan dönen
future'a koyuyor, caller normal şekilde `.exceptionally()` ile yakalayabiliyor.

*(bkz. docs/async.md, docs/aop.md, docs/transactions.md)*

---

## Senaryo 12 — N+1, Lazy Loading, LazyInitializationException

**Mülakatçı:** N+1 problemi nedir?

**Aday:** Bir liste sorgusundan (1 sorgu) sonra her elemanın lazy koleksiyonuna
erişmenin, eleman sayısı kadar (N) ek sorgu tetiklemesi. Ben bunu 5 order için tam olarak
6 sorgu (1+5) olarak SQL logunda gördüm.

**Mülakatçı:** EAGER yaparsan çözülür mü?

**Aday:** Hayır, farklı bir maliyete dönüşür. OrderItem'daki Product EAGER olduğu için,
product'a hiç dokunmayan bir metod bile her seferinde product'ı join'liyordu - "ücretsiz"
değil, sadece görünmez bir maliyet.

**Mülakatçı:** Doğru çözüm ne?

**Aday:** Duruma göre: fetch join veya @EntityGraph tek sorguda ilişkiyi de getiriyor;
eğer tam entity'e ihtiyacım yoksa DTO projection en verimlisi - hiç entity/koleksiyon
yüklenmiyor.

**Mülakatçı:** LazyInitializationException ne zaman alırsın?

**Aday:** Transaction/session kapandıktan sonra lazy bir alana erişmeye çalışınca. Bunu
da bizzat tetikledim: bir entity'yi @Transactional metod içinde yükleyip lazy koleksiyona hiç
dokunmadan return ettim, sonra dışarıdan erişince exception aldım.

**Mülakatçı:** Open Session in View bunu çözmez mi?

**Aday:** Çözer ama bedelle - connection'ı tüm request boyunca (template render dahil)
açık tutar ve lazy sorguların nereden geldiğini gizler. Ben projede bilinçli olarak kapalı
tutuyorum, böylece bu tür buglar erken ve açıkça ortaya çıkıyor.

*(bkz. docs/n-plus-one.md)*

---

## Senaryo 13 — Exception Handling

**Mülakatçı:** Checked exception'ları neden unchecked'e tercih etmiyorsun?

**Aday:** İki sebep: zorunlu catch/throws boilerplate'i insanları
`catch(Exception e){}` yazmaya itiyor, ve Spring'in default rollback kuralı checked
exception'da rollback yapmıyor - bunu bizzat gördüm, bir checked exception fırlayınca debit
işlemi commit oldu, oysa rollback beklerdim.

**Mülakatçı:** Exception'ı yutmanın somut zararı ne?

**Aday:** Bir payment gateway çağrısını `catch(Exception e){}` ile yuttuğumda, caller
`true` (başarılı) dönüşü aldı - gerçekte ödeme başarısız olmuştu ve hiçbir iz kalmadı.

**Mülakatçı:** Sadece `throw new RuntimeException("error")` ile rethrow etsem yeterli mi?

**Aday:** Hayır - orijinal exception'ı cause olarak vermezsen kaybolur. Bunu da test
ettim, `getCause()` null çıktı. Doğrusu orijinal exception'ı cause olarak taşıyan anlamlı bir
exception fırlatmak.

*(bkz. docs/exceptions.md)*

---

## Senaryo 14 — Bağlam İçinde Design Pattern'lar

**Mülakatçı:** Strategy pattern'i ne zaman kullanırsın?

**Aday:** Bir if/else zincirinin her yeni tip eklendiğinde büyümesini önlemek için.
Payment tipini if/else yerine ayrı sınıflara (`PaymentStrategy` implementasyonları) taşıdım,
Factory de doğru olanı seçiyor - Spring'in `List<PaymentStrategy>` autowiring'i sayesinde yeni
bir tip eklemek sadece yeni bir `@Component` eklemek demek.

**Mülakatçı:** Builder'ı her zaman record'a tercih eder misin?

**Aday:** Hayır - tüm alanlar zorunluysa record daha basit. Builder'ı, birkaç OPSİYONEL
alanı olan (kupon kodu, hediye paketi gibi) bir sınıf için kullandım; record'un canonical
constructor'ı her çağrıda pozisyonel olarak null/false geçmemi zorlardı.

**Mülakatçı:** Proxy pattern'i Spring'in neresinde görüyorsun?

**Aday:** Her yerde - @Transactional, @Async, ve her @Aspect birer Spring AOP proxy'si.
Bunu somutlaştırmak için kendi elimle bir JDK dynamic proxy yazdım, logging yapan basit bir
Greeter proxy'si - ve bunun self-invocation'ı neden kırdığını (proxy `this` değil) tam olarak
anladım.

*(bkz. docs/design-patterns.md)*

---

## Ek Sorular (hızlı ateş)

51. **Q:** `saveAndFlush()` her yerde kullanılmalı mı? **A:** Hayır - loop içinde her
    iterasyonda çağırmak, tek bir commit-time flush yerine N ayrı flush'a (round trip)
    dönüşür; bunu Hibernate `Statistics.getFlushCount()` ile ölçtüm.
52. **Q:** `ConcurrentHashMap` her zaman thread-safe mi? **A:** Tekil metodlar evet, ama
    `if (!map.containsKey(k)) map.put(k, v)` gibi compound bir işlem hala racy -
    `computeIfAbsent` kullanmak gerekiyor, bunu da test ettim.
53. **Q:** `Optional.orElse()` ile `orElseGet()` farkı nedir? **A:** `orElse` argümanı her
    zaman eager evaluate edilir (değer mevcut olsa bile), `orElseGet` sadece absent ise
    supplier'ı çağırır - bunu bir side-effect sayacıyla kanıtladım.
54. **Q:** `parallelStream()` her zaman güvenli mi? **A:** Hayır - shared mutable bir
    ArrayList'e side-effecting forEach ile yazmak veri kaybına yol açabilir; `collect()`
    kullanmak gerekir.
55. **Q:** equals override edip hashCode etmezsen ne olur? **A:** HashSet iki
    "eşit" nesneyi de içine alabilir, çünkü farklı bucket'lara düşerler - bunu test ettim.
