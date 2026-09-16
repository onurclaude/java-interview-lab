# Design Pattern'lar

Kod: `com.interviewlab.patterns.*`

**Birincil öğrenme arayüzü — Postman + IntelliJ debugger (Strategy/Factory/Adapter/Observer/
Builder/Proxy için):**
```http
POST /api/labs/patterns/strategy/{bad,good}?paymentType=...
POST /api/labs/patterns/factory/resolve?paymentType=EXTERNAL_PROVIDER
POST /api/labs/patterns/adapter/{bad,good}
POST /api/labs/patterns/observer/place-order
GET  /api/labs/patterns/observer/state
POST /api/labs/patterns/builder/{build,build-invalid}
POST /api/labs/patterns/proxy/{without-logging,with-logging}
```
Tam breakpoint sırası için **`docs/DEBUGGER_LABS.md`** 34-38 numaralı girişlere bakın. Kalan
diğer pattern'ler (Template Method/Observer'ın kendisi hariç Decorator/Facade/Singleton/Chain
of Responsibility) kod-okuma + `DesignPatternsTest` ile en iyi gösterilir - bu ikincildir,
Strategy/Factory/Adapter/Observer/Builder/Proxy için ise HTTP lab BİRİNCİLDİR.

Aşağıdaki her pattern, tek bir paylaşılan checkout/payment domain'inde gerçek bir problemi,
yerini aldığı naif alternatifle karşılaştırarak çözer. Tam "WHAT/WHY/WHEN NOT TO USE"
detayı her sınıfın javadoc'unda yaşar - bu doc, mülakata yönelik özettir.

## Strategy + Factory

**Problem:** ödeme tipine göre farklı şekilde ödeme almak. **Hatalı:**
`patterns.strategy.bad.IfElsePaymentProcessor` — her yeni tip için düzenlenen, giderek
büyüyen bir `if/else` zinciri. **Pattern:** `PaymentStrategy` interface'i + tip başına bir
implementasyon (`CreditCardPaymentStrategy`, `WalletPaymentStrategy`,
`BankTransferPaymentStrategy`), branch'lenmek yerine `PaymentStrategyFactory` aracılığıyla
seçilir - bu factory, context'teki her `PaymentStrategy` bean'inden Spring tarafından
otomatik olarak doldurulur. **Ne zaman kullanılmamalı:** eğer gerçekten sadece bir veya iki
varyant varsa ve büyüme beklenmiyorsa, interface seremonisi kendini amorti etmeyebilir.

## Template Method

**Problem:** birkaç ödeme tipi tek bir işleme iskeletini paylaşır
(validate→authorize→execute→audit); sadece `authorize` tipe göre değişir. **Pattern:**
`PaymentProcessingTemplate.process()` `final`'dir; sadece `authorize()` abstract'tır
(`CreditCardPaymentProcessing`, `WalletPaymentProcessing`). **Ne zaman kullanılmamalı:**
değişen adımların sayısı sürekli büyüyorsa, Template Method katı bir sınıf hiyerarşisiyle
mücadele etmeye başlar - bu noktada genellikle Strategy (algoritmanın tamamını inject etmek)
kazanır.

## Observer

**Problem:** diğer sistemlerin (inventory, notification), order kodunun onları bilmesine
gerek kalmadan bir sipariş verildiğinde tepki vermesi gerekir. **Pattern:** Spring'in
`ApplicationEventPublisher` + `@EventListener`'ı (`OrderPlacementService`,
`OrderPlacedEvent` publish eder; `InventoryReservationListener`/`EmailNotificationListener`
tepki verir) — GoF Observer pattern'inin hazır bir implementasyonu.

## Decorator

**Problem:** bir temel fiyatın öngörülemez bir ayarlama KOMBİNASYONUNA ihtiyacı vardır
(indirim, ücret, ikisi birden, hiçbiri). **Kötü alternatif:** giderek büyüyen bir
boolean/optional flag listesine sahip bir metod, veya kombinasyon başına bir subclass
(kombinatoryal patlama). **Pattern:** `PercentageDiscountDecorator`/`HandlingFeeDecorator`,
her biri başka bir `PricedItem`'ı sarmalar - herhangi bir kombinasyon sadece iç içe
construction'dır, yeni bir sınıfa gerek yoktur. **Ne zaman kullanılmamalı:** ayarlama
SIRASI iş açısından kritikse ve yanlış yapılması kolaysa, her çağrı noktasının decorator'ları
doğru şekilde iç içe geçireceğine güvenmek yerine, sırayı bir kez kodlayan açık bir pipeline
daha güvenlidir.

## Adapter

**Problem:** üçüncü parti bir SDK'nın interface'i (`ExternalPaymentProviderSdk`: `long`
olarak cent, bir status-code `int`), bu projenin kendi `PaymentStrategy`'siyle uyuşmuyor ve
SDK değiştirilemiyor. **Pattern:** `ExternalPaymentProviderAdapter`, çeviriyi TEK BİR yerde
yapar. **Ne zaman kullanılmamalı:** her iki interface'i de kontrol ediyorsan, sebepsiz yere
bir çeviri katmanı eklemek yerine doğrudan birini değiştir.

## Facade

**Problem:** checkout; validation, payment ve notification'ı içerir — ayrı subsystem'ler.
**Pattern:** `CheckoutFacade.checkout()`, orkestrasyonu tek bir çağrının arkasına gizler;
subsystem'ler bağımsız olarak kullanılabilir kalır. **Ne zaman kullanılmamalı:** bireysel
adımlar üzerinde ince taneli kontrole ihtiyaç duyan caller'lara (örn. "şimdi validate et,
ödeme yöntemini daha sonra seç") subsystem'lerin doğrudan kullanılması daha iyi hizmet eder.

## Builder

**Problem:** telescoping bir constructor veya mutable, yarı-oluşturulmuş bir bean olmadan,
birkaç OPSİYONEL alana (kupon, hediye paketi, teslimat notu) sahip immutable bir
`OrderRequest` inşa etmek. **Neden bir record değil?** Bir record'un canonical
constructor'ı, her caller'ı opsiyonel olsun olmasın her alan için pozisyonel bir şey
geçmeye zorlar - tam olarak Builder'ın çözdüğü, çağrı noktasının okunmazlığı problemi. Tüm
alanlar zorunlu olduğunda bir record *daha iyi* bir seçimdir (bkz. `OrderRequest`'in kendi
javadoc'u). **Pattern:** `OrderRequest.builder(...).items(...).giftWrap(true).build()`,
sadece `build()`'de bir kez validate eder ve tam oluşmuş, immutable bir nesne teslim eder.

## Singleton (GoF) ile Spring singleton Karşılaştırması

**Pattern:** `ClassicSingleton` — private constructor, `volatile` bir field ile
double-checked locking, `getInstance()`. **Mülakat noktası:** bu, bir Spring `@Service`
(docs/bean-scopes.md) ile AYNI garanti DEĞİLDİR — GoF Singleton, sınıfın kendisi tarafından
zorlanır (JVM/classloader başına bir instance); Spring "singleton" ise container tarafından
zorlanır (her `ApplicationContext` için bir instance).

## Proxy

**Pattern:** `GreeterProxyFactory`, delegate etmeden önce/sonra log tutan sıradan bir JDK
dynamic `Proxy` inşa eder - bu projede `@Transactional`, `@Async` ve her `@Aspect`'in
dayandığı şeyin minimal, sıfırdan yazılmış bir versiyonudur. Aynı zamanda self-invocation'ın
üçünü de *neden* bozduğunun da açıklamasıdır (docs/transactions.md, docs/async.md,
docs/aop.md): gerçek nesnenin içindeki `this`, asla proxy değildir.

## Chain of Responsibility

**Problem:** order validation'ın birkaç bağımsız kurala ihtiyacı vardır (stok, fraud,
harcama limiti). **Hatalı:** `patterns.chainofresponsibility.bad.MonolithicOrderValidator`
— tek bir metod, üç ilgisiz concern, hiçbiri ayrı ayrı test edilebilir veya yeniden
kullanılabilir değil. **Pattern:** `StockValidator`/`FraudValidator`/`LimitValidator`, her
biri kendi sınıfı, `OrderValidationChain` tarafından sırayla çalıştırılır - bir kural
eklemek bir sınıf eklemek demektir, bir tanesini düzenlemek değil.

## Sık Sorulan Mülakat Soruları

- **Q:** Strategy ile Factory nasıl birlikte çalışır? — Factory, doğru Strategy
  implementasyonunu seçer/oluşturur; Strategy, seçilen davranışı encapsulate eder.
- **Q:** Decorator ile Proxy karışır mı? — İkisi de "wrap" eder, ama Decorator davranış
  EKLER, Proxy ERİŞİMİ kontrol eder (lazy loading, security, transaction).
- **Q:** Spring @Service GoF Singleton mıdır? — Hayır, container-managed bir scope'tur,
  class'ın kendisi tekilliği garanti etmez.
- **Q:** Builder her zaman record'dan iyi midir? — Hayır; tüm alanlar zorunluysa record daha
  basit ve daha az kod gerektirir.

## 30 Saniyelik Mülakat Cevabı

"Design pattern'ları soyut örneklerle değil, tek bir checkout domain'i üzerinde birbirine
bağlı şekilde uyguladım: Strategy+Factory ödeme tipini seçiyor, Template Method ortak
işleme akışını tanımlıyor, Observer Spring event'leriyle sipariş sonrası bildirimleri
decouple ediyor, Decorator fiyat ayarlamalarını kombinlenebilir hale getiriyor, Facade
hepsini tek bir checkout çağrısı arkasında topluyor. En çok öğrendiğim şey Proxy pattern'iydi
- Spring'in @Transactional, @Async ve AOP'sinin hepsinin aynı proxy mekanizmasına dayandığını
ve bu yüzden hepsinin aynı self-invocation tuzağına düştüğünü kendi elimle JDK dynamic proxy
yazarak anladım."

## Takip Soruları

- Strategy pattern ile Spring'in `@ConditionalOnProperty` ile bean seçimi nasıl ilişkilenir?
- Chain of Responsibility'de bir validator'ın zinciri erken kesmesi (short-circuit) nasıl
  eklenir?
