# Persistence Context, Dirty Checking ve Flush

Kod: `com.interviewlab.persistence.*`

**Birincil öğrenme arayüzü — Postman + IntelliJ debugger + DBeaver:**
```http
POST /api/labs/persistence/reset
POST /api/labs/persistence/bad
POST /api/labs/persistence/good
```
Tam breakpoint sırası için **`docs/DEBUGGER_LABS.md`** "PERSISTENCE CONTEXT" bölümüne bakın.
`PersistenceLifecycleServiceTest`, AYNI davranışın otomatik regresyon kanıtıdır — ikincildir,
birincil değil.

# Problem

"Aynı transaction içinde bir entity'yi kaydedip, güncelleyip ve silebilir miyiz? Bu her
zaman 3 ayrı SQL (INSERT/UPDATE/DELETE) üretir mi?"

## Hatalı Kod

```java
// com.interviewlab.persistence.bad.MutatingDetachedEntityService
public Long renameCustomerAssumingDirtyChecking(String name, String email, String newName) {
    Customer customer = new Customer(name, email);
    customer = customerRepository.save(customer);   // save()'s own transaction opens AND closes here
    customer.changeName(newName);                    // mutating a now-DETACHED entity
    return customer.getId();                         // rename is silently lost
}
```

## Neden Yanlış

`JpaRepository.save()`'in kendisi `@Transactional`'dır. Sarmalayan bir transaction yoksa,
Spring sadece `save()` için bir tane açar ve `save()` return ettiği anda onu (ve içindeki
persistence context'i) kapatır. Bu metodun hâlâ elinde tuttuğu `Customer` referansı artık
**detached**'tır: aynı Java field'ları, ama snapshot yok, persistence context yok, dirty
checking yok.

## Gerçekte Ne Oluyor

Dirty checking sadece açık bir persistence context içindeki **managed** entity'lerde çalışır.
Detached bir entity artık sıradan bir Java nesnesinden ibarettir - onu mutate etmek
veritabanına sessizce hiçbir şey yapmaz.

## Nasıl Reproduce Edilir

`PersistenceLifecycleServiceTest.shouldSilentlyDropChangeWhenMutatingDetachedEntity()`,
yukarıdaki metodu çağırır, satırı yeniden yükler ve ismin hâlâ *eski* değer olduğunu assert
eder.

## Doğru Kod

```java
// com.interviewlab.persistence.good.PersistenceLifecycleService
@Transactional
public void renameViaDirtyCheckingOnly(Long customerId, String newName) {
    Customer managed = entityManager.find(Customer.class, customerId);
    managed.changeName(newName);
    // no save() call needed - commit's flush sees the diff and issues UPDATE
}
```

Entity yaşam döngüsü şudur: **transient** (`new Customer(...)`, identity yok, JPA tarafından
bilinmiyor) → **managed** (`save()`/`persist()`/`find()`, takip ediliyor, L1 cache'de) →
**detached** (persistence context kapandı, örn. transaction bitti) → **removed**
(hâlâ managed olan bir entity'de `delete()`/`remove()` çağrıldı, flush'ta silinmek üzere
planlandı).

### "3 SQL statement" cevabı: flush zamanlamasına bağlı (ama sezgisel varsayımdan daha ince)

`Customer`, `GenerationType.SEQUENCE` kullanır (`IDENTITY` değil): kimlik (id), `save()`
sırasında hemen bir `nextval(...)` round trip'iyle alınır, ama asıl `INSERT` satırı flush'a
kadar ertelenir. `PersistenceLifecycleService.persistMutateAndRemoveWithoutFlush()`, arada
**hiç** explicit flush olmadan save → mutate → delete yapar. Birçok kaynak burada Hibernate'in
`ActionQueue`'sunun hâlâ bekleyen `INSERT`'i, entity hemen sonra remove edildiği için iptal
edeceğini ve sıfır SQL üretileceğini iddia eder - **bu proje bunu gerçek Postgres'e karşı test
ettiğinde bu iddia doğrulanmadı**: gözlemlenen gerçek davranış, commit anında hem `INSERT` hem
`DELETE`'in normal şekilde gönderilmesidir (`UPDATE` yine de hiç üretilmez, çünkü dirty
checking `REMOVED` entity'leri atlar). Net sonuç: **bir INSERT, bir DELETE, sıfır UPDATE** -
`shouldStillIssueInsertAndDeleteEvenWhenRemovedBeforeFirstFlush()` ile kanıtlanmıştır. Asıl
ders bir SQL sayısı değil: bu tür bir "optimizasyon var mı yok mu" sorusuna asla bir
yorumdan/makale özetinden değil, gerçek SQL log'undan cevap verin.

Bunun yerine `save()`'den sonra açıkça `entityManager.flush()` çağırın
(`persistFlushMutateAndRemove()`), ve `INSERT` gerçekten olur — ama *sonrasındaki* mutasyon
hâlâ hiçbir zaman kalıcı hale gelmez: `delete()` entity'yi `REMOVED` olarak işaretledikten
sonra, commit-time flush'ın dirty-checking aşaması `REMOVED` entity'leri tamamen atlar, bu
yüzden hiçbir `UPDATE` hiç fırlatılmaz. Net SQL: bir `INSERT`, bir `DELETE`, **sıfır**
`UPDATE` — `shouldNeverIssueUpdateWhenEntityIsRemovedAfterExplicitFlush()` ile kanıtlanmıştır.

### save() ile flush() ile saveAndFlush()

- `save()`, bir persist action'ı planlar; SQL'in *şimdi* çalışıp çalışmayacağı ID generation
  stratejisine ve bir flush'ın gerçekleşip gerçekleşmediğine bağlıdır.
- `flush()`, persistence context'i veritabanıyla *hemen şimdi* senkronize olmaya zorlar.
- `saveAndFlush()` ikisini tek bir çağrıda yapar. Bunu bir döngüde çağırmak
  (`com.interviewlab.persistence.bad.SaveAndFlushInLoopService`), **bir** flush olabilecek
  şeyi (potansiyel olarak JDBC-batch'lenmiş) **N** ayrı flush'a dönüştürür — Hibernate
  `Statistics.getFlushCount()` üzerinden `shouldFlushOnceForBatchButNTimesForSaveAndFlushLoop()`
  ile kanıtlanmıştır.

## Trade-off'lar

| | Explicit flush | Explicit flush yok |
|---|---|---|
| SQL hatası ortaya çıkar | Hemen, flush çağrısında | Sadece commit'te |
| Round trip'ler | Flush çağrısı başına bir | Bir tane, commit'te batch'lenmiş |
| Kullanım senaryosu | Yazma hatalarını şimdi görmeniz gerekiyorsa, veya bekleyen değişikliklere bağlı native SQL çalıştırıyorsanız | Varsayılan — Spring/Hibernate'in şeyleri batch'lemesine izin verin |

## Sık Sorulan Mülakat Soruları

- **Q: Optimistic locking nedir?** *(çapraz referans: bkz. docs/optimistic-locking.md — bu
  farklı bir konu; persistence context'i locking ile karıştırmayın.)*
- **Q: save() çağrıldığında SQL hemen gider mi?** Bağlıdır: ID generation stratejisi
  (`IDENTITY` hemen INSERT gerektirir, `SEQUENCE` erteleyebilir) ve araya bir flush girip
  girmediği.
- **Q: Flush ne zaman olur?** Commit'te otomatik olarak, `flush()` explicit çağrısında, veya
  (varsayılan `FlushModeType.AUTO` ile) o persistence context'i etkileyen bir JPQL/Criteria
  sorgusu çalıştırılmadan hemen önce.
- **Q: Dirty checking nasıl çalışır?** Hibernate, entity `managed` olduğu anda field
  değerlerinin bir snapshot'ını alır; flush anında güncel değerlerle snapshot'ı karşılaştırır,
  fark varsa UPDATE üretir.
- **Q: Detached bir entity'i mutate edersem ne olur?** Hiçbir şey — dirty checking sadece
  managed entity'lerde çalışır.

## 30 Saniyelik Mülakat Cevabı

"Bunu bir Customer entity'siyle denedim: aynı transaction içinde save → rename → delete
yaptığımda, hiç flush çağırmadan bile Hibernate'in INSERT+DELETE çiftini iptal ETMEDİĞİNİ
gördüm — SQL log'da ikisi de commit anında normal şekilde çıktı, sadece beklediğim gibi
sıfır statement değil. Explicit flush() eklediğimde INSERT beklenildiği gibi gerçekten gitti,
ama sonrasındaki rename hiçbir zaman DB'ye yazılmadı, çünkü entity flush anında zaten REMOVED
durumundaydı ve dirty checking REMOVED entity'ler için UPDATE üretmiyor. Buradaki asıl ders,
'X optimizasyonu var' diye bir yerde okuduğunuz şeye güvenmemek — gerçek SQL log'una bakmak."

## Takip Soruları

- `GenerationType.IDENTITY`, `SEQUENCE`'a göre flush zamanlamasını nasıl farklılaştırır?
- `saveAndFlush()` ne zaman gerçekten gereklidir?
- Persistence context'in first-level cache ile ilişkisi nedir?
