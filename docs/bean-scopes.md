# Spring Bean Scope'ları

Kod: `com.interviewlab.scopes.*`

**Birincil öğrenme arayüzü — Postman + IntelliJ debugger:**
```http
POST /api/labs/scopes/reset
POST /api/labs/scopes/singleton/bad
POST /api/labs/scopes/singleton/good
POST /api/labs/scopes/prototype/bad
POST /api/labs/scopes/prototype/good
GET  /api/labs/scopes/lifecycle
GET  /lab/scopes/request
GET  /lab/scopes/session
GET  /lab/scopes/application
```
Session scope için Postman Cookie Jar talimatı: "11 Bean Scopes & Lifecycle" klasörünün 00
INFO isteğine bakın. `BeanScopesTest`, AYNI davranışın otomatik regresyon kanıtıdır —
ikincildir, birincil değil.

## Singleton ≠ GoF Singleton

Spring "singleton" scope'u, **her `ApplicationContext` için bir instance** anlamına gelir ve
bunu *container* garanti eder — JVM/classloader başına bir instance anlamına gelmez, ki bunu
*sınıfın kendisi* garanti eder (bu, GoF pattern'idir,
`com.interviewlab.patterns.singleton.ClassicSingleton`, docs/design-patterns.md). Aynı JVM
içindeki iki ayrı `ApplicationContext` (testlerde sıkça karşılaşılan bir durum), aynı
`@Service` sınıfının kendi "singleton" instance'ına sahip olur.

## Hatalı Kod — singleton üzerinde mutable state

```java
// com.interviewlab.scopes.singleton.bad.MutableSingletonPriceService
private BigDecimal currentPrice; // shared across every concurrent request

public BigDecimal calculateDiscountedPrice(BigDecimal basePrice, BigDecimal discountPercentage) {
    this.currentPrice = basePrice;
    simulateSlowStep();
    ...
}
```

## Neden Yanlış

Tam olarak bir instance, her concurrent request'i işler. Bir instance field'ı, hepsi arasında
paylaşılan, mutable bir state'tir — hiçbir senkronizasyon olmadan bir data race.

## Gerçekte Ne Oluyor

`shouldCorruptResultWithMutableSingletonUnderConcurrency()`: farklı base price'larla 50
concurrent çağrı; bazı çağrılar FARKLI bir çağrının base price'ından hesaplanmış bir sonuç
döner, çünkü bir thread'in `currentPrice`'a yazdığı değer, ilk thread onu geri okumadan önce
başka bir thread tarafından üzerine yazılmıştır.

## Doğru Kod

```java
// com.interviewlab.scopes.singleton.good.StatelessPriceService
public BigDecimal calculateDiscountedPrice(BigDecimal basePrice, BigDecimal discountPercentage) {
    BigDecimal discountAmount = basePrice.multiply(discountPercentage); // local variable - each thread's own stack
    ...
}
```
Hâlâ bir singleton bean — sorun zaten bu değildi. Burada thread'ler arasında paylaşılan
hiçbir şey yok, dolayısıyla senkronize edilmesi gereken de hiçbir şey yok
(`shouldNeverCorruptResultWithStatelessSingletonUnderConcurrency`).

## Prototype

```java
// com.interviewlab.scopes.prototype.bad.SingletonWithDirectPrototypeInjection
public SingletonWithDirectPrototypeInjection(PrototypeWorker worker) { this.worker = worker; }
```

Dependency injection SADECE BİR KEZ, singleton oluşturulurken gerçekleşir. Spring,
container'dan bir `PrototypeWorker` ister (gerçekten yeni bir instance TAM O ANDA oluşturulur),
bunu teslim eder, ve singleton o tek referansı sonsuza dek elinde tutar —
`shouldReusePrototypeBeanWhenInjectedDirectlyIntoSingleton()`, her çağrıda aynı id'yi
gösterir.

**Çözüm 1 — `ObjectProvider`:** `workerProvider.getObject()`, her çağrıda container'a
yeniden sorar (`shouldCreateNewPrototypeInstanceWithObjectProvider`).

**Çözüm 2 — scoped proxy:** `@Scope(value = "prototype", proxyMode = ScopedProxyMode.TARGET_CLASS)`,
inject edilen field'ı, her metod çağrısında şeffaf bir şekilde yeni bir instance getiren bir
CGLIB proxy'ye dönüştürür — sıradan bir field erişimi gibi görünür, ama aynı etkiyi sağlar
(`shouldCreateNewPrototypeInstanceWithScopedProxy`). `ObjectProvider`'ın explicit
`.getObject()` çağrısına kıyasla, çağrı başına küçük bir proxying overhead'i getirir.

## Request / Session / Application

Üçü de `BeanScopesTest` içinde `MockMvc` ile kanıtlanmıştır:

- **Request** (`@Scope(WebApplicationContext.SCOPE_REQUEST, proxyMode = TARGET_CLASS)`):
  bir HTTP request İÇİNDEKİ her okuma için aynı instance, bir sonraki request'te farklı bir
  instance (`shouldReturnSameIdWithinOneRequestForRequestScopedBean`,
  `shouldReturnDifferentRequestScopedIdAcrossSeparateRequests`).
- **Session**: aynı `HttpSession`'ı paylaşan birçok request boyunca aynı instance; farklı bir
  session farklı bir instance alır
  (`shouldShareSessionScopedIdAcrossRequestsInSameSessionButNotAcrossSessions`).
- **Application**: `ServletContext` başına bir instance. Tipik bir tek-`ApplicationContext`'li
  Spring Boot uygulamasında bu, gözlemlenebilir şekilde bir singleton ile aynıdır — bu ayrım
  yalnızca birden fazla Spring context'inin tek bir `ServletContext`'i paylaştığı durumlarda
  önem kazanır (klasik Spring MVC root+child context kurulumu), ki Spring Boot'un varsayılan
  tek-context modelinde bu nadiren gerekir.

**Thread-safety notu:** request ve session scope'u, o scope İÇİNDEKİ concurrency'ye karşı
(örn. aynı session'a aynı anda çarpan iki tarayıcı sekmesi) bean'i kendiliğinden thread-safe
yapmaz — sadece hangi caller'ların bir instance'ı PAYLAŞTIĞINI garanti eder, ona erişimin
senkronize olduğunu değil.

## Sık Sorulan Mülakat Soruları

- **Q:** Spring singleton = GoF Singleton mi? — Hayır; Spring singleton "her
  ApplicationContext için bir instance" demektir, sınıfın kendisi bunu garanti etmez.
- **Q:** Prototype bean'i singleton'a inject edersem her çağrıda yeni instance alır mıyım? —
  Hayır, sadece bir kere inject edilir; `ObjectProvider` veya scoped proxy gerekir.
- **Q:** `ObjectProvider` ile scoped proxy arasındaki fark nedir? — Biri explicit
  `getObject()` çağrısı ister, diğeri normal field erişimi gibi görünür ama arkada proxy
  vardır.
- **Q:** Application scope singleton'dan farkı nedir? — ServletContext seviyesinde paylaşım;
  çoğu tek-context Spring Boot uygulamasında pratik farkı yoktur.

## 30 Saniyelik Mülakat Cevabı

"Singleton mutable state bug'ını bizzat gördüm: bir fiyat hesaplama servisinde instance
field kullanınca, 50 concurrent request'in bazıları başka bir request'in base price'ından
hesaplanmış sonuç döndürdü. Field'ı local variable'a çevirince sorun tamamen kayboldu.
Prototype tarafında da klasik hatayı yaptım: prototype bean'i normal constructor injection
ile singleton'a verince her çağrıda AYNI instance'ı aldığımı gördüm - ObjectProvider'a
geçince her çağrıda gerçekten farklı id'ler almaya başladım."

## Takip Soruları

- `@Scope` annotation'ını bir `@Bean` factory method'da nasıl kullanırsınız?
- Request scope bean'i bir `@Async` metod içinde kullanmaya çalışırsanız ne olur?
