# JVM Runtime Memory Areas ve Java Memory Model (JMM)

Bu iki kavram sıkça karıştırılır ama TAMAMEN farklı sorulara cevap verir:
- **JVM Runtime Memory Areas** (bu bölüm): JVM, bellekte NEREDE ne saklar? (heap, stack,
  metaspace...) - bir depolama/yerleşim sorusu.
- **Java Memory Model / JMM** (aşağıda): çoklu thread'ler AYNI belleği okurken/yazarken
  HANGİ GARANTİLER var? (visibility, ordering, atomicity) - bir eşzamanlılık-semantiği
  sorusu. Bkz. `docs/java-locks.md`, `docs/atomic.md`.

## JVM Runtime Memory Areas (JVM Spec §2.5)

```
Kaynak kod (.java)
     | javac
Bytecode (.class)
     | JVM class loading
+-------------------------------------------------------+
|                    JVM Runtime                        |
|  +----------------+  +----------------+  +----------+ |
|  |      Heap      |  |   Metaspace    |  |  ...     | |
|  | (TÜM thread'ler|  | (class         |  |          | |
|  |  paylaşır)     |  |  metadata,     |  |          | |
|  |                |  |  TÜM thread'ler|  |          | |
|  +----------------+  |  paylaşır)     |  +----------+ |
|                       +----------------+               |
|  +----------------+  +----------------+  +----------+ |
|  | Thread-1 Stack |  | Thread-2 Stack |  |  ...     | |
|  | (bu thread'e   |  | (bu thread'e   |  |          | |
|  |  ÖZEL)         |  |  ÖZEL)         |  |          | |
|  +----------------+  +----------------+  +----------+ |
+-------------------------------------------------------+
```

### Heap

Tüm `new` ile oluşturulan nesneler (ve dizi'ler) burada yaşar. **Tüm thread'ler
tarafından PAYLAŞILIR** - bu yüzden heap'teki bir nesneye birden fazla thread'in erişimi,
JMM'in devreye girdiği tam olarak bu durumdur (aşağıya bakın). Garbage Collector'ın
yönettiği alan burasıdır (bkz. `docs/garbage-collection.md`).

### Thread Stack

**Her thread'in kendi, özel stack'i vardır** - başka hiçbir thread buna erişemez, bu
yüzden burada YARIŞ (race) OLAMAZ. Her metod çağrısı, o thread'in stack'ine bir "stack
frame" ekler; frame şunları tutar:
- **Local variable'lar** (primitive değerler doğrudan, nesne REFERANSLARI - nesnenin
  kendisi değil, heap'teki adresi).
- Metod parametreleri.
- Return adresi.

Bu proje bunu tam olarak neden kullanıyor: `docs/bean-scopes.md`'deki
`StatelessPriceService` (singleton, ama her thread kendi local değişkenlerini stack'inde
tutar - paylaşılan hiçbir şey yok) vs `MutableSingletonPriceService` (bilerek instance
alanına - yani HEAP'e - yazıyor, bu yüzden race oluyor). **Bu tam olarak "yerel
değişkenler stack'te, instance alanları heap'te" ayrımının canlı kanıtı.**

### Metaspace (Java 8+, PermGen'in yerine geçti)

Sınıfların KENDİ metadata'sını tutar: sınıf yapısı, metod bytecode'u, runtime constant
pool, `static` alanlar (Java 8'den itibaren static alanlar da burada, heap'te değil -
öncesinde PermGen'in bir parçasıydı). **Reflection İLE AYNI ŞEY DEĞİLDİR** - metaspace
bu metadata'nın SAKLANDIĞI yerdir, reflection ise o metadata'yı RUNTIME'da SORGULAMAK
için kullanılan bir API'dir (bkz. `docs/NOTES_CORRECTIONS.md` #reflection,
`ReflectionTest`). Metaspace, native (JVM heap dışı) bellektedir - varsayılan olarak
sınırsızdır (`-XX:MaxMetaspaceSize` ile sınırlanabilir); çok sayıda dinamik sınıf
üreten uygulamalar (ör. ağır proxy/bytecode-generation kullanan framework'ler) burada
bir "metaspace leak" yaşayabilir.

### PC Register (Program Counter)

Her thread'in kendine ait, şu an yürütülmekte olan JVM bytecode talimatının adresini
tutan küçük bir bellek alanı. Kavramsal olarak önemlidir (context switch'te "kaldığın
yerden devam et" burada saklanır) ama uygulama kodu bununla DOĞRUDAN hiç etkileşmez.

### Native Method Stack

Java kodu bir native (JNI - C/C++ ile yazılmış) metod çağırdığında kullanılan, Thread
Stack'e benzer ama native kod için ayrı bir stack. Bu projede doğrudan gözlemlenmez
(JNI kullanmıyoruz), ama JVM spec'inin bir parçası olarak bilinmesi beklenir.

---

## Java Memory Model (JMM) — JLS Chapter 17

JMM, "birden fazla thread aynı heap belleğine erişirken hangi garantiler var?" sorusunu
cevaplar. Üç temel kavram:

### Visibility (görünürlük)

Bir thread bir değişkeni değiştirdiğinde, BAŞKA bir thread bu değişikliği GÖRECEĞİNİN
garantisi YOKTUR - CPU cache'leri, derleyici optimizasyonları (reordering), her thread'in
değeri kendi cache'inde "bayat" tutmasına izin verebilir. `volatile`, bir alana yapılan
her yazmanın ana belleğe HEMEN görünür olmasını garanti eder (bkz. `docs/atomic.md`
`ShutdownFlagWorker` - `volatile boolean running` olmadan, bir worker thread'in
`shutdown()` çağrısını ASLA görmeyip sonsuza kadar çalışmaya devam edebileceği
gösterilir).

### Atomicity (atomiklik)

`volatile`, SADECE visibility garantisi verir, ATOMİKLİK VERMEZ. `count++` gibi bir
compound (oku-değiştir-yaz) operasyon, `volatile` olsa bile atomik DEĞİLDİR - üç ayrı
adımdır (oku, artır, yaz) ve iki thread arasında interleave olabilir. Bu proje bunu
`docs/atomic.md`'de `VolatileCounter` (bad) ile kanıtlıyor: `volatile int count` üzerinde
`count++` çağıran çok sayıda thread, beklenen toplamdan DAHA AZ bir sonuç üretir - visibility
garantisi olsa bile, atomiklik yoksa lost update olur. Gerçek atomiklik için
`AtomicInteger` (CAS tabanlı) ya da `synchronized` gerekir.

### Ordering (sıralama) ve happens-before

Derleyici/JIT/CPU, PROGRAM SIRASINI (bir thread'in KENDİ içindeki görünen davranışını
bozmadığı sürece) yeniden sıralayabilir (reorder) - tek thread'li kod için bu görünmez,
ama çok thread'li kodda "thread A'nın 2. satırı, thread B'nin gördüğü sırada 1. satırdan
ÖNCE" gibi garantiler olmadan sürprizlere yol açabilir. **happens-before**, JMM'in bu
sırayı GARANTİ ETTİĞİ belirli noktaları tanımlar:
- Bir `volatile` alana yazma, o alana yapılan SONRAKİ okumalarla happens-before ilişkisi kurar.
- Bir `synchronized` bloktan ÇIKMAK (unlock), aynı monitor'a SONRADAN girmekle (lock)
  happens-before ilişkisi kurar.
- Bir `Thread.start()` çağrısı, başlatılan thread'in İÇİNDEKİ her şeyle happens-before
  ilişkisi kurar.
- Bir thread'in sonlanması (`Thread.join()` ile beklenen), `join()`'den SONRAKİ kodla
  happens-before ilişkisi kurar.
- Bu projenin HER YERDE kullandığı `CountDownLatch.countDown()`/`await()` de bu garantiyi
  taşır (`java.util.concurrent` yapıları JMM'in happens-before zincirine dahildir) - bu
  YÜZDEN bu projenin tüm concurrency testleri latch kullanır, `Thread.sleep()` ile "umarım
  yeterince beklemiştir" YAKLAŞIMINA GÜVENMEZ.

**Bu proje JMM'i şu labda doğrudan uyguluyor:** `docs/java-locks.md`
(`synchronized`/`ReentrantLock`), `docs/atomic.md` (`volatile`/`Atomic*`), ve her
concurrency testinde latch'lerin KENDİSİ happens-before'un pratik uygulamasıdır.

## Sık Sorulan Mülakat Soruları

- **Q:** Heap ile stack arasındaki fark nedir? — Heap tüm thread'ler paylaşır (nesneler
  burada), stack her thread'e özeldir (local değişkenler + referanslar burada).
- **Q:** `volatile`, thread-safety garantisi mi verir? — Hayır, sadece VISIBILITY. Compound
  operasyonlar (`count++`) hâlâ atomik değildir.
- **Q:** Metaspace ile reflection aynı mı? — Hayır. Metaspace, class metadata'sının
  SAKLANDIĞI yer; reflection, o metadata'yı SORGULAMAK için kullanılan API.
- **Q:** happens-before ne işe yarar? — Derleyici/CPU'nun reorder edemeyeceği, JMM
  tarafından GARANTİ EDİLEN sıralama noktalarını tanımlar (volatile write→read,
  unlock→lock, thread start/join).

## 30 Saniyelik Mülakat Cevabı

"Heap tüm thread'lerin paylaştığı, nesnelerin yaşadığı alan - stack her thread'e özel,
local değişkenler ve nesne referansları (nesnenin kendisi değil) burada. JMM ise bu
paylaşılan heap belleğine çoklu thread erişiminde hangi garantilerin olduğunu tanımlıyor:
visibility (volatile), atomicity (AtomicInteger/synchronized) ve ordering (happens-before).
Projede bunu somut gördüm: volatile bir sayaç üzerinde count++ hâlâ lost update
üretiyordu - visibility yeterli değildi, atomiklik de gerekiyordu."

## Takip Soruları

- `final` alanların JMM'de özel bir garantisi var mı? (Evet - constructor tamamlandıktan
  sonra, final alanlara yapılan yazmalar diğer thread'lere "freeze" garantisiyle görünür,
  ekstra senkronizasyon olmadan - "safe publication" için önemli bir detay.)
- Metaspace neden sınırsız büyüyebilir, ve bu ne zaman bir sorun olur?
