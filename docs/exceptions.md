# Exception Hiyerarşisi, @RestControllerAdvice ve Rollback Kuralları

Kod: `com.interviewlab.exception.*` — Testler: `ExceptionHierarchyTest`

## Hiyerarşi

```
Throwable
  Error                  - JVM-level problems (OutOfMemoryError); don't catch, can't recover
  Exception
      RuntimeException   - unchecked; not declared, not required to be caught
      (checked exceptions - everything else under Exception)
```

Bu projenin kendi hiyerarşisi, kasıtlı olarak tamamen UNCHECKED'dir (aşağıya bakın):

```
BusinessException extends RuntimeException
  PaymentException extends BusinessException
    InsufficientBalanceException extends PaymentException
```

## Neden unchecked?

Checked bir exception, call stack'teki her caller'ı ya onu yakalamaya ya da `throws` deklare
etmeye zorlar — pratikte bu, geliştiricilere sadece compiler'ı tatmin etmek için
`catch (Exception e) {}` yazmayı öğretir (aşağıdaki yutma örneğine bakın). Ayrıca Spring'in
varsayılan `@Transactional` rollback kuralıyla da kötü etkileşir; bu kural, `rollbackFor`
aksini söylemedikçe checked exception'larda rollback YAPMAZ (docs/transactions.md). Unchecked
business exception'lar her iki sorunu da önler.

## Hatalı Kod #1 — yutma

```java
// com.interviewlab.exception.bad.SwallowingExceptionService
try {
    paymentGatewayClient.charge(cardToken, amount);
    return true;
} catch (PaymentGatewayCheckedException e) {
    // nothing - no log, no rethrow
}
return true; // caller believes the charge succeeded
```

`shouldSilentlySwallowFailureAndReturnNormally()`: gateway gerçekten başarısız oldu; caller
yine de `true` alıyor.

## Hatalı Kod #2 — cause'u kaybetme

```java
// com.interviewlab.exception.bad.LossyRethrowService
} catch (PaymentGatewayCheckedException e) {
    throw new RuntimeException("payment failed"); // 'e' discarded
}
```

`shouldLoseOriginalCauseWithLossyRethrow()`: ortaya çıkan exception'da `getCause()` `null`
döner — orijinal exception'ın taşıdığı decline kodu / diagnostic detay sonsuza dek kaybolur.

## Doğru Kod — sarmala, cause'u koru

```java
// com.interviewlab.exception.good.WrappingExceptionService
} catch (PaymentGatewayCheckedException e) {
    throw new InsufficientBalanceException("payment failed for card token=" + cardToken, e);
}
```

`shouldPreserveCauseChainAndProduceCorrectHierarchyWithWrapping()`: fırlatılan exception,
`RuntimeException`'a kadar `instanceof` zincirini geçer, VE `getCause()` hâlâ orijinal
`PaymentGatewayCheckedException`'ı döndürür — hiçbir şey kaybolmaz, hem caller'ların
yakalayabileceği anlamlı bir tip hem de log'lar için tam diagnostic zincir korunur.

## @RestControllerAdvice

```java
@ExceptionHandler(InsufficientBalanceException.class)
public ResponseEntity<ErrorResponse> handleInsufficientBalance(InsufficientBalanceException ex) {
    return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(new ErrorResponse("INSUFFICIENT_BALANCE", ex.getMessage()));
}
@ExceptionHandler(PaymentException.class) ...
@ExceptionHandler(BusinessException.class) ...
```

Her controller metodunda bir `try/catch` yerine, tüm hiyerarşiyi HTTP response'lara tek bir
yerde eşler, en-spesifikten-başlayarak eşleştirilir —
`shouldMapInsufficientBalanceExceptionToHttp402ViaControllerAdvice()`, spesifik handler'ın
daha genel `PaymentException`/`BusinessException` olanlara karşı kazandığını doğrular.

## Checked ile unchecked ve `@Transactional` rollback

Tam `rollbackFor` örneği için docs/transactions.md'ye bakın — kısa versiyon: unchecked
varsayılan olarak rollback yapar, checked yapmaz, meğer ki `rollbackFor` aksini söylesin. Bu
tam olarak neden bu projenin kendi business exception'larının unchecked olduğunun sebebidir:
"bu neden rollback olmadı" tarzı sürprizlerin tüm bir sınıfını ortadan kaldırır.

## Sık Sorulan Mülakat Soruları

- **Q:** Checked ve unchecked exception arasındaki fark nedir? — Checked, `throws` ile
  deklare edilmek zorunda ve compiler tarafından zorlanır; unchecked zorlanmaz.
- **Q:** Neden business exception'ları unchecked yapıyoruz? — Zorunlu catch/throws
  boilerplate'inden ve `@Transactional` default rollback tuzağından kaçınmak için.
- **Q:** Exception wrap ederken nelere dikkat edilir? — Orijinal exception'ı `cause` olarak
  vermek, anlamlı bir mesaj eklemek.
- **Q:** `@RestControllerAdvice` handler sırası nasıl belirlenir? — En spesifik tip önce
  eşleşir (Spring, exception hiyerarşisinde en yakın eşleşeni seçer).

## 30 Saniyelik Mülakat Cevabı

"Exception swallowing'in gerçek etkisini gösterdim: bir payment gateway çağrısı fail
ediyordu ama catch bloğu boş olduğu için caller `true` (başarılı) dönüşü alıyordu.
Sonra sadece `throw new RuntimeException('payment failed')` yaparak rethrow ettiğimde de
orijinal exception'ın cause'unun kaybolduğunu gördüm. Doğrusu, orijinal exception'ı cause
olarak taşıyan anlamlı bir unchecked exception fırlatmaktı - hem hiyerarşi (BusinessException
-> PaymentException -> InsufficientBalanceException) hem de tam cause chain korunuyor, ve
@RestControllerAdvice bunu doğru HTTP status'a çeviriyor."

## Takip Soruları

- `@ExceptionHandler` birden fazla exception tipini nasıl tek bir metodda handle eder?
- Custom bir `Error` alt sınıfı ne zaman (nadiren) mantıklı olur?
