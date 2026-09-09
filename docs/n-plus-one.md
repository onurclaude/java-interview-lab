# N+1, Lazy/Eager Fetching ve Fetch Stratejileri

Kod: `com.interviewlab.jpa.*` — Testler: `NPlusOneTest`

# Problem

`Order`'ın bir `@OneToMany List<OrderItem> orderItems`'ı var. N tane order yükleyip her
birinin item'larını okumak - gerçekte kaç sorgu çalışır?

## Hatalı Kod

```java
// com.interviewlab.jpa.bad.NPlusOneOrderService
List<Order> orders = orderRepository.findAll();   // query #1
for (Order order : orders) {
    total += order.getOrderItems().size();        // one MORE query, per order
}
```

## Neden Yanlış

`@OneToMany`, varsayılan olarak `FetchType.LAZY`'dir (varsayılan olarak EAGER olan
`@ManyToOne`/`@OneToOne`'ın aksine - aşağıya bakın). `findAll()` onu getirmez; HER order'da
`getOrderItems()`'a ilk erişim kendi `SELECT`'ini tetikler. N order için: **1 + N** sorgu.

## Gerçekte Ne Oluyor

`shouldIssueOneQueryPerOrderWhenTouchingLazyCollectionInALoop()`: 5 seed edilmiş order ile,
tam olarak 5 ayrı `SELECT ... FROM lab_jpa_order_item WHERE order_id = ?` statement'ı
yakalanır - başlangıçtaki orders sorgusuna ek olarak, order başına bir tane.

## Doğru Kod — üç çözüm, aynı sonuç, farklı trade-off'lar

**Fetch join** (`FetchJoinOrderService`):
```java
@Query("select distinct o from Order o left join fetch o.orderItems")
List<Order> findAllWithItemsFetchJoin();
```
Tek sorgu, gerçek bir SQL JOIN, koleksiyonu sadece bu çağrı için doldurur.

**`@EntityGraph`** (`EntityGraphOrderService`) — aynı tek-sorgu sonucu, JPQL'de elle
yazmak yerine deklare edilmiş:
```java
@EntityGraph(attributePaths = "orderItems")
@Query("select o from Order o")
List<Order> findAllWithEntityGraph();
```

**DTO projection** (`DtoProjectionOrderService`) — caller'ın hiçbir zaman tam entity'lere
ihtiyacı olmadığında:
```java
@Query("select new ...OrderSummary(o.id, o.customerName, count(oi)) from Order o left join o.orderItems oi group by o.id, o.customerName")
List<OrderSummary> findAllSummaries();
```
Entity yok, koleksiyon yok, persistence-context takip overhead'i yok - sadece ihtiyaç
duyulan sütunlar. Üçü de toplamda tam olarak bir sorgu ürettiği kanıtlanmıştır
(`shouldIssueOnlyOneQueryTotalWithFetchJoin`, `...WithEntityGraph`, `...WithDtoProjection`).

## EAGER neden bedava bir çözüm değil

```java
// OrderItem.product
@ManyToOne(fetch = FetchType.EAGER)   // the DEFAULT for @ManyToOne - written out here on purpose
```

`EagerFetchAlwaysLoadsService.sumQuantitiesOnly()`, `getProduct()`'ı hiç okumaz - ama mapping
EAGER olduğu için, herhangi bir yerde bir `OrderItem`'ın her yüklenmesi, koşulsuz olarak her
zaman `Product`'ı da join'ler/yükler.
`shouldAlwaysJoinProductWhenLoadingOrderItemsDueToEagerFetch()`, yakalanan SQL'in hâlâ product
tablosuna referans verdiğini gösterir. EAGER "akıllı" anlamına gelmez - "her zaman, koşulsuz,
sonsuza dek, her yerde" anlamına gelir. Yukarıdaki N+1 çözüm araçları (fetch join,
`@EntityGraph`), eager loading'in hiç mümkün olmamasından vazgeçmek yerine, onu tek bir sorgu
için bilinçli olarak seçmenin doğru yoludur.

## LazyInitializationException ve Open Session in View

```java
// com.interviewlab.jpa.bad.LazyInitializationDemoService
@Transactional(readOnly = true)
public Order loadOrderWithoutTouchingItems(Long orderId) {
    return orderRepository.findById(orderId).orElseThrow();
    // transaction/session ends HERE
}
```

`shouldThrowLazyInitializationExceptionWhenAccessingLazyCollectionOutsideTransaction()`: bu
metod return ettikten sonra `order.getOrderItems()` çağırmak
`org.hibernate.LazyInitializationException` fırlatır - fetch sorgusunu çalıştırabilecek
session zaten kapatılmıştır.

**Open Session in View (OSIV)**, Hibernate session'ını tüm HTTP request boyunca açık tutar,
böylece view/serialization kodundan lazy erişim "sorunsuz çalışır". Bu proje, lazy-access
buglarının burada, sessizce örtbas edilmek yerine, açıkça ortaya çıkması için bilinçli olarak
`spring.jpa.open-in-view=false` ayarını yapar (bkz. `application.yml`) - OSIV'in kolaylığı,
request'in TAM süresi boyunca (template render/JSON serialization dahil) tutulan bir
veritabanı connection'ına mal olur ve bir lazy-loading sorgusunun gerçekte nereden
tetiklendiğini gizler.

## Trade-off'lar

| Çözüm | Sorgu sayısı | Yüklenen entity'ler | En uygun olduğu durum |
|---|---|---|---|
| Fetch join | 1 | Tam `Order` + `OrderItem` | Yüklenen entity'leri sonradan değiştirmek gerekiyorsa |
| `@EntityGraph` | 1 | Tam `Order` + `OrderItem` | Fetch join ile aynı, deklaratif stil |
| DTO projection | 1 | Yok | Read-only, özet şeklinde veri |
| EAGER mapping | Her zaman join'ler, her yüklemede | Tam, koşulsuz | Neredeyse hiçbir zaman doğru varsayılan değil |

## Sık Sorulan Mülakat Soruları

- **Q:** N+1 problemi nedir? — Bir liste sorgusundan sonra her elemanın lazy bir
  ilişkisine erişmenin, eleman sayısı kadar ek sorgu tetiklemesi.
- **Q:** EAGER N+1'i çözer mi? — Hayır, farklı bir maliyete (her zaman, gereksiz yere
  join/load) dönüştürür.
- **Q:** LazyInitializationException ne zaman oluşur? — Session/transaction kapandıktan
  sonra lazy bir alana erişilmeye çalışıldığında.
- **Q:** Open Session in View nedir, neden riskli? — Session'ı tüm request boyunca açık
  tutar; connection'ı gereğinden uzun tutar ve lazy query'lerin nereden geldiğini gizler.

## 30 Saniyelik Mülakat Cevabı

"N+1'i gerçek SQL logunda gördüm: 5 order için `findAll()` bir sorgu attı, ama her order'ın
`getOrderItems()`'ına erişince 5 tane daha sorgu gitti - toplam 6. Fetch join veya
@EntityGraph ile bunu tek sorguya indirdim. Ayrıca EAGER'ın 'ücretsiz çözüm' olmadığını da
gösterdim: OrderItem'daki Product EAGER olduğu için, product'a hiç dokunmayan bir metod bile
her seferinde product'ı join'liyordu. LazyInitializationException'ı da bizzat tetikledim:
transaction kapandıktan sonra lazy koleksiyona erişince exception aldım - bu yüzden projede
open-in-view'i bilinçli olarak kapalı tutuyorum."

## Takip Soruları

- `@BatchSize` N+1'i nasıl azaltır (fetch join'in alternatifi olarak)?
- Fetch join ile pagination (`Pageable`) neden birlikte iyi çalışmaz?
