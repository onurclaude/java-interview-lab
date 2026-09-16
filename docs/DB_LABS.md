# DB Labs (DBeaver)

Bu dosya, her DB-ilişkili lab için DBeaver'da (`localhost:5434/interviewlab`,
user/pass `interviewlab`/`interviewlab`) çalıştırıp kendi gözlerinle inceleyebileceğin
copy/paste SQL içerir. Her sorgu, sırasıyla ilgili `/api/labs/*` endpoint'ini çağırdıktan
SONRA çalıştırılmak üzere yazılmıştır.

---

## Persistence Context (LAB 01)

```sql
SELECT * FROM lab_customer;
```

`POST /api/labs/persistence/bad` sonrası: `name` sütunu hâlâ `Ada` (değişiklik kayboldu).
`POST /api/labs/persistence/good` sonrası: yeni bir satır `Linus Torvalds` ismiyle.

---

## Transaction Rollback (LAB 02)

```sql
SELECT * FROM lab_account ORDER BY id DESC LIMIT 5;
```

`bad` sonrası: `balance = 70.00` (checked exception'a rağmen commit oldu).
`good` sonrası: `balance = 100.00` (rollbackFor ile rollback oldu).

---

## Propagation (LAB 03)

```sql
SELECT * FROM lab_account ORDER BY id DESC LIMIT 5;
SELECT * FROM lab_audit_log ORDER BY id DESC LIMIT 5;
```

`requires-new/bad` sonrası: `lab_audit_log`'da `self-invocation-N` mesajlı satır YOK.
`requires-new/good` sonrası: `lab_audit_log`'da `real-proxy-N` mesajlı satır VAR (outer
rollback olsa bile hayatta kaldı).

---

## Isolation (LAB 06)

```sql
SELECT * FROM lab_isolation_account;
```

Her `reset` sonrası tek satır, `balance = 100`. `read-committed` sonrası nihai balance 200
(T2'nin commit'i); `repeatable-read` sonrası nihai balance yine 200 olur (T2 gerçekten
commit etti) ama T1'in KENDİ transaction'ı İÇİNDEN yaptığı ikinci okuma 100 gördü - bu
farkı HTTP response'undaki `transaction1SecondRead` alanından gör, tablonun son hali her
iki durumda da aynıdır (T2 her durumda gerçekten commit eder).

---

## Optimistic Locking (LAB 04)

```sql
SELECT id, stock FROM lab_no_version_product;
SELECT id, stock, version FROM lab_locking_product;
```

`bad` sonrası: `lab_no_version_product.stock = 7` (beklenen 5 değil - lost update).
`good` sonrası: `lab_locking_product.stock = 8`, `version = 1` (T2 reddedildi, sadece T1 uygulandı).

---

## Pessimistic Locking (LAB 05)

```sql
SELECT id, stock FROM lab_no_version_product;
SELECT id, stock FROM lab_locking_product;
```

`bad` sonrası: kilitsiz race, lost update (stock beklenenden farklı).
`good` sonrası: `stock = 5` (10 - 2 - 3, HER İKİ azaltma da doğru uygulandı - FOR UPDATE bloklaması sayesinde).

---

## N+1 (LAB 14)

```sql
SELECT * FROM lab_jpa_order;
SELECT * FROM lab_jpa_order_item;
SELECT * FROM lab_jpa_product;
```

10 sipariş, her biri 1-3 arası kalem, hepsi aynı ürüne (`Widget`) referans verir - bu
kasıtlı: N+1 demosu için ürün çeşitliliği önemli değil, sipariş sayısı önemli.

---

## Database Indexes / EXPLAIN ANALYZE (Faz 3)

Bu proje henüz özel bir index lab HTTP endpoint'i içermiyor - ama aşağıdaki adımları
DBeaver'dan bizzat çalıştırarak PostgreSQL'in index kullanımını gerçek `EXPLAIN ANALYZE`
çıktısıyla gözlemleyebilirsin (bu proje bu adımları bizzat çalıştırıp gerçek sonuçları
doğruladı - aşağıdaki sayılar üretilmiş/varsayılmış değil, gerçek çalıştırma çıktısıdır).

**1. Test verisi ekle (5000 satır):**

```sql
DO $$
BEGIN
  FOR i IN 1..5000 LOOP
    INSERT INTO lab_customer(id, name, email)
    VALUES (nextval('lab_customer_seq'), 'Customer ' || i, 'customer' || i || '@example.com');
  END LOOP;
END $$;
```

**2. Index'siz sorgu planını gözlemle:**

```sql
EXPLAIN ANALYZE SELECT * FROM lab_customer WHERE email = 'customer2500@example.com';
```

Gerçek çıktı (bu projede çalıştırıldı):
```
Seq Scan on lab_customer  (cost=0.00..109.53 rows=1 width=45) (actual time=0.111..0.238 rows=1 loops=1)
  Filter: ((email)::text = 'customer2500@example.com'::text)
  Rows Removed by Filter: 5001
Execution Time: 0.255 ms
```
`Seq Scan` - tüm 5001 satır tek tek kontrol edildi (`Rows Removed by Filter: 5001`).

**3. Index ekle:**

```sql
CREATE INDEX idx_lab_customer_email ON lab_customer(email);
```

**4. Aynı sorguyu tekrar çalıştır:**

```sql
EXPLAIN ANALYZE SELECT * FROM lab_customer WHERE email = 'customer2500@example.com';
```

Gerçek çıktı:
```
Index Scan using idx_lab_customer_email on lab_customer  (cost=0.28..8.30 rows=1 width=45) (actual time=0.019..0.019 rows=1 loops=1)
  Index Cond: ((email)::text = 'customer2500@example.com'::text)
Execution Time: 0.043 ms
```
`Index Scan` - planlayıcı maliyeti 109.53'ten 8.30'a düştü, gerçek çalışma süresi ~6 kat
azaldı. 5000 satırda fark küçük (sub-millisaniye) ama **sorgu PLANININ DEĞİŞMESİ** (Seq
Scan → Index Scan) asıl ders - milyonlarca satırda bu fark saniyeler mertebesine çıkar.

**5. Temizlik (kendi ortamını temiz tutmak için):**

```sql
DROP INDEX idx_lab_customer_email;
DELETE FROM lab_customer;
```

**Composite / unique / partial index örnekleri** (kendi denemen için, bu proje bunları
henüz otomatik doğrulamadı):

```sql
-- Composite: (name, email) birlikte aranıyorsa
CREATE INDEX idx_lab_customer_name_email ON lab_customer(name, email);

-- Unique: email'in tekil olmasını DB seviyesinde zorla
CREATE UNIQUE INDEX idx_lab_customer_email_unique ON lab_customer(email);

-- Partial: sadece belirli bir alt kümeyi indeksle (ör. sadece aktif kayıtlar - bu şemada
-- "aktif" alanı yok, örnek amaçlı, id > 1000 ile göster)
CREATE INDEX idx_lab_customer_recent ON lab_customer(email) WHERE id > 1000;
```

**Index maliyeti unutulmamalı:** her index, her `INSERT`/`UPDATE`/`DELETE`'te de
güncellenmelidir (yazma maliyeti) ve disk yer kaplar - "her sütuna index ekle" doğru
strateji değildir, sadece gerçekten sorgu filtresi/join/order by'da kullanılan sütunlara.

---

## Normalization örneği (Faz 3, kavramsal)

Bu proje normalize edilmiş bir şema kullanıyor zaten (`lab_jpa_order` / `lab_jpa_order_item`
/ `lab_jpa_product` - sipariş, kalem ve ürün ayrı tablolar, 3NF'ye uygun). Unnormalized
alternatifi göstermek için (SADECE kavramsal, bu projede gerçek bir tablo değil):

```sql
-- UNNORMALIZED (yapma): tek tabloda tekrarlanan ürün bilgisi
-- order_id | customer_name | product_name | product_price | quantity
-- 1        | Ada           | Widget       | 9.99           | 2
-- 1        | Ada           | Gadget       | 19.99          | 1
-- Product bilgisi HER order_item satırında TEKRARLANIR - update anomaly riski
-- (Widget'ın fiyatı değişirse, TÜM satırların güncellenmesi gerekir, biri unutulursa
-- veri tutarsız hale gelir).

-- NORMALIZED (bu projenin gerçek şeması): lab_jpa_product ayrı tablo, lab_jpa_order_item
-- sadece product_id'ye referans verir - fiyat TEK yerde yaşar.
SELECT oi.id, o.customer_name, p.name, p.price, oi.quantity
FROM lab_jpa_order_item oi
JOIN lab_jpa_order o ON o.id = oi.order_id
JOIN lab_jpa_product p ON p.id = oi.product_id;
```

**Insert/Update/Delete anomaly'leri (unnormalized versiyonda olurdu, normalize şemada olmaz):**
- **Insert anomaly**: henüz hiç siparişi olmayan bir ürünü eklemek için (unnormalized'da)
  sahte bir sipariş satırı gerekirdi - normalize şemada `lab_jpa_product`'a bağımsız INSERT yeterli.
- **Update anomaly**: bir ürünün fiyatı değişirse, unnormalized'da O ÜRÜNÜ İÇEREN HER
  SATIRIN güncellenmesi gerekirdi - normalize şemada tek `UPDATE lab_jpa_product`.
- **Delete anomaly**: unnormalized'da son siparişi silmek, ürünün KENDİSİ hakkındaki
  bilgiyi de (yanlışlıkla) silebilirdi - normalize şemada ürün, siparişlerden bağımsız yaşar.
