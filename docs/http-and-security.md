# HTTP Semantics ve Security Temelleri

## HTTP Metodları: "POST=create, PUT=update" ezberinden kaçının

Doğru zihniyet, CRUD eşlemesi değil, **safe/idempotent/cacheable** semantiğidir (RFC 7231):

| Metod | Safe? | Idempotent? | Cacheable? | Anlamı |
|---|---|---|---|---|
| `GET` | ✅ | ✅ | ✅ | Sunucu durumunu DEĞİŞTİRMEZ; aynı isteği N kez göndermek TEK seferle aynı sonucu verir |
| `HEAD` | ✅ | ✅ | ✅ | GET gibi ama body yok - sadece header/varlık kontrolü |
| `POST` | ❌ | ❌ | Genelde hayır (yanıt açıkça cache-control ile işaretlenmezse) | Yeni bir kaynak OLUŞTURABİLİR, her çağrıda YENİ bir yan etki üretebilir (ör. iki POST = iki sipariş) |
| `PUT` | ❌ | ✅ | ❌ | Bir kaynağı TAMAMEN değiştirir/oluşturur - AYNI PUT'u N kez göndermek, kaynağı HER SEFERİNDE aynı son duruma getirir |
| `PATCH` | ❌ | ❌ (genelde) | ❌ | KISMİ güncelleme - idempotent OLMAK ZORUNDA değildir (ör. "sayacı 1 artır" PATCH'i idempotent değildir) |
| `DELETE` | ❌ | ✅ | ❌ | Bir kaynağı siler - AYNI DELETE'i iki kez göndermek (ilki sildikten sonra ikincisi "zaten yok" der) son durumu değiştirmez |

**Safe** = sunucu durumunu değiştirmeyen (sorgulama amaçlı).
**Idempotent** = AYNI isteği N kez göndermenin, 1 kez göndermekle AYNI SON DURUMU
üretmesi (ama N. çağrının YANITI 1. çağrıyla aynı olmak ZORUNDA değil - ör. tekrarlanan
bir DELETE, ilkinde 204, ikincisinde 404 dönebilir, ama kaynağın DURUMU her ikisinde de
"yok").

**"POST=create, PUT=update" ezberinin neden eksik olduğu:** PUT de bir kaynak
OLUŞTURABİLİR (client'ın kaynağın ID'sini bildiği "upsert" senaryosunda -
`PUT /users/42` kaynak yoksa oluşturur, varsa değiştirir - HER İKİ DURUMDA DA idempotent).
POST'un asıl ayırt edici özelliği idempotent OLMAMASIDIR, "create" ile eşanlamlı olması
değil.

## HTTP Status Kodları

| Kod | Anlamı | Not |
|---|---|---|
| `200 OK` | Başarılı, body var | |
| `201 Created` | Başarılı, yeni kaynak oluştu | `Location` header'ı yeni kaynağa işaret etmeli |
| `204 No Content` | Başarılı, body yok | Genelde `DELETE`/bazı `PUT` sonrası |
| `400 Bad Request` | İstek sözdizimsel/semantik olarak GEÇERSİZ | Client hatası - request'in kendisi bozuk |
| `401 Unauthorized` | Aslında "Unauthenticated" - credential eksik/geçersiz | `WWW-Authenticate` header'ı ZORUNLU (RFC 7235) |
| `403 Forbidden` | Kimlik biliniyor (ya da önemli değil), ama yetki YOK | Bkz. `docs/NOTES_CORRECTIONS.md` #8 |
| `404 Not Found` | Kaynak yok (ya da VARLIĞINI bile açıklamak istemiyorsan 403 yerine bunu dönebilirsin) | |
| `409 Conflict` | İstek, kaynağın MEVCUT durumuyla çakışıyor | Ör. optimistic lock çakışması (bkz. `docs/optimistic-locking.md`) |
| `422 Unprocessable Entity` | Sözdizimi doğru ama semantik olarak işlenemez | Ör. validation hatası |
| `500 Internal Server Error` | Sunucu tarafı beklenmeyen hata | Client'ın hatası DEĞİL |

Bu proje `GlobalExceptionHandler`'da (`docs/exceptions.md`, `/api/labs/exceptions`)
checked/unchecked exception ayrımını gerçek HTTP status kodlarına eşlemenin canlı bir
örneğini içeriyor.

## REST vs SOAP

**"REST = JSON" eşitliği YANLIŞTIR.** REST, bir **mimari STİLDİR** (Roy Fielding'in
2000 tez'i) - kaynak-tabanlı URL'ler, standart HTTP metodları, stateless, HATEOAS
(hypermedia) gibi kısıtlamalar tanımlar; VERİ FORMATINI dikte ETMEZ (REST ile XML, hatta
plain text de dönebilirsiniz - JSON sadece yaygın, pratik bir tercih).

**SOAP** ise bir **PROTOKOLDÜR** - XML tabanlı bir mesaj formatı (envelope + header +
body), genelde WSDL (Web Services Description Language) ile KATI bir kontrat tanımlar,
kendi hata modeli (SOAP Fault) vardır, HTTP dışında (SMTP gibi) taşıyıcılar üzerinde de
çalışabilir (REST doğası gereği HTTP'ye bağlıdır).

| | REST | SOAP |
|---|---|---|
| Ne | Mimari stil | Protokol |
| Format | Herhangi biri (genelde JSON) | Sadece XML |
| Kontrat | Genelde OpenAPI/Swagger (opsiyonel) | WSDL (genelde zorunlu, katı) |
| Durum | Stateless (REST kısıtlaması) | Stateful olabilir |

## Authentication vs Authorization — artık çalışan bir lab ile

Kod: `com.interviewlab.security.*`, `com.interviewlab.web.lab.security.*` — Lab:
`/api/labs/security/*` (LAB 17, `docs/INTERACTIVE_LABS.md`).

**Authentication (kimlik doğrulama)**: "Sen kimsin?" - `401`'in konusu.
**Authorization (yetkilendirme)**: "Sana bunu yapmana İZİN VAR mı?" - `403`'ün konusu.

Bu proje artık bunu SADECE anlatmıyor, gerçek Spring Security + JWT ile GÖSTERİYOR:

```http
GET /api/labs/security/protected              (token yok)
```
→ **401** (gerçek çalıştırılmış sonuç, varsayım değil).

```http
POST /api/labs/security/login?username=ada&role=USER
GET  /api/labs/security/protected             (geçerli token, Authorization: Bearer ...)
```
→ **200**, `{"authenticatedAs":"ada","authorities":["ROLE_USER"]}`.

```http
GET /api/labs/security/admin-only             (USER rolündeki geçerli token ile)
```
→ **403** (kimlik biliniyor - "ada" - ama `ROLE_ADMIN` yok).

```http
POST /api/labs/security/login?username=grace&role=ADMIN
GET  /api/labs/security/admin-only             (ADMIN rolündeki token ile)
```
→ **200**.

**Bu projenin geri kalanına (`/api/labs/persistence`, `/api/labs/optimistic`, vb.) hâlâ
auth GEREKMEZ** - `SecurityConfig`, sadece YENİ `/api/labs/security/**` path'lerini korur,
Faz 1/2/3'te inşa edilen 15 diğer lab'ı BOZMAMAK için bilinçli bir tasarım kararı (gerçek
bir production uygulamasında bunun TERSİ doğru olurdu - her şey varsayılan olarak korunur,
sadece gerçekten public olması gereken uç noktalar açıkça izin verilir).

### JWT (JSON Web Token) temelleri

```
header.payload.signature
```

- **Header**: hangi algoritmanın kullanıldığı (ör. `HS256`, `RS256`).
- **Payload**: claim'ler (ör. `sub` [subject/user id], `exp` [expiration], custom claim'ler).
- **Signature**: header+payload'ın, bir gizli anahtar (HMAC) ya da private key (RSA/ECDSA)
  ile imzalanmış hali - alıcının, token'ın DEĞİŞTİRİLMEDİĞİNİ doğrulamasını sağlar.

**KRİTİK: JWT ŞİFRELİ (encrypted) DEĞİLDİR, sadece imzalıdır (signed).** Header ve
payload, sadece **Base64URL ile ENCODE edilmiştir** - şifreleme değildir, HERKES tarafından
decode edilip OKUNABİLİR (jwt.io gibi bir siteye yapıştırıp içeriği görebilirsiniz).
İmza, sadece "bu token'ı BEKLENEN sunucu üretti VE içeriği değiştirilmedi" garantisi
verir - "bu içerik gizlidir" garantisi VERMEZ.

**Sonuç: payload'a ASLA şifre, API key gibi gizli bilgi KOYMAYIN** - herkes okuyabilir.
Gerçekten şifreleme gerekiyorsa JWE (JSON Web ENCRYPTION, ayrı bir standart) kullanılır,
düz JWT değil.

**Gerçek, çalıştırılmış kanıt** (`POST /api/labs/security/login?username=ada&role=USER`'ın
GERÇEK yanıtından, secret KULLANILMADAN, sadece base64 decode ile elde edildi):
```json
{"sub":"ada","roles":["USER"],"iat":1789495871,"exp":1789496771}
```
Bu proje, token'ı imzalayan secret key'e HİÇ ERİŞMEDEN, sadece `Base64.getUrlDecoder()`
ile bu payload'ı okudu - JWT'nin şifreli OLMADIĞININ doğrudan kanıtı.

## Sık Sorulan Mülakat Soruları

- **Q:** PUT idempotent mi? — Evet, aynı PUT'u N kez göndermek her seferinde AYNI son
  durumu üretir (POST'un aksine).
- **Q:** JWT şifreli midir? — Hayır, sadece imzalıdır (signed) - payload herkes tarafından
  okunabilir, base64 ile decode edilebilir.
- **Q:** REST = JSON mu? — Hayır, REST bir mimari stil, veri formatını dikte etmez.

## 30 Saniyelik Mülakat Cevabı

"HTTP metodlarını CRUD eşlemesiyle değil safe/idempotent/cacheable semantiğiyle
düşünüyorum - PUT idempotenttir (N kez = 1 kez), POST değildir. JWT konusunda önemli bir
nokta: token şifreli değil, sadece imzalı - payload'ı herkes decode edip okuyabilir, bu
yüzden içine asla gizli veri koymam. 401 ile 403'ü de net ayırıyorum: 401 'sen kimsin
bilmiyorum', 403 'seni biliyorum ama yetkin yok'."

## Takip Soruları

- HATEOAS nedir, gerçek dünyada neden nadiren tam olarak uygulanır?
- Bir API'de idempotency key (idempotency-key header) kullanmanın POST'u nasıl
  "idempotent hale getirdiği" (aslında POST'u idempotent yapmaz, ama tekrar denemeleri
  güvenli hale getirir)?
