# Atomic Primitifler, CAS ve ABA Problemi

Kod: `com.interviewlab.concurrency.atomic.*` — Testler: `VolatileAndAtomicTest`

## `AtomicInteger` neden doğru, `volatile int` neden değil?

```java
counter++;                       // volatile - NOT atomic: read, add, write, three steps
atomicCounter.incrementAndGet();  // Atomic - IS atomic: one CAS-based operation
```

`incrementAndGet()`, donanım düzeyinde bir **Compare-And-Set** komutuyla uygulanır: mevcut
değeri oku, yeni değeri hesapla, sonra **sadece** başka bir thread bu arada değeri
değiştirmediyse yeni değeri atomik olarak geri yaz; başka biri önce davrandıysa, tüm
oku-hesapla-yaz işlemini baştan tekrar dene. İşlemi bir bütün olarak atomik yapan da bu retry
döngüsüdür - salt bir `volatile` field'ın yapısal olarak sağlayamayacağı bir şey, çünkü
visibility, "benim okumam ile yazmam arasında başka biri buna dokundu mu?" sorusuna hiçbir
şey söylemez.

`VolatileAndAtomicTest` her iki tarafı da kanıtlar:
`shouldLoseIncrementOperationsWithVolatileCounter()` concurrency altında increment
kaybediyor; `shouldProduceCorrectCountWithAtomicInteger()` asla kaybetmiyor.

## synchronized ile Atomic Karşılaştırması

`synchronized`, kaybeden thread'i bloklar - thread parklanır ve sırasını bekler, bu da
potansiyel olarak bir OS context switch'i tetikler. CAS tabanlı atomic'ler, kaybeden
thread'in CPU üzerinde dönmeye ve tekrar denemeye devam etmesine izin verir, hiç bloklamaz.
Düşük-orta contention altında bu belirgin şekilde daha ucuzdur. ÇOK yüksek contention altında
(aynı atomic'e çok sayıda thread saldırıyorsa), CAS retry'larının kendisi pahalı hale gelir -
her thread sürekli başarısız olup yeniden okur - bu noktada striped/sharded bir counter
(`java.util.concurrent.atomic.LongAdder`) genellikle tek bir `AtomicInteger` veya
`AtomicLong`'dan daha iyi performans gösterir.

## ABA Problemi

```java
// com.interviewlab.concurrency.atomic.AbaProblemDemo
ref.set(original);                                    // "A"
String observed = ref.get();                          // thread reads "A"
// ...meanwhile, another thread changes A -> B -> A...
ref.compareAndSet(observed, "final-value");            // succeeds! ref still "looks like" observed
```

`AtomicReference` üzerindeki sıradan bir CAS, sadece object identity/equality'yi
karşılaştırır. Bir thread kendi oku-sonra-CAS-yap sırasının ortasındayken bir değer
A→B→A şeklinde değişirse, o thread'in CAS'ı "hâlâ A" görür ve başarılı olur - değer
gerçekten değişip aradaki sürede geri dönmüş olsa bile. Basit bir counter için zararsızdır
(A, A'dır); ancak "bu değer gitti ve geri geldi" bilgisinin anlam taşıdığı yapılar için
tehlikelidir - klasik örnek, lock-free stack/queue node'ları; burada pop edilen bir node'un
belleği yeniden kullanılıp aynı identity ile geri push edilebilir.
`shouldSucceedFalsePositivelyWithPlainAtomicReferenceUnderAbaInterleaving()`, bu yanlış
pozitif başarıyı kanıtlar.

## Çözüm: `AtomicStampedReference`

```java
ref.compareAndSet(original, intermediate, 0, 1);   // A -> B, stamp 0 -> 1
ref.compareAndSet(intermediate, original, 1, 2);   // B -> A, stamp 1 -> 2
// thread's CAS now correctly fails: stamp moved even though the value looks the same
ref.compareAndSet(observedValue, "final", observedStamp, observedStamp + 1);
```

Her değeri, AYRICA eşleşmesi gereken bir tamsayı stamp ile eşleştirmek, değerin kendisi
orijinal haline geri dönse bile aradaki mutasyonu görünür kılar -
`shouldDetectAbaInterleavingWithAtomicStampedReference()`, CAS'ın artık doğru şekilde
başarısız olduğunu kanıtlar.

## Sık Sorulan Mülakat Soruları

- **Q:** CAS nasıl çalışır? — Mevcut değeri oku, yeni değeri hesapla, "hala okuduğum değer
  mi?" kontrolüyle atomik olarak yaz; değilse retry et.
- **Q:** synchronized yerine ne zaman Atomic kullanılır? — Düşük/orta contention'da, basit
  tek-değişkenli güncellemelerde; synchronized daha karmaşık kritik bölgeler için.
- **Q:** ABA problemi neden önemlidir? — Lock-free veri yapılarında (stack/queue), bir
  değerin "hiç değişmediğini" değil "aynı görünüme geri döndüğünü" CAS ayırt edemez.
- **Q:** Çok yüksek contention'da Atomic'in performansı ne olur? — CAS retry'ları artar,
  `LongAdder` gibi striped alternatifler daha iyi ölçeklenir.

## 30 Saniyelik Mülakat Cevabı

"volatile ile Atomic arasındaki farkı bir counter testiyle kanıtladım: volatile int üzerinde
counter++ concurrent thread'lerde increment kaybediyordu, AtomicInteger'a geçince
kaybolmuyordu - çünkü CAS bütün read-modify-write'ı atomik hale getiriyor, volatile sadece
visibility sağlıyor. ABA problemini de AtomicReference ile gösterdim: bir thread A'yı okuyup
CAS yapmadan önce başka bir thread değeri A->B->A yapınca, plain CAS bunu fark etmeden
başarılı oluyordu; AtomicStampedReference'taki stamp bu ara değişikliği yakalayıp CAS'ı doğru
şekilde reddetti."

## Takip Soruları

- `LongAdder` iç yapısı nasıl çalışır (striping)?
- `AtomicReference.updateAndGet()` ile manuel CAS loop'u arasındaki fark nedir?
- CAS'ın CPU seviyesinde karşılığı nedir (`cmpxchg`)?
