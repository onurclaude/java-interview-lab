# Transaction Propagation

Kod: `com.interviewlab.transaction.propagation.*` — Testler: `PropagationShowcaseTest`, `PropagationSelfInvocationTest`

# Problem

REQUIRED, REQUIRES_NEW, NESTED ve diğer dört propagation tipi bir slayt sunumunda hepsi
benzer görünür. Gerçek bir transaction/connection'a gerçekte farklı olarak ne yaparlar?

## REQUIRED (varsayılan)

Varsa caller'ın transaction'ına katılır; yoksa yeni bir tane başlatır. Aksi belirtilmedikçe
bu projede her yerde kullanılır.

## REQUIRES_NEW

Caller'ın transaction'ını (ve persistence context'i/connection'ını) **askıya alır**,
tamamen bağımsız bir tane başlatır, onu kendi başına commit/rollback eder, sonra caller'ınkine
geri döner. Tam audit-log örneği ve self-invocation tuzağı için docs/transactions.md'ye
bakın.

## NESTED — teoride vs. gerçekte (JPA/Hibernate ile pratikte KULLANILAMAZ)

```java
// com.interviewlab.transaction.propagation.demo.NestedStepService
@Transactional(propagation = Propagation.NESTED)
public void attemptOverdraft(Long accountId) {
    Account account = accountRepository.findById(accountId).orElseThrow();
    account.debit(new BigDecimal("100000.00"));
    throw new IllegalStateException("business rule violation");
}
```

**Teoride** REQUIRES_NEW'in aksine, NESTED hiçbir şeyi askıya **almaz** — bir JDBC
**savepoint** kullanarak caller ile *aynı* fiziksel connection/transaction içinde çalışır.
Başarısız olursa, sadece o savepoint'ten sonraki iş rollback olur; outer transaction
rollback-only olarak işaretlenmez.

**Bu proje bunu gerçek Postgres'e karşı test ettiğinde, çok daha temel bir gerçek ortaya
çıktı: yukarıdaki metodun gövdesi HİÇBİR ZAMAN ÇALIŞMIYOR.** `nestedStepService.attemptOverdraft(...)`
çağrısının kendisi, transaction proxy'si savepoint oluşturmaya çalışırken şu istisnayla
başarısız oluyor:

```
org.springframework.transaction.NestedTransactionNotSupportedException:
JpaDialect does not support savepoints - check your JPA provider's capabilities
```

Bunun iki farklı, birbirinden bağımsız nedeni var, ve bu proje ikisini de ayırt ediyor:

1. **`JpaTransactionManager.nestedTransactionAllowed` varsayılan olarak `false`'tur** —
   bu açıkça `true` yapılmadan (`NestedTransactionManagerConfig`), NESTED şu farklı
   mesajla başarısız olur: `"Transaction manager does not allow nested transactions by
   default"`. Bu, GEREKLİ ama YETERLİ OLMAYAN bir düzeltmedir.
2. **Asıl (ve giderilemez) neden:** flag `true` yapılsa bile, Spring'in kendi
   `HibernateJpaDialect.beginTransaction()` implementasyonunun döndürdüğü transaction-data
   nesnesi (`HibernateJpaDialect.SessionTransactionData`) `SavepointManager` arayüzünü hiç
   UYGULAMAZ. Spring'in `JpaTransactionManager$JpaTransactionObject.setTransactionData(...)`'ı
   yalnızca `transactionData instanceof SavepointManager` ise `EntityManagerHolder`'a bir
   savepoint manager kaydeder; `HibernateJpaDialect` için bu koşul asla sağlanmaz, bu yüzden
   `EntityManagerHolder.getSavepointManager()` her zaman `null`'dur ve NESTED her denemede
   `"JpaDialect does not support savepoints"` ile başarısız olur. Bu, Spring 6.1.x'in kendi
   bytecode'u decompile edilerek doğrulanmıştır (`javap`) — bir varsayım ya da makale özeti
   değil.

**Sonuç:** `Propagation.NESTED`, düz Spring Data JPA + Hibernate ile pratikte kullanılamaz.
Gerçek savepoint desteği, `ConnectionHolder`'ın gerçekten `SavepointManager` uyguladığı saf
JDBC tabanlı bir `DataSourceTransactionManager` gerektirir (ör. JdbcTemplate/MyBatis tabanlı
kod) — Spring Data JPA + Hibernate kombinasyonu bunun kapsamı dışındadır.
`OuterOrderWithNestedStepService`, bu istisnayı yakalayıp devam eder; nested adımın planladığı
debit hiçbir zaman uygulanmadığı için (gövde hiç çalışmadığından) geri alınacak bir "hayalet
değişiklik" de yoktur — bu, bu lab'ın ilk tasarımının varsaydığı `entityManager.refresh()`
ihtiyacını da ortadan kaldırır (bkz. `shouldNeverExecuteNestedStepBodyBecauseHibernateJpaDialectHasNoSavepointSupport`).

## Diğer dördü, kısaca

| Propagation | Davranış | Demo |
|---|---|---|
| `SUPPORTS` | Varsa bir transaction'a katılır; yoksa transaction olmadan çalışır. Asla hata vermez. | `isTransactionActiveUnderSupports` |
| `MANDATORY` | Mevcut bir transaction gerektirir; yoksa `IllegalTransactionStateException` fırlatır. | `isTransactionActiveUnderMandatory` |
| `NOT_SUPPORTED` | Mevcut herhangi bir transaction'ı askıya alır; transaction olmadan çalışır. | `isTransactionActiveUnderNotSupported` |
| `NEVER` | Bir transaction AKTİFSE `IllegalTransactionStateException` fırlatır. | `isTransactionActiveUnderNever` |

Dördü de `PropagationShowcaseTest`'te `TransactionSynchronizationManager.isActualTransactionActive()`
ile kanıtlanmıştır; self-invocation tuzağına düşmeden "aktif bir transaction içinden
çağrılmış" durumunu simüle etmek için testten `TransactionTemplate` üzerinden yönlendirilir.

## Trade-off'lar

- **REQUIRED**: makul varsayılan; aksi için özel bir sebebiniz olmadıkça kullanın.
- **REQUIRES_NEW**: caller'ın sonucundan gerçek izolasyon, ikinci bir fiziksel
  connection/transaction ve "zaten commit oldu, geri alamazsın" riski pahasına.
- **NESTED**: teoride REQUIRES_NEW'den daha ucuzdur (ikinci bir connection yok), ama pratikte
  düz Spring Data JPA + Hibernate ile HİÇ ÇALIŞMAZ — `HibernateJpaDialect`'in transaction
  data'sı `SavepointManager` uygulamadığı için `nestedTransactionAllowed=true` bile yeterli
  değildir. Sadece saf JDBC tabanlı (`DataSourceTransactionManager`) kodda gerçekten
  kullanılabilir bir seçenektir.

## Sık Sorulan Mülakat Soruları

- **Q:** REQUIRES_NEW ile NESTED arasındaki temel fark nedir? — REQUIRES_NEW ayrı bir
  fiziksel transaction/connection açar (suspend+resume); NESTED (teoride) aynı connection
  üzerinde bir savepoint kullanır.
- **Q:** NESTED, JPA/Hibernate ile çalışır mı? — Hayır, pratikte çalışmaz.
  `HibernateJpaDialect`, `SavepointManager` uygulayan bir transaction-data nesnesi
  döndürmediği için her NESTED denemesi `NestedTransactionNotSupportedException: "JpaDialect
  does not support savepoints"` ile başarısız olur — `nestedTransactionAllowed=true` doğru
  ayarlanmış olsa bile.
- **Q:** MANDATORY ne zaman kullanılır? — Bir metodun HİÇBİR ZAMAN kendi başına, transaction
  olmadan çağrılmaması gerektiğini garantilemek için (örn. bir "adım" metodu).

## 30 Saniyelik Mülakat Cevabı

"NESTED'i bir overdraft senaryosunda test ettim ve beklediğimden farklı, ama daha değerli bir
şey öğrendim: `nestedTransactionAllowed=true` ayarlasam bile, nested metodun gövdesi hiçbir
zaman çalışmıyordu — proxy, transaction başlamadan önce bir savepoint oluşturmaya çalışırken
başarısız oluyordu. Spring'in bytecode'unu decompile ettiğimde kök nedeni buldum:
`HibernateJpaDialect`'in döndürdüğü transaction-data nesnesi `SavepointManager` arayüzünü hiç
uygulamıyor, bu yüzden Spring'in `EntityManagerHolder`'ı bir savepoint manager'a asla sahip
olmuyor. Yani NESTED, düz JPA/Hibernate uygulamalarında pratikte kullanılamaz bir propagation
modudur — çoğu insanın fark etmediği bir gerçek."

## Takip Soruları

- NESTED, saf JDBC tabanlı bir `DataSourceTransactionManager` ile neden gerçekten çalışır?
- REQUIRES_NEW içinde bir deadlock riski var mı (iki connection aynı satırları farklı sırada
  kilitlerse)?
