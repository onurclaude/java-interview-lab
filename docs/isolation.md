# Transaction Isolation Level'ları

Kod: `com.interviewlab.transaction.isolation.*`

**Birincil öğrenme arayüzü — Postman + IntelliJ debugger:**
```http
POST /api/labs/isolation/reset
POST /api/labs/isolation/non-repeatable-read/read-committed
POST /api/labs/isolation/non-repeatable-read/repeatable-read
POST /api/labs/isolation/phantom-read/read-committed
POST /api/labs/isolation/phantom-read/repeatable-read
POST /api/labs/isolation/dirty-read/read-uncommitted
```
Tam breakpoint sırası için **`docs/DEBUGGER_LABS.md`** "ISOLATION" bölümlerine bakın.
`IsolationLevelsTest`, AYNI davranışın otomatik regresyon kanıtıdır — ikincildir, birincil değil.

# Problem

Her isolation level'ın, SQL standardının soyut tanımı değil, PostgreSQL'in gerçekte
GERÇEKTEN ne engellediği nedir?

## Neden persistence context yerine JDBC/JPA?

`IsolationAnomalyLab`, `EntityManager.find()` yerine ham `JdbcTemplate` çağrıları kullanır.
Eğer JPA kullansaydı, first-level cache, bir transaction içindeki ikinci `find()`'da *veritabanını
hiç yeniden sorgulamadan* AYNI managed instance'ı döndürürdü — bu da bu lab'ın göstermek için
var olduğu tam o anomalileri (non-repeatable read, phantom read) sessizce gizlerdi. Bu, başlı
başına iyi bir mülakat noktası: isolation-level anomalileri, *veritabanının* size neyi
gösterdiğiyle ilgilidir; JPA'nın kendi cache'i bunları veritabanının gerçek davranışından
bağımsız olarak maskeleyebilir.

## Non-repeatable read

Aynı transaction içinde, arada başka bir transaction'ın commit edilmiş bir UPDATE'i olan,
aynı satırın iki SELECT'i.

- **READ_COMMITTED**: `shouldExhibitNonRepeatableReadUnderReadCommitted` — ilk okuma 100,
  ikinci okuma 200. Her statement, commit edilmiş veriye taze bir bakış alır.
- **REPEATABLE_READ**: `shouldPreventNonRepeatableReadUnderRepeatableRead` — her iki okuma da
  100 döner. Postgres'in REPEATABLE READ'i, tüm transaction için TEK BİR snapshot alır.

## Phantom read

Arada, predicate ile eşleşen yeni bir satırı commit eden başka bir transaction olan,
tekrarlanan bir range sorgusu (`count(*) where balance > 75`).

- **READ_COMMITTED**: `shouldExhibitPhantomReadUnderReadCommitted` — count 0 → 1 olur.
  Yeni commit edilen satır görünür hale gelir: bir phantom.
- **REPEATABLE_READ**: `shouldPreventPhantomReadUnderRepeatableReadOnPostgres` — count 0 → 0
  kalır. **Bu Postgres'e özgüdür ve SQL standardından daha güçlüdür**; standart, REPEATABLE
  READ altında phantom read'lere açıkça izin verir. Postgres, REPEATABLE READ'i tam snapshot
  isolation olarak implement eder, bu da tesadüfen phantom'ları da engeller - bunu başka bir
  veritabanında varsaymayın.

## Dirty read

Uçuşta, eşzamanlı, hâlâ commit edilmemiş bir yazma ile `READ_UNCOMMITTED` üzerinde denenmiştir.

`shouldNeverExhibitDirtyReadRegardlessOfReadUncommittedRequest`: okuyucu ESKİ, commit edilmiş
değeri (100) görür, asla commit edilmemiş 999'u görmez. **Bu test, kod dikkatli olduğu için
değil, veritabanına özgü bir sebeple geçer**: PostgreSQL, READ UNCOMMITTED'i hiç implement
etmez - onu istemek sessizce size onun yerine READ COMMITTED verir. Dirty read'ler,
Postgres'te, istenen isolation level ne olursa olsun, yapısal olarak imkansızdır.

## Lost update — sadece isolation level ile önlenir, `@Version` gerekmez

`shouldRejectSecondCommitWithSerializationFailureUnderRepeatableRead`: iki REPEATABLE_READ
transaction ikisi de balance=100 okur, ikisi de `100 - 10`'u hesaplar, ikisi de commit etmeye
çalışır. Postgres, ikinci commit edenin snapshot'ının çakışan bir değişikliğe göre eski
olduğunu tespit eder ve onun `COMMIT`'ini bir **serialization failure** (SQLState `40001`,
"could not serialize access due to concurrent update") ile reddeder — kaybeden, tüm
transaction'ı yeniden denemek zorundadır. Bu, `@Version` tabanlı optimistic locking'den
(docs/optimistic-locking.md) *farklı* bir mekanizmadır: veritabanının kendi MVCC/snapshot
makinesi, hiçbir application-level version sütunu olmadan, isolation-level katmanında
tespiti yapar.

## Her level'ın neyi engellediği/engellemediği ve maliyeti

| Level | Dirty read | Non-repeatable read | Phantom read | Postgres notları |
|---|---|---|---|---|
| READ_UNCOMMITTED | engellenir (Postgres bunu gerçekten hiç sunmaz) | mümkün | mümkün | sessizce == READ_COMMITTED |
| READ_COMMITTED (Postgres varsayılanı) | engellenir | mümkün | mümkün | en ucuzu; iyi bir varsayılan |
| REPEATABLE_READ | engellenir | engellenir | **engellenir** (Postgres'e özgü) | write-write çakışmalarında serialization failure'a yol açabilir |
| SERIALIZABLE | engellenir | engellenir | engellenir | en güçlüsü; contention altında en yüksek abort/retry oranı |

**Hangisi ne zaman kullanılır:** neredeyse her şey için READ_COMMITTED (Postgres'in
varsayılanı, iyi throughput). Açık bir `@Version` veya `SELECT ... FOR UPDATE` olmadan
korumak istediğiniz belirli bir read-modify-write dizisi için REPEATABLE_READ/SERIALIZABLE —
kaybeden transaction'ın retry etmesi gerektiğini kabul ederek.

## Sık Sorulan Mülakat Soruları

- **Q:** READ_UNCOMMITTED Postgres'te gerçekten var mı? — Hayır, sessizce READ_COMMITTED'e
  yükseltilir.
- **Q:** REPEATABLE_READ phantom read'i engeller mi? — Standart SQL'e göre hayır, ama
  Postgres'in snapshot isolation implementasyonu evet engeller.
- **Q:** Lost update'i sadece isolation level ile (Version olmadan) önleyebilir miyim? —
  Evet, REPEATABLE_READ/SERIALIZABLE ile, ama kaybeden taraf bir serialization failure alır
  ve retry etmesi gerekir.

## 30 Saniyelik Mülakat Cevabı

"İzolasyon seviyelerini gerçek PostgreSQL container'ında test ettim. READ_COMMITTED'de aynı
transaction içinde iki kere okuduğumda, aradaki commit'i görüyordum (non-repeatable read).
REPEATABLE_READ'e geçince görmüyordum — üstelik phantom read'i de engelliyordu, ki bu SQL
standardının ötesinde bir garanti, çünkü Postgres REPEATABLE_READ'i tam snapshot isolation
olarak implement ediyor. En ilginç kısmı: iki REPEATABLE_READ transaction aynı satırı
okuyup update etmeye çalıştığında, DB @Version falan olmadan ikinci commit'i 'could not
serialize access' hatasıyla reddetti — yani lost update'i isolation seviyesinin kendisi de
önleyebiliyor."

## Takip Soruları

- SERIALIZABLE ile REPEATABLE_READ arasındaki fark Postgres'te nedir (write skew)?
- Serialization failure alan bir transaction'ı nasıl retry edersiniz (Spring'de)?
- Isolation level'ı yükseltmenin throughput maliyeti nasıl ölçülür?
