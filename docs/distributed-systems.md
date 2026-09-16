# Distributed Transactions: 2PC vs Saga

Kod: `com.interviewlab.javacore.saga.*` — Test: `OrderSagaOrchestratorTest`

## Problem

Bir "sipariş oluştur" işlemi, aslında ayrı servislerin (ya da ayrı veritabanlarının) sahip
olduğu birden fazla adımdan oluşur: sipariş kaydı, ödeme tahsilatı, stok rezervasyonu,
kargo planlaması. Bunların HEPSİ ya birlikte başarılı olmalı, ya da HİÇBİRİ kalıcı
olmamalı - ama bunlar ayrı transaction sınırlarında (belki ayrı veritabanlarında bile)
yaşıyor, tek bir yerel `@Transactional` bunu kapsayamaz.

## Yaklaşım 1: 2PC (Two-Phase Commit)

```
Coordinator                Participant A         Participant B
    |-- PREPARE ------------->  |                     |
    |-- PREPARE --------------------------------------->  |
    |<-- READY -----------------|                     |
    |<-- READY ------------------------------------------  |
    |-- COMMIT -------------->  |                     |
    |-- COMMIT ------------------------------------------>  |
```

- **Prepare fazı**: coordinator, TÜM katılımcılara "hazır mısın?" sorar; her katılımcı
  kaynaklarını KİLİTLER ve "evet" (ya da "hayır") der.
- **Commit fazı**: HERKES "evet" dediyse, coordinator hepsine "commit et" emri verir.
  Biri bile "hayır" dediyse, hepsine "abort et" emri verilir.
- **Blocking problem:** bir katılımcı "READY" dedikten SONRA coordinator ÇÖKERSE, o
  katılımcı COMMIT mi ABORT mu emri geleceğini bilmeden kaynaklarını SÜRESİZ KİLİTLİ
  tutar - bu, 2PC'nin en bilinen zaafıdır ve mikroservis mimarilerinde 2PC'nin nadiren
  tercih edilmesinin temel nedenidir (yüksek kullanılabilirlik gereksinimiyle çelişir).

## Yaklaşım 2: Saga (bu projenin gerçekleştirdiği)

```
CreateOrder ---> ChargePayment ---> ReserveStock (BAŞARISIZ!) 
     |                  |                  X
     |                  |                  |
     v                  v                  |
(telafi: iptal)  (telafi: iade)  <---------+
     ^                  ^
     |                  |
  2. telafi          1. telafi (TERS SIRA)
```

Her adım KENDİ yerel transaction'ını commit eder (kilit YOK, kaynaklar hemen serbest
kalır). Bir adım başarısız olursa, coordinator (orchestrator) şimdiye kadar BAŞARIYLA
commit edilmiş adımları **TERS SIRADA** telafi edici (compensating) aksiyonlarla geri
alır - `OrderSagaOrchestratorTest.shouldCompensatePreviousStepsInReverseOrderWhenAStepFails()`
tam olarak bunu kanıtlıyor: `CreateOrder` ve `ChargePayment` commit olur, `ReserveStock`
başarısız olur, `ArrangeShipping`'e HİÇ ulaşılmaz, ve `ChargePayment` → `CreateOrder`
sırasıyla (en son commit edilen, ilk telafi edilen) geri alınır.

**Trade-off:** Saga blocking DEĞİLDİR (2PC'nin aksine), ama:
- Atomiklik İDDİA ETMEZ - ara durumlar (ör. "ödeme alındı ama stok henüz rezerve
  edilmedi") KISA bir süre için GERÇEKTEN GÖRÜNÜR olabilir (eventual consistency).
- Her adımın gerçek, çalışan bir telafi mantığı OLMALIDIR - "parayı iade et" her zaman
  "parayı çek"in basit tersi değildir (ör. kart zaten kapanmışsa).
- Orchestration-based (bu projenin yaklaşımı - merkezi bir koordinatör adımları sırayla
  çağırır) vs choreography-based (her servis, bir öncekinin event'ini dinleyip kendi
  adımını tetikler, merkezi koordinatör yok) - bu proje orchestration kullanıyor çünkü
  test edilebilirliği/okunabilirliği daha yüksek.

## Sık Sorulan Mülakat Soruları

- **Q:** 2PC neden mikroservislerde nadiren kullanılır? — Blocking'dir; coordinator
  çökerse katılımcılar kaynakları süresiz kilitli tutabilir, bu yüksek kullanılabilirlik
  gereksinimiyle çelişir.
- **Q:** Saga, ACID Isolation garantisi verir mi? — Hayır - ara durumlar kısa süreliğine
  görünür olabilir (eventual consistency), true isolation yok.
- **Q:** Telafi edici aksiyon her zaman mümkün müdür? — Hayır, bazı aksiyonlar (ör. bir
  e-posta gönderme) gerçekten "geri alınamaz" - bu durumlarda telafi, "durumu düzelten"
  başka bir aksiyon olabilir (ör. düzeltme e-postası), birebir tersi değil.

## 30 Saniyelik Mülakat Cevabı

"2PC, tüm katılımcıları prepare fazında kilitleyip hep birlikte commit/abort yaptırır -
ama coordinator çökerse katılımcılar süresiz kilitli kalabilir, bu yüzden mikroservislerde
nadiren tercih edilir. Saga bunun yerine her adımı kendi başına commit ettirir, bir adım
başarısız olursa önceki BAŞARILI adımları TERS SIRADA telafi edici aksiyonlarla geri alır
- bunu bir order/payment/stock senaryosuyla test ettim: stock adımı başarısız olunca
payment iade edildi, order iptal edildi, shipping adımına hiç ulaşılmadı."

## Takip Soruları

- Orchestration-based saga ile choreography-based saga arasındaki fark nedir?
- Bir telafi aksiyonunun KENDİSİ başarısız olursa ne olur? (Genelde retry + manuel
  müdahale/alerting gerekir - "telafi'nin telafisi" sonsuz bir problem olabilir.)
