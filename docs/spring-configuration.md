# Spring Configuration Annotations ve Auto-Configuration

Bean lifecycle sırası (constructor → BeanPostProcessor.before → @PostConstruct →
BeanPostProcessor.after) için **birincil öğrenme arayüzü — Postman + IntelliJ debugger:**
```http
GET /api/labs/scopes/lifecycle
```
Tam breakpoint sırası için `docs/DEBUGGER_LABS.md` "BEAN LIFECYCLE" bölümüne bakın (uygulama
BAŞLANGICINDA tetiklenir, ayrıca `com.interviewlab.scopes.lifecycle.BeanLifecycleDemoBean` +
`BeanLifecycleLoggingPostProcessor`'a breakpoint koyup uygulamayı Debug modda başlatın).

## IoC / DI temelleri

**IoC (Inversion of Control)**: nesnelerin kendi bağımlılıklarını KENDİLERİ oluşturması
yerine, bir container'ın (Spring `ApplicationContext`) onları oluşturup enjekte etmesi -
kontrolün "tersine döndüğü" yer burası (uygulama kodu değil, container "kimin ne zaman
oluşturulacağını" kontrol eder).

**DI (Dependency Injection)**: IoC'nin somut mekanizması - bir bean, ihtiyaç duyduğu
diğer bean'leri (constructor, setter, ya da field üzerinden) container'dan ALIR, kendi
`new` etmez.

**Neden constructor injection (bu projenin HER YERDE kullandığı)?**
```java
// BAD (tight coupling, test edilemez):
class PaymentService {
    private final PaymentGatewayClient client = new PaymentGatewayClient(); // hard-coded
}

// GOOD (bu projenin yaklaşımı):
class PaymentService {
    private final PaymentGatewayClient client;
    public PaymentService(PaymentGatewayClient client) { this.client = client; }
}
```
Field injection (`@Autowired private X x;`) YERİNE constructor injection tercih edilir çünkü:
- **Immutability**: alan `final` olabilir - bir kez set edilir, asla değişmez.
- **Testability**: bir unit test'te, Spring context'i hiç başlatmadan `new
  PaymentService(mockClient)` ile test edilebilir - field injection'da bu reflection
  gerektirir.
- **Explicit dependencies**: bir sınıfın TÜM bağımlılıkları constructor imzasında
  görünür - çok fazla bağımlılık varsa (constructor "şişerse"), bu GÖRÜNÜR bir "bu sınıf
  çok fazla iş yapıyor" sinyalidir (field injection bu sinyali gizler).
- Bu proje bunu `docs/persistence-context.md`'de belgelenen TEK istisna dışında (bkz.
  `PersistenceLifecycleService` - `@PersistenceContext EntityManager` field injection,
  çünkü bu annotation'ın constructor-tabanlı bir eşdeğeri yok) HER YERDE uyguluyor.

## Bean Lifecycle

Bkz. `com.interviewlab.scopes.lifecycle.*`, doğrulanmış gerçek sıra (`GET
/api/labs/scopes/lifecycle`):

```
1. Constructor (+ constructor injection)
2. BeanPostProcessor.postProcessBeforeInitialization
3. @PostConstruct (CommonAnnotationBeanPostProcessor tarafından çağrılır)
4. BeanPostProcessor.postProcessAfterInitialization
   ... bean kullanıma hazır ...
5. @PreDestroy (context kapanırken)
```

**Kritik nokta**: `@PostConstruct`'ın KENDİSİ, Spring'in bir `BeanPostProcessor`'ı
(`CommonAnnotationBeanPostProcessor`) tarafından çağrılır - "container'ın kendi özel
lifecycle callback'leri" bile, uygulama kodunun kullanabileceği AYNI genel mekanizmayı
(`BeanPostProcessor`) kullanır. Bu, Spring'in "her şey bir bean, her şey aynı
altyapıyla genişletilebilir" felsefesinin somut bir kanıtıdır.

## Temel Annotation'lar

| Annotation | Ne yapar |
|---|---|
| `@Configuration` | Bu sınıfın `@Bean` metodları içerdiğini, container'ın bunları bean tanımı olarak işleyeceğini belirtir |
| `@Bean` | Bir metodun DÖNÜŞ DEĞERİNİ bir bean olarak kaydet (genelde 3. taraf sınıflar için - kendi yazdığın sınıflarda `@Component` tercih edilir) |
| `@Component` | Bir sınıfı doğrudan bir bean olarak işaretle (`@Service`/`@Repository`/`@Controller`, `@Component`'in anlam taşıyan özelleşmiş halleridir) |
| `@ComponentScan` | Hangi paket(ler)in `@Component`/`@Service`/vb. için TARANACAĞINI belirtir |
| `@PropertySource` | Ek bir `.properties`/`.yml` dosyasını property kaynaklarına ekler |
| `@ConfigurationProperties` | Bir `application.yml` bölümünü tip-güvenli bir Java nesnesine bağlar (`@Value` tekil property'ler için, bu TOPLU bağlama için) |
| `@EnableAutoConfiguration` | Spring Boot'un classpath'e göre otomatik bean yapılandırmasını AÇAR |
| `@SpringBootApplication` | Üçünün TOPLAMI (aşağıya bakın) |

```java
@SpringBootApplication
// ≈
@SpringBootConfiguration   // @Configuration'ın özelleşmiş hali
@EnableAutoConfiguration
@ComponentScan             // bu sınıfın paketinden aşağı doğru tarar
public class InterviewLabApplication { ... }
```

Bu proje bunu `InterviewLabApplication.java`'da tam olarak kullanıyor - `@ComponentScan`
açıkça yazılmadığı halde, `com.interviewlab` paketinin TAMAMI (tüm `web.lab.*`
controller'lar, `*.bad`/`*.good` servisleri dahil) otomatik taranıyor, çünkü
`@SpringBootApplication` bunu implicit olarak sağlıyor.

## Auto-Configuration ve `@ConditionalOnMissingBean`

Spring Boot'un auto-configuration'ı, classpath'te NE olduğuna ve HANGİ bean'lerin ZATEN
tanımlı olduğuna bakarak "akıllı varsayılanlar" sağlar. Anahtar mekanizma
`@ConditionalOnMissingBean` - "bu TÜRDEN bir bean HENÜZ tanımlanmadıysa, ben tanımlayayım"
demektir.

**Bu proje bunu CANLI olarak kullanıyor** (bkz.
`com.interviewlab.transaction.propagation.demo.NestedTransactionManagerConfig`, ve
`docs/propagation.md`): Spring Boot, `spring-boot-starter-data-jpa` classpath'te olduğu
için otomatik olarak bir `JpaTransactionManager` bean'i tanımlamaya ÇALIŞIR - ama bu
otomatik bean tanımı `@ConditionalOnMissingBean(PlatformTransactionManager.class)` ile
korunur. Bu proje kendi `PlatformTransactionManager` bean'ini (nestedTransactionAllowed=true
ile) tanımladığı için, Spring Boot'un auto-configuration'ı "zaten biri var, ben
tanımlamayayım" der ve GERİ ÇEKİLİR - hiçbir çakışma/hata olmadan, bean override
mekanizması hiç devreye girmeden.

## Sık Sorulan Mülakat Soruları

- **Q:** `@Component` ile `@Bean` arasındaki fark nedir? — `@Component`, KENDİ yazdığın
  bir sınıfı işaretler (class-level). `@Bean`, bir `@Configuration` sınıfındaki bir
  METODUN dönüş değerini bean yapar - genelde 3. taraf (kaynak kodunu değiştiremediğin)
  sınıflar için kullanılır.
- **Q:** `@SpringBootApplication` tam olarak ne yapar? — `@SpringBootConfiguration` +
  `@EnableAutoConfiguration` + `@ComponentScan`'in birleşimi.
- **Q:** Auto-configuration nasıl "akıllı" davranır? — `@ConditionalOnMissingBean`,
  `@ConditionalOnClass` gibi conditional annotation'larla - kullanıcı kendi bean'ini
  tanımlarsa, auto-configuration geri çekilir.

## 30 Saniyelik Mülakat Cevabı

"`@SpringBootApplication`, üç annotation'ın toplamı: `@SpringBootConfiguration` (bu
sınıfın kendisinin bir configuration olduğu), `@EnableAutoConfiguration` (classpath'e
göre otomatik bean'ler), `@ComponentScan` (bu paketten aşağı tara). Auto-configuration'ın
'akıllılığı' `@ConditionalOnMissingBean` gibi annotation'lardan geliyor - projede kendi
`PlatformTransactionManager`'ımı tanımladığımda, Spring Boot'un otomatik olanı sessizce
geri çekildi, hiçbir çakışma olmadı."

## Takip Soruları

- `@Primary` ile `@Qualifier` arasındaki fark nedir, ne zaman hangisi kullanılır?
- Field injection'ın testability açısından neden field injection kullanılmaması gerektiği?
