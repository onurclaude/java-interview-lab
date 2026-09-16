# Transaction Rollback ve Proxy / Self-Invocation Problemi

Kod: `com.interviewlab.transaction.rollback.*`, `com.interviewlab.transaction.propagation.*`

**Birincil öğrenme arayüzü — Postman + IntelliJ debugger + DBeaver:**
```http
POST /api/labs/transaction/reset
POST /api/labs/transaction/bad
POST /api/labs/transaction/good
POST /api/labs/propagation/requires-new/bad
POST /api/labs/propagation/requires-new/good
```
`RollbackBehaviorTest`, `PropagationSelfInvocationTest` — AYNI davranışın otomatik regresyon
kanıtıdır — ikincildir, birincil değil.

# Problem

`@Transactional`, *her* exception'da rollback yapar mı? Ve aynı sınıftan başka bir
`@Transactional` metod çağırmak neden bazen "çalışmıyor"?

## Hatalı Kod — checked exception, rollback yok

```java
// com.interviewlab.transaction.rollback.bad.CheckedExceptionNoRollbackService
@Transactional
public void debitThenFailWithCheckedException(Long accountId, BigDecimal amount) throws SimulatedCheckedFailureException {
    Account account = accountRepository.findById(accountId).orElseThrow();
    account.debit(amount);
    throw new SimulatedCheckedFailureException("Downstream validation failed");
}
```

## Neden Yanlış

Spring'in *varsayılan* rollback kuralı (EJB konvansiyonundan miras): unchecked
`RuntimeException`/`Error`'da rollback yapar, `rollbackFor` aksini söylemedikçe checked
exception'larda **commit** eder. Checked bir exception'ın "gürültülü şekilde başarısız
olması", transaction'ın rollback olduğu anlamına gelmez.

## Gerçekte Ne Oluyor

`RollbackBehaviorTest.shouldCommitDespiteCheckedExceptionByDefault()`: debit, fırlatılan
exception'a rağmen **hayatta kalır** — bakiye, metod "başarısız olmasına" rağmen 100.00'dan
70.00'a düşer. `@Transactional(rollbackFor = Exception.class)` eklendiğinde aynı test
(`shouldRollbackWhenRollbackForConfiguredForCheckedException`), bakiyenin 100.00'a geri
döndüğünü gösterir. Unchecked bir exception hiçbir ekstra konfigürasyon olmadan rollback
yapar (`shouldRollbackOnUncheckedExceptionByDefault`).

## Self-invocation: diğer klasik tuzak

```java
// com.interviewlab.transaction.propagation.bad.SelfInvocationPaymentService
@Transactional
public void processPayment(Long accountId, BigDecimal amount, String auditMessage) {
    ...
    this.audit(auditMessage);           // SELF-INVOCATION
    throw new OrderProcessingException("payment gateway timeout");
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void audit(String message) { ... }
```

`@Transactional`, proxy tabanlı AOP'dir: Spring, bean'i, **bean'in dışından gelen**
çağrıların etrafında transaction başlatan/katılan/commit eden bir proxy ile sarar.
`this.audit(...)`, o proxy'ye asla ulaşmayan sıradan bir JVM metod çağrısıdır — `REQUIRES_NEW`
sessizce göz ardı edilir, `audit()` sadece `processPayment()` ile aynı transaction içinde
çalışır ve onunla birlikte rollback olur
(`PropagationSelfInvocationTest.shouldNotApplyRequiresNewDuringSelfInvocation`).

## Doğru Kod

`audit()`'i farklı bir bean'e taşıyın (`com.interviewlab.transaction.propagation.good.AuditService`)
ve inject edilen bir referans üzerinden çağırın — artık çağrı gerçek proxy'den geçer, ve
`REQUIRES_NEW`, outer olan rollback olmadan ÖNCE commit eden, gerçekten bağımsız bir
transaction açar (`shouldCommitRequiresNewTransactionEvenWhenOuterTransactionRollsBack`).

```
Order Transaction (REQUIRED)
   + debit account          -> rolls back with the order
   + auditService.audit()   -> REQUIRES_NEW, commits independently, SURVIVES the rollback
```

**Bu neden tehlikeli olabilir:** REQUIRES_NEW hemen commit eder ve caller'ın sonraki
rollback'i tarafından asla geri alınamaz. Onu gerçek bir side effect'i olan bir şey için
kullanmak (gerçek bir kart çekimi, idempotent olmayan bir webhook), çevreleyen business
transaction rollback olurken side effect'in commit edilmiş kalmasına yol açabilir — bu bir
düzeltme değil, bir tutarsızlıktır.

Bu tam proxy mekanizması — ve tam self-invocation hatası — `@Async`'e (docs/async.md) ve
herhangi bir custom `@Aspect`'e (docs/aop.md) de uygulanır: hepsi, aynı kör noktaya sahip
Spring AOP proxy'leridir.

## Trade-off'lar

| Yaklaşım | Artıları | Eksileri |
|---|---|---|
| Varsayılan rollback kuralları | Çoğu REST/service koduyla eşleşir (unchecked fırlat, rollback bekle) | Checked exception'lar için sessiz bir tuzak |
| `rollbackFor` | Açık, checked exception'lar için doğru | Unutulması kolay; ihtiyacı olan her metoda eklenmesi gerekir |
| REQUIRES_NEW için ayrı bean | Gerçekten çalışır | Sadece bir proxy sınırlamasını dolanmak için ekstra bir bean/interface |

## Sık Sorulan Mülakat Soruları

- **Q:** `@Transactional` her exception'da rollback yapar mı? — Hayır, sadece unchecked'te,
  varsayılan olarak.
- **Q:** Self-invocation neden proxy'yi atlar? — Proxy sadece dışarıdan gelen çağrıları
  intercept eder; `this.` çağrısı JVM seviyesinde direkt metod çağrısıdır.
- **Q:** `REQUIRES_NEW` her zaman güvenli mi? — Hayır, geri alınamaz side effect'ler için
  tehlikeli olabilir.

## 30 Saniyelik Mülakat Cevabı

"Bir payment+audit senaryosunda self-invocation'ı bizzat gördüm: `this.audit()` ile REQUIRES_NEW
annotate edilmiş metodu çağırdığımda, audit kaydı outer transaction rollback olunca kayboldu
— çünkü proxy hiç devreye girmemişti. Audit'i ayrı bir bean'e taşıyıp gerçek proxy üzerinden
çağırınca, outer transaction rollback olsa bile audit kaydı DB'de kaldı."

## Takip Soruları

- `rollbackFor` yerine `noRollbackFor` ne zaman kullanılır?
- CGLIB proxy vs JDK dynamic proxy farkı self-invocation'ı etkiler mi? (Hayır — ikisi de aynı
  proxy sınırlamasına sahip.)
- `AopContext.currentProxy()` self-invocation'ı "çözer" mi, ve neden genelde önerilmez?
