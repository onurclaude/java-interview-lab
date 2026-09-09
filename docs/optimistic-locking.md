# Optimistic Locking

Kod: `com.interviewlab.locking.optimistic.*` — Testler: `OptimisticLockingTest`

# Problem

İki transaction aynı `Product` satırını okuyor (`version=0`). İkisi de okuduklarından yeni
bir stok değeri hesaplıyor. İkisi de commit etmeye çalışıyor. Somut olarak ne olur — hangi SQL
çalışır, hangi exception (varsa) fırlatılır?

## Hatalı Kod — `@Version` olmadan locking varsayımı

```java
// com.interviewlab.locking.optimistic.bad.NoVersionProduct - no @Version field at all
```

## Neden Yanlış

Dirty checking, koşulsuz olarak, ŞU ANDA bellekteki field'ların söylediği her ne ise onu
yazar. Bir `@Version` sütunu olmadan, üretilen `UPDATE`'in `WHERE` cümlesi sadece
`WHERE id = ?`'dir — satırın okunduğundan beri değiştiğini fark etmesinin hiçbir yolu yoktur.

## Gerçekte Ne Oluyor

`OptimisticLockingTest.shouldLoseUpdateWithoutVersionField()`: T1 stock=100 yükler, T2
(T1 commit etmeden önce) stock=100 yükler. T1, 2 azaltır ve commit eder → stock=98. T2, hâlâ
bellekte eski `stock=100`'ü tutarak, BUNDAN 3 azaltır ve commit eder → **stock=97**. T1'in
değişikliği kaybolur; doğru cevaba (100-2-3=95) hiçbir zaman ulaşılmaz — klasik bir **lost
update**, ve bunu söyleyen hiçbir exception hiç fırlatılmaz.

## Doğru Kod

```java
// com.interviewlab.locking.entity.Product
@Version
private Long version;
```

Hibernate artık commit'te şunu üretir:

```sql
UPDATE lab_locking_product SET stock=?, version=? WHERE id=? AND version=?
```

Başka bir transaction `version`'ı zaten ilerlettiyse, bu `WHERE` **sıfır satırla** eşleşir.
Hibernate bunu tespit eder (versioned bir update'te etkilenen satır sayısını her zaman
kontrol eder) ve `jakarta.persistence.OptimisticLockException` fırlatır; bunu Spring
`org.springframework.orm.ObjectOptimisticLockingFailureException`'a çevirir.

**Bu, veritabanı satırını kilitler mi?** Hayır. Hiçbir noktada hiçbir veritabanı-seviyesi
lock söz konusu değildir — kontrol, sonradan yapılan salt "WHERE cümlesi eşleşti mi?"
sorgusudur. Bu, pessimistic locking'den (docs/pessimistic-locking.md) temel farktır:
optimistic locking çakışmaları TESPİT EDER; pessimistic locking, bloklayarak onların
mümkün olmasını baştan ENGELLER.

`OptimisticLockingTest.shouldPreventLostUpdateWithOptimisticLock()`, yukarıdaki tam
interleaving'in artık sessizce veriyi bozmak yerine exception fırlattığını kanıtlar: stock
tam olarak 98'de biter (sadece T1'in değişikliği hayatta kalır), ve T2'nin denemesi gürültülü
şekilde başarısız olur.

## Hatalı varyantlar, hepsi gerçek, hepsi test edilmiş

1. **`@Version` yok** — sadece dirty checking'in "optimistic locking" olduğuna inanmak.
   Yukarıda gösterildi.
2. **Exception'ı yutmak** (`SwallowingOptimisticLockService`):
   `catch (ObjectOptimisticLockingFailureException e) { }` — çakışma doğru şekilde tespit
   edilir ve sonra çöpe atılır; caller yazmanın başarılı olduğuna inanır
   (`shouldSilentlyDoNothingWhenSwallowingOptimisticLockException`).
3. **Sınırsız retry** (`UnboundedRetryStockService`): limit ve backoff olmadan çakışmada
   `while (true)` ile retry etmek. Sürekli contention altında (ham-JDBC bir "hammer" ile
   `version`'ı sürekli artırarak simüle edilir), `RetryingStockService` (sınırlı, 3 deneme)
   doğru şekilde pes eder; sınırsız versiyon devam eder
   (`shouldEventuallySucceedWithUnboundedRetryOnceContentionStops`, contention durduğunda
   sonunda başarılı olduğunu, sınırlı versiyonun asla izin vermeyeceği kadar çok deneme
   aldıktan sonra kanıtlar).
4. **Tek bir hot row'da çok yüksek contention**: aynı "hammer" senaryosu tam olarak bu
   durumdur — optimistic locking, sürekli ağır concurrent yazma altındaki tek bir satır için
   kötü bir uyumdur, çünkü ÇOĞU deneme çakışır ve retry etmek zorunda kalır; pessimistic
   locking (herkesi yarışıp retry etmek yerine sadece sıraya sokan), genellikle throughput'ta
   burada kazanır.
5. **Client-controlled version** (`ClientControlledVersionService`): caller/request
   tarafından sağlanan bir version numarasına güvenip onu doğrudan managed entity'ye yazmak
   — hâlâ gerçek bir kod kokusu (provider'a ait bir alana client girdisi bağlanıyor), ama
   `shouldIgnoreClientSuppliedVersionAndAlwaysPersistHibernatesOwnIncrement` **şaşırtıcı**
   bir şeyi kanıtlıyor: kalıcı hale gelen version, client'ın gönderdiği 999 değil, Hibernate'in
   kendi ürettiği değer (1) oluyor. Hibernate, versiyonlu bir UPDATE üretirken entity'nin
   bellekteki `version` alanına hiç güvenmez — SET cümlesi için her zaman kendi yüklediği
   (snapshot) değer + 1'i kullanır. Yani bu spesifik saldırı bu ORM mapping'inde işe yaramaz;
   asıl risk `entityManager.merge()` ile detached bir entity birleştirirken client'ın
   sağladığı version'ın merge'ün optimistic-check'inde kullanılmasıdır (bu projenin kapsamı
   dışında, ama gerçek risk noktası orasıdır).

## Trade-off'lar: retry yapılmalı mı, yapılmamalı mı?

Sınırlı retry+backoff, `RetryingStockService.decreaseStockWithRetry` için güvenlidir, çünkü
"mevcut stok'u yeniden oku ve -N'i yeniden uygula" niyet olarak idempotent'tir - onu taze veri
üzerinde yeniden çalıştırmak tam olarak doğru şeyi yapar. **Genel olarak güvenli değildir**:
idempotent olmayan bir external side effect içeren (örn. gerçek bir kart çekimi) tüm bir
business operasyonunu körü körüne retry etmek, o side effect'i iki kez yapma riski taşır.
Yerel, version-korumalı yazmayı retry edin; asla geri alınamaz bir şey içeren tüm bir akışı
retry etmeyin.

## Sık Sorulan Mülakat Soruları

- **Q:** Optimistic locking nedir? — Bir satırı okuyup güncellerken, aradan başka bir
  transaction'ın o satırı değiştirip değiştirmediğini bir `version` sütunuyla kontrol eden,
  DB'de hiçbir lock almayan bir çakışma tespit mekanizması.
- **Q:** `@Version` nasıl çalışır? — Her UPDATE'in WHERE'ine `AND version=?` ekler, SET'ine
  `version=version+1` ekler; etkilenen satır sayısı 0 ise exception fırlatır.
- **Q:** Database row'u gerçekten locklar mı? — Hayır, hiçbir zaman.
- **Q:** Pessimistic locking'den farkı nedir? — Optimistic: çakışmayı SONRADAN tespit eder,
  lock almaz, retry ister. Pessimistic: çakışmayı BAŞTAN imkansız kılar, lock alır, blocking
  yapar.
- **Q:** `OptimisticLockException` ne zaman oluşur? — Versioned UPDATE/DELETE'in WHERE
  koşulu 0 satır eşleştirdiğinde.
- **Q:** Retry yapılmalı mı? — Sınırlı sayıda ve backoff ile, sadece operasyon idempotent
  ise; irreversible side effect içeren operasyonlarda hayır.

## 30 Saniyelik Mülakat Cevabı

"Bunu bir stock update senaryosunda denedim. İki transaction aynı version'ı okudu (version=0),
ilk transaction commit ettikten sonra ikinci transaction'ın `UPDATE ... WHERE version=0`
sorgusu zero rows affected döndürdü ve Hibernate `ObjectOptimisticLockingFailureException`
fırlattı. `@Version` olmadan aynı senaryoyu denediğimde exception falan almadım — ikinci
transaction birinci transactionin değişikliğini sessizce ezdi, klasik lost update. Yani
'optimistic locking var' demek için JPA kullanmak yetmiyor, `@Version` alanı şart."

## Takip Soruları

- `@Version` alanı `Long` mi `int` mi olmalı, fark eder mi?
- Optimistic locking ile isolation level tabanlı lost-update önleme (docs/isolation.md)
  arasındaki fark nedir?
- `saveAndFlush()` optimistic lock exception'ı ne zaman fırlatır - hemen mi, commit'te mi?
