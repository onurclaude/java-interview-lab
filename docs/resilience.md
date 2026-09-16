# Resilience4j: Retry, Circuit Breaker, Rate Limiter, Bulkhead, Timeout, Fallback

Kod: `com.interviewlab.resilience.*`, `com.interviewlab.web.lab.resilience.*` — Lab:
`/api/labs/resilience/*` — Doc: bu dosya.

## Problem

"Bir dış bağımlılık başarısız oluyor ya da yavaş" - TEK bir problem gibi görünür, ama
altı FARKLI mekanizma altı FARKLI yönünü ele alır. Hepsi `FlakyExternalService`
(davranışı kontrol edilebilir sahte bir dış servis) üzerinden GERÇEK Resilience4j
nesneleriyle (programatik API - `Retry.of(...)`, `CircuitBreaker.of(...)`, vb.) test
edilmiştir.

## Retry

```http
POST /api/labs/resilience/retry
```

İlk 2 çağrı başarısız olacak şekilde ayarlanmış, `maxAttempts=3`. **Gerçek sonuç:**
`actualCallCount: 3`, `outcome: SUCCEEDED_AFTER_RETRIES` - 3. denemede başarılı oldu,
çağıran hiçbir retry mantığı yazmadı.

```http
POST /api/labs/resilience/retry-exhausted
```

Servis HER ZAMAN başarısız. **Gerçek sonuç:** `actualCallCount: 3`,
`outcome: ALL_ATTEMPTS_EXHAUSTED` - Retry SINIRSIZ değildir, `maxAttempts` tükenince
orijinal exception çağırana ulaşır.

**Retry storm riski:** çok agresif retry (kısa `waitDuration`, yüksek `maxAttempts`),
ZATEN zorlanan bir bağımlılığa daha fazla yük bindirerek onu DAHA DA kötüleştirebilir -
bu yüzden artan (exponential) backoff önemlidir.

## Circuit Breaker

```http
POST /api/labs/resilience/circuit-breaker
```

**Gerçek sonuç:** `stateTransitions: ["CLOSED", "OPEN"]` - 4 ardışık başarısızlıktan
sonra (`slidingWindowSize=4`, `failureRateThreshold=50`), devre CLOSED'dan OPEN'a geçti.
OPEN durumdayken bir çağrı daha denendiğinde: `underlyingServiceCalledDuringOpenState:
false`, `shortCircuited: true` - **gerçek servis HİÇ ÇAĞRILMADAN**
`CallNotPermittedException` fırlatıldı.

```
CLOSED --[failure rate eşiği aşıldı]--> OPEN --[waitDurationInOpenState geçti]--> HALF_OPEN
   ^                                                                                  |
   |------------------[HALF_OPEN'daki denemeler başarılı]----------------------------|
   |------------------[HALF_OPEN'daki denemeler başarısız]--> OPEN'a geri dön
```

**Retry ile temel fark:** Retry, TEK bir çağrının içinde "tekrar dene" der (çağıranı
bekletir). Circuit Breaker, ÇOKLU çağrılar arasında bir DURUM tutar - "bu bağımlılık
şu an sağlıksız, denemeyi bile boşver" kararını TÜM sonraki çağrılar için verir.

## Rate Limiter

```http
POST /api/labs/resilience/rate-limiter
```

**Gerçek sonuç:** `limitForPeriod: 2`, `attempted: 5`, `permitted: 2`, `rejected: 3`.

**Circuit Breaker ile temel fark:** Rate Limiter, bağımlılığın SAĞLIĞINDAN bağımsız
çalışır - servis TAMAMEN SAĞLIKLI olsa bile, ÖNCEDEN belirlenmiş bir hız tavanını aşan
istekler reddedilir (ör. "bu API'ye saniyede en fazla 100 istek gönderebilirim"
sözleşmesini korumak için). Circuit Breaker, GÖZLEMLENEN başarısızlığa REAKTİF tepki
verir.

## Bulkhead

```http
POST /api/labs/resilience/bulkhead
```

**Gerçek sonuç:** `maxConcurrentCalls: 2`, 4 eşzamanlı istek denendi, `accepted: 2`,
`rejected: 2` - kalan 2 istek KUYRUĞA ALINMADI, anında reddedildi
(`maxWaitDuration=ZERO`).

**Analoji:** gemi bölmeleri (compartments) - bir bölme su alsa (bir bağımlılık
yavaşlasa/tıkansa) bile, GEMİ BATMAZ (çağıranın TÜM thread havuzu tükenmez), çünkü o
bağımlılığa aynı anda gidebilecek çağrı sayısı SINIRLIDIR.

## Timeout

```http
POST /api/labs/resilience/timeout
```

Simüle edilmiş servis 500ms sürüyor, `TimeLimiter` 100ms'de kesiyor. **Gerçek sonuç:**
`outcome: TIMED_OUT` - çağıran, servisin GERÇEKTEN bitmesini (500ms) beklemedi, 100ms'de
vazgeçti.

## Fallback

```http
POST /api/labs/resilience/fallback
```

Retry tükendikten sonra, exception fırlatmak yerine `DEFAULT_CACHED_RESPONSE` döndü.
**Kritik uyarı (bu projenin ısrarla vurguladığı):** fallback, HER SENARYODA "güvenli"
bir seçim DEĞİLDİR - bir fiyat sorgusu başarısız olduğunda ESKİ bir fiyatı fallback
olarak dönmek, kullanıcıya YANLIŞ bir fiyat gösterme riski taşır. Fallback'in ne
döndüreceği, o VERİNİN bayat olmasının kabul edilebilir olup olmadığına bağlıdır.

## Trade-off'lar

| Mekanizma | Neyi çözer | Neyi çözmez |
|---|---|---|
| Retry | Geçici (transient) hatalar | Kalıcı hatalar - retry storm riski |
| Circuit Breaker | Zaten sağlıksız bir bağımlılığa boşuna istek göndermeyi durdurur | Bağımlılığın KENDİSİNİ iyileştirmez |
| Rate Limiter | Önceden belirlenmiş bir hız sözleşmesini korur | Bağımlılığın gerçek sağlığını izlemez |
| Bulkhead | Bir bağımlılığın TÜM kaynakları tüketmesini önler | Bağımlılığın kendisi hâlâ yavaş/başarısız olabilir |
| Timeout | Çağıranın süresiz beklemesini önler | Arka planda başlamış işi iptal etmeyebilir (implementasyona bağlı) |
| Fallback | Çağırana HER ZAMAN bir yanıt verir | Yanıtın DOĞRULUĞUNU garanti etmez - bayat veri riski |

## Sık Sorulan Mülakat Soruları

- **Q:** Retry ile Circuit Breaker'ı ne zaman BİRLİKTE kullanırsın? — Neredeyse her zaman
  - Retry geçici hataları tolere eder, Circuit Breaker KALICI hale gelen başarısızlıkları
  tespit edip fail-fast'e geçer (Retry'nin sonsuz denemeye "yaklaşmasını" önler).
- **Q:** Circuit Breaker HALF_OPEN durumunda ne olur? — Sınırlı sayıda deneme çağrısına
  izin verilir; başarılı olurlarsa CLOSED'a döner, başarısız olurlarsa tekrar OPEN'a geçer.
- **Q:** Fallback her zaman iyi bir fikir mi? — Hayır - döndürdüğü verinin BAYAT/YANLIŞ
  olabileceği senaryolarda (ör. fiyat, stok durumu), sessizce yanlış bilgi göstermek
  exception fırlatmaktan DAHA KÖTÜ olabilir.

## 30 Saniyelik Mülakat Cevabı

"Resilience4j'nin altı mekanizmasını hepsini `/api/labs/resilience` altında gerçek
durum geçişleriyle test ettim: Retry geçici hataları 3 denemede telafi etti, ama HER
ZAMAN başarısız olan bir servise karşı tükendi ve exception'ı geçirdi. Circuit Breaker,
4 ardışık başarısızlıktan sonra CLOSED'dan OPEN'a geçti - OPEN'dayken gerçek servisi hiç
çağırmadan fail-fast yaptı, bunu 'servis hiç çağrılmadı' assertion'ıyla kanıtladım. Rate
Limiter ile Circuit Breaker'ın farkını da net gördüm: Rate Limiter servis sağlıklı olsa
bile sabit bir hız tavanını uygular, Circuit Breaker gözlemlenen başarısızlığa tepki
verir."

## Bulkhead: semaphore vs ThreadPoolBulkhead (gerçek testle doğrulandı)

Yukarıdaki `/api/labs/resilience/bulkhead`, semaphore tabanlı `Bulkhead`'i kullanır -
`ResilienceTest.shouldRunSemaphoreBulkheadSynchronouslyOnCallingThreadButThreadPoolBulkheadAsynchronously()`
ikisi arasındaki GERÇEK farkı kanıtlıyor:

| | Semaphore `Bulkhead` | `ThreadPoolBulkhead` |
|---|---|---|
| Çalışma şekli | SENKRON - `decorateSupplier(...).get()`, iş bitene kadar ÇAĞIRANI BLOKE EDER | ASENKRON - hemen bir `CompletionStage` döner, iş AYRI bir thread havuzunda çalışır |
| Ölçtüğü | Sadece EŞZAMANLI ÇAĞRI SAYISI | Hem eşzamanlı çağrı sayısı HEM ayrı bir thread pool + kuyruk |
| Gerçek test sonucu | 100ms'lik simüle edilmiş çağrı, `get()`'i GERÇEKTEN ~100ms bloke etti | Aynı 100ms'lik çağrı, `get()` 50ms'DEN AZ sürede döndü - iş arka planda devam etti |
| Ne zaman tercih edilir | Zaten senkron bir çağrı zincirindeyseniz (ör. bir `@Transactional` metodun içi) | Çağıranın thread'ini HİÇ bloke etmemesi gerekiyorsa (ör. bir reactive/async akış) |

## Takip Soruları

- Retry + Circuit Breaker birlikte kullanıldığında hangi SIRAYLA sarmalanmalı (decorator
  sırası önemli mi)?
