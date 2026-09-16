# Pessimistic Locking

Kod: `com.interviewlab.locking.pessimistic.*`, `com.interviewlab.locking.deadlock.*`

**Birincil öğrenme arayüzü — Postman + IntelliJ debugger + DBeaver:**
```http
POST /api/labs/pessimistic/reset
POST /api/labs/pessimistic/bad
POST /api/labs/pessimistic/good
```
DBeaver'da kilit tutan satırı görmek için: `SELECT * FROM pg_locks WHERE relation =
'lab_locking_product'::regclass;`. `PessimisticLockingTest`, AYNI davranışın otomatik
regresyon kanıtıdır — ikincildir, birincil değil.

# Problem

Bir çakışmayı sonradan tespit etmek (optimistic locking) yerine, ikinci bir transaction'ın
ilki bitene kadar aynı satırı read-for-update ile okumasını bile imkansız hale getirmek.

## Doğru Kod

```java
// com.interviewlab.locking.entity.ProductRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select p from Product p where p.id = :id")
Optional<Product> findByIdForUpdate(Long id);
```

PostgreSQL'de bu, gerçek bir satır kilidine derlenir — ama harfiyen `FOR UPDATE` değil:
Hibernate 6, PostgreSQL üzerinde `PESSIMISTIC_WRITE` için varsayılan olarak `FOR NO KEY UPDATE`
üretir (foreign-key kısıtlarını gereksiz yere bloklamayan, PostgreSQL'e özgü daha gevşek bir
satır-yazma kilidi türü) — `PessimisticLockingTest.shouldIssueForUpdateSqlForPessimisticWriteLock()`,
yakalanan SQL'in `for update` ya da `for no key update` içerdiğini assert eder. Aynı satır için aynı metodu çağıran ikinci bir
transaction, ilki commit edene ya da rollback olana kadar **gerçekten veritabanında bloklanır**
- `shouldBlockSecondTransactionWithPessimisticWriteLock()`, T2'nin gerçek wall-clock
bekleyişini ölçer ve T1'in lock'u tuttuğu süreyle eşleştiğini gösterir.

## Hatalı Kod — transaction dışında locking

```java
// com.interviewlab.locking.pessimistic.bad.LockOutsideTransactionService
public void lockWithoutTransaction(Long productId) {
    productRepository.findByIdForUpdate(productId); // no @Transactional on this method
}
```

## Neden Yanlış

Bir row lock, sadece onu tutan transaction'ın ömrü boyunca anlamlıdır. Transaction yoksa, JPA
bunu doğrudan reddeder: `shouldThrowWhenAcquiringPessimisticLockOutsideTransaction()`,
`jakarta.persistence.TransactionRequiredException`'ı gösterir.

## Hatalı Kod — lock'u bir external çağrı boyunca tutmak

```java
// com.interviewlab.locking.pessimistic.bad.LockHeldDuringExternalCallService
Product product = productRepository.findByIdForUpdate(productId).orElseThrow();
simulateSlowExternalCall(simulatedExternalCallMillis); // lock held for ALL of this
product.decreaseStock(amount);
```

Bir lock, veritabanıyla hiç ilgisi olmayan bir şeyi bekleyerek geçen süre dahil,
transaction'ın TÜM süresi boyunca tutulur. `shouldHoldLockForFullDurationOfExternalCall()`,
ikinci bir lock arayan tarafın bekleyişinin, sadece DB yazmasını değil, simüle edilen çağrının
tam gecikmesini de içerdiğini gösterir. **Çözüm:** external çağrıları lock'u almadan önce
yapın ve lock tutan transaction'ı mümkün olduğunca kısa tutun.

## Veritabanı seviyesinde gerçek deadlock

```java
// com.interviewlab.locking.deadlock.InconsistentLockOrderTransferService
Product from = productRepository.findByIdForUpdate(fromId).orElseThrow();  // locks first
... wait for the other side to also grab its first lock ...
Product to = productRepository.findByIdForUpdate(toId).orElseThrow();      // may deadlock here
```

T1, A→B transfer eder (A'yı kilitler, B'yi ister); T2, B→A transfer eder (B'yi kilitler, A'yı
ister) — gerçek bir circular wait, bu sefer JVM'in `ThreadMXBean`'i değil, **PostgreSQL'in
kendi deadlock detector'ı** tarafından tespit edilir (`concurrency.pathologies` /
docs/java-locks.md'deki JVM-içi `synchronized` deadlock'uyla karşılaştırın). Postgres,
döngüyü kırmak için iki transaction'dan birini `deadlock detected` hatasıyla (SQLState
`40P01`) öldürür - `shouldDeadlockAtDatabaseLevelWithInconsistentLockOrder()`, iki
transaction'dan tam olarak birinin bu hatayla başarısız olduğunu kanıtlar. **Çözüm:** her
zaman deterministik bir sırada kilitleyin (örn. id'ye göre) -
`DeterministicOrderTransferService` / `shouldNotDeadlockWithDeterministicLockOrder()`.

## Diğer hatalı kullanımlar (kısaca)

- **Her okumada `PESSIMISTIC_WRITE`**, hiç yazmayanlarda bile: her okuyucuyu, sebepsiz yere,
  diğer her okuyucunun arkasında sıraya sokar - güvenle eşzamanlı çalışabilecek okumalar artık
  tamamen serileşir. Yazmaya çalışmıyorsanız sade okumalar (veya optimistic locking)
  kullanın.

## Optimistic ve Pessimistic karşılaştırması

| | Optimistic (`@Version`) | Pessimistic (`FOR UPDATE`) |
|---|---|---|
| Çakışma yönetimi | Sonradan, exception ile tespit edilir | Bloklayarak önlenir |
| DB lock'u alınır mı? | Hayır | Evet, transaction süresince |
| Contention davranışı | Hızlı başarısız olur, retry mantığı gerekir | Bloklar/sıraya sokar, retry gerekmez |
| En uygun olduğu durum | Düşük/orta contention, kısa transaction'lar | Aynı satır(lar)da yüksek contention, veya retry'nin güvenli olmadığı durumlar |
| Deadlock riski | Yok (lock tutulmaz) | Var, lock sırası tutarsızsa |
| Düşük contention altında throughput | Yüksek (bloklama yok) | Daha düşük (koşulsuz lock overhead'i) |
| Hot bir row altında throughput | Çökebilir (çoğu deneme çakışır ve retry eder) | Genellikle daha iyi (düzenli kuyruk, boşa retry yok) |

## Sık Sorulan Mülakat Soruları

- **Q:** `FOR UPDATE` gerçekten satırı kilitler mi? — Evet, gerçek bir DB-seviyesi row lock
  (PostgreSQL + Hibernate 6'da genellikle `FOR NO KEY UPDATE` varyantı olarak).
- **Q:** Pessimistic lock transaction dışında alınabilir mi? — Hayır,
  `TransactionRequiredException` fırlatılır (Spring Data repository üzerinden çağrılırsa,
  Spring'in exception translation'ı bunu `InvalidDataAccessApiUsageException`'a sarar).
- **Q:** İki pessimistic lock nasıl deadlock'a yol açar? — Farklı sırada birden fazla satır
  kilitlenirse; çözüm deterministic lock ordering.
- **Q:** Ne zaman optimistic, ne zaman pessimistic? — Düşük/orta contention ve kısa
  transaction'larda optimistic; yüksek contention'da veya retry'nin güvenli olmadığı
  senaryolarda pessimistic.

## 30 Saniyelik Mülakat Cevabı

"Pessimistic locking'i gerçek Postgres'te test ettim: `findByIdForUpdate` gerçekten `SELECT
... FOR UPDATE` üretiyor ve ikinci transaction bu satıra dokunmaya çalıştığında fiziksel
olarak bekliyor - ölçtüğüm bekleme süresi birinci transaction'ın lock'u tuttuğu süreyle
neredeyse birebir eşleşti. Ayrıca gerçek bir DB-seviyesi deadlock da ürettim: iki transaction
A ve B satırlarını ters sırada kilitlemeye çalışınca, Postgres'in kendi deadlock detector'ı
bunu yakaladı ve taraflardan birini 'deadlock detected' hatasıyla iptal etti."

## Takip Soruları

- `PESSIMISTIC_READ` ile `PESSIMISTIC_WRITE` arasındaki fark nedir?
- Lock timeout (`javax.persistence.lock.timeout`) ne işe yarar?
- Connection pool boyutu pessimistic locking stratejisini nasıl etkiler?
