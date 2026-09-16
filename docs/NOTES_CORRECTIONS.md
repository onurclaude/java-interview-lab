# Notes Corrections

Kullanıcının eski Java/Spring mülakat notlarındaki, sürüme/teknolojiye bağlı olarak
eskimiş veya baştan yanlış olan iddiaların Java 21 + Spring Boot 3.x + PostgreSQL
bağlamında denetlenmesi. Notlar "source of truth" kabul edilmedi - her düzeltme ya bu
projenin kendi kodunda/testinde doğrulanmış bir davranışa, ya da resmi spesifikasyona
(JLS, JPA, HTTP RFC'leri) dayanır.

Format: `NOTE SAID` (notlarda ima edilen/söylenen) → `CORRECT VERSION` (doğrusu) →
`WHY` (neden) → `INTERVIEW ANSWER` (mülakatta verilecek kısa, doğru cevap).

---

## 1. Green Threads vs Virtual Threads

**NOTE SAID:**
"Green threads, JVM'in kendi scheduler'ının yönettiği hafif thread'lerdir" (genellikle
modern "lightweight thread" kavramıyla karıştırılır).

**CORRECT VERSION:**
Green thread'ler **tarihsel bir kavramdır** - JDK 1.1'de (1996-1997), OS thread'leri
kullanmadan, JVM içinde userspace'te tamamen software olarak schedule edilen thread'lerdi.
JDK 1.3 (2000) itibarıyla Sun, tüm ana platformlarda bunları **native OS thread'lerle
DEĞİŞTİRDİ** - o zamandan JDK 21'e kadar (Virtual Threads gelene dek), Java thread'leri
HER ZAMAN 1:1 OS thread eşlemesiyle çalıştı ("platform thread"). **Virtual Thread'ler
(Java 21, JEP 444, final)**, green thread'lerin dönüşü DEĞİL - kavramsal olarak
benzerler (JVM tarafından yönetilen, OS thread'inden bağımsız), ama uygulama tamamen
farklı: bir Virtual Thread, blocking bir I/O operasyonuna girdiğinde, taşıdığı **carrier
platform thread'i BIRAKIR** (unmount), böylece o carrier thread başka bir virtual
thread'i çalıştırabilir; I/O tamamlandığında virtual thread uygun bir carrier thread'e
yeniden bağlanır (mount). Bu, `ForkJoinPool`'un continuation mekanizmasını kullanır -
green thread döneminde bu altyapı yoktu.

**WHY:**
JDK sürüm tarihini (green thread → 1.3'te terk edildi → 2004-2011 arası hiç "hafif
thread" yoktu → Virtual Threads Java 21'de) karıştırmak, "Java hep hafif thread'lere
sahipti" gibi yanlış bir sürekliliği ima eder.

**INTERVIEW ANSWER:**
"Green thread'ler 1990'ların JVM'lerinde vardı ve terk edildi; bugünkü Virtual Thread'ler
(Java 21) yepyeni bir mekanizma - blocking I/O'da carrier platform thread'i bırakıp
yeniden bağlanabilen continuation tabanlı thread'ler. İkisi arasında doğrudan bir soy
bağı yok, sadece 'OS thread'inden daha hafif' fikri ortak."

*(Bkz. Faz 3 runtime demo: `docs/DEBUGGER_LABS.md` "Virtual Threads" bölümü.)*

---

## 2. JRE Terminolojisi

**NOTE SAID:**
"JDK = compiler + tools + runtime, JRE = sadece runtime, JVM = bytecode execution" -
klasik, hâlâ çoğu yerde tekrarlanan üçlü.

**CORRECT VERSION:**
Bu tanım **Java 8 ve öncesi için doğruydu**, ama **Java 9'dan (2017) itibaren JDK
dağıtımları artık ayrı, indirilebilir bir "JRE" paketi İÇERMİYOR**. Java Platform Module
System (JPMS, JEP 220) ile Oracle ve OpenJDK, JDK'yı modüler hale getirdi ve
bağımsız JRE indirmesini kaldırdı - `jlink` aracı artık uygulamanıza özel, sadece
ihtiyaç duyduğunuz modülleri içeren KÜÇÜLTÜLMÜŞ özel bir runtime imajı oluşturmanıza
izin veriyor (klasik "tam JRE" yerine). Kavramsal olarak "JRE" (sınıf kütüphaneleri + JVM,
derleyici yok) hâlâ bir JDK kurulumunun İÇİNDE mevcuttur, ama artık ayrı bir ürün/indirme
değildir.

**WHY:**
2026'da (bu projenin bağlamı) bir adayın "JRE'yi ayrıca indirdim" demesi güncel değildir -
modern dağıtım (Java 21, Temurin/Corretto/Oracle) sadece JDK'dır, `jlink` ile custom
runtime imajları üretilir.

**INTERVIEW ANSWER:**
"JDK = JRE + geliştirme araçları (`javac`, `jlink`, vb.) - Java 8'e kadar doğruydu bu.
Java 9'dan beri ayrı bir JRE dağıtımı yok; JPMS ile JDK modüler oldu ve `jlink`,
uygulamaya özel küçültülmüş runtime imajları üretiyor. Bugün sadece JDK indirirsiniz."

---

## 3. GC Collectors

**NOTE SAID:**
"Java'nın garbage collector'ı" (tekil, sanki tek bir GC algoritması varmış gibi), ya da
eski CMS (Concurrent Mark Sweep) odaklı bir anlatım.

**CORRECT VERSION:**
Java 21'de birden fazla collector vardır ve **CMS Java 14'te (JEP 363) tamamen
KALDIRILDI**. Güncel manzara:
- **G1 (Garbage First)** — Java 9'dan beri **varsayılan** collector. Heap'i bölgelere
  (region) ayırır, çoğunlukla concurrent çalışır, öngörülebilir duraklama hedefleri
  (`-XX:MaxGCPauseMillis`) sunar.
- **ZGC** — Java 15'te (JEP 377) production-ready oldu; Java 21'de (JEP 439) **generational
  ZGC** geldi. Alt-milisaniye duraklama süreleri hedefler, çok büyük heap'lerde (TB
  mertebesinde) bile ölçeklenir.
- **Shenandoah** — Red Hat'in düşük-duraklama collector'ı (OpenJDK'de mevcut, ama
  Oracle JDK build'lerinde değil).
- **Serial/Parallel GC** — hâlâ var, küçük heap'ler veya throughput-öncelikli batch
  işler için.

**WHY:**
"CMS" veya "tek bir GC" demek, hem kaldırılmış bir collector'a atıfta bulunmak hem de
Java'nın 2010'lardan beri "seç ve kullan" felsefesine geçtiğini kaçırmak anlamına gelir.

**INTERVIEW ANSWER:**
"Java 21'de varsayılan G1 - bölgesel, çoğunlukla concurrent, duraklama hedefi
konfigüre edilebilir. Çok düşük duraklama gerekiyorsa (ör. büyük heap, gerçek zamanlıya
yakın gereksinim) generational ZGC (Java 21'de production) tercih edilir. CMS, Java 14'te
kaldırıldı - artık mevcut değil."

---

## 4. HashMap Implementation Detayları

**NOTE SAID:**
"HashMap O(1)'dir" (başka bir açıklama olmadan bırakılır).

**CORRECT VERSION:**
`HashMap.get()`/`put()` **ortalama (amortized) O(1)**'dir, ama:
- **Collision** durumunda (aynı bucket'a düşen farklı key'ler), o bucket içindeki arama
  O(k) olur (k = o bucket'taki eleman sayısı).
- Java 8'den beri (JEP-benzeri bir iyileştirme, resmi bir JEP değil ama `HashMap`'in kendi
  iç değişikliği), bir bucket'taki zincir **8 elemana ulaşırsa VE toplam tablo kapasitesi
  en az 64 ise**, o bucket bir linked list yerine kırmızı-siyah ağaca (red-black tree)
  dönüştürülür ("treeification") - bu, worst-case'i O(n)'den **O(log n)**'e düşürür (kötü
  `hashCode()` implementasyonlarına veya kasıtlı hash-collision saldırılarına karşı).
- **Load factor** (varsayılan `0.75`) - tablo doluluk oranı bu eşiği aşınca, kapasite
  2 katına çıkarılır (resize) ve TÜM elemanlar yeniden hash'lenir (bu tek resize
  operasyonu O(n)'dir, ama amortize edildiğinde ortalama O(1)'i bozmaz).
- Bu implementasyon detayları (treeification eşiği=8, untreeify eşiği=6, min
  treeify capacity=64) **JavaDoc'ta belgelenmiştir ama JLS/JPA gibi bir spesifikasyonun
  parçası değildir** - JDK sürümüne göre değişebilir, garanti edilmez.

**WHY:**
"O(1)" demek doğru ama eksiktir - bir senior mülakatında collision/resize/treeification
sorulmadan geçilmez.

**INTERVIEW ANSWER:**
"Ortalama O(1), ama collision'lar varsa bucket içi arama O(k)'dır. Java 8+, kötü hash
dağılımına karşı 8+ elemanlı bucket'ları (kapasite ≥64 ise) kırmızı-siyah ağaca çevirir -
worst-case O(log n)'e düşer. Load factor 0.75 aşılınca resize olur - O(n) tek seferlik
maliyet, ama amortize O(1)'i bozmaz."

*(Bkz. Faz 3 runtime demo: aşağıda "HashMap Internals" bölümü ve
`com.interviewlab.collectionstopic.HashMapInternalsDemo`.)*

---

## 5. Clustered Index Terminolojisi (PostgreSQL bağlamında)

**NOTE SAID:**
"Clustered index, satırları fiziksel olarak indekse göre sıralı tutar" (SQL Server/MySQL
InnoDB terminolojisi, PostgreSQL'e doğrudan uygulanır gibi anlatılır).

**CORRECT VERSION:**
PostgreSQL'in index mimarisi SQL Server'dan **temelden farklıdır**:
- SQL Server/MySQL (InnoDB): birincil anahtar (PK) OTOMATİK olarak clustered index'tir -
  tablo verisi, PK indeksinin YAPRAK düğümlerinde fiziksel olarak saklanır. Tabloda
  ikinci bir clustered index OLAMAZ.
- **PostgreSQL: hiçbir index varsayılan olarak clustered değildir.** Tüm index'ler
  (PK dahil) "heap" adı verilen ayrı bir tablo depolama alanına işaret eden **ayrı
  yapılardır** (B-tree, genellikle). PostgreSQL'in `CLUSTER` komutu VAR (`CLUSTER
  tablename USING indexname;`), ama bu **TEK SEFERLİK, manuel bir operasyondur** - tabloyu
  o an için belirtilen index sırasına göre fiziksel olarak yeniden yazar. Sonraki
  INSERT/UPDATE'ler bu sırayı KORUMAZ (SQL Server'ın sürekli garantisinin aksine) -
  PostgreSQL zamanla "declustered" hale gelir, tekrar `CLUSTER` çalıştırmak gerekir.

**WHY:**
"PostgreSQL'de PK = clustered index" demek yanlıştır ve bir PostgreSQL-spesifik
mülakatta ciddi bir hata olarak görülür.

**INTERVIEW ANSWER:**
"SQL Server'da PK otomatik clustered index'tir, veri fiziksel olarak o sırada saklanır.
PostgreSQL'de HİÇBİR index varsayılan olarak clustered değil - hepsi heap'e işaret eden
ayrı yapılar. PostgreSQL'in `CLUSTER` komutu var ama tek seferlik, sürekli garanti
vermiyor - yeni satırlar eklendikçe tablo tekrar 'declustered' oluyor."

*(Bkz. Faz 3: `docs/DB_LABS.md` "Indexes" bölümü.)*

---

## 6. CAP Teoremi ve Consistency

**NOTE SAID:**
"CAP teoremi: Consistency, Availability, Partition tolerance - üçünden ikisini seçebilirsin"
(genellikle ACID'in Consistency'siyle aynı kavrammış gibi anlatılır).

**CORRECT VERSION:**
CAP'teki **Consistency**, ACID'teki **Consistency** ile AYNI ŞEY DEĞİLDİR:
- **ACID Consistency**: bir transaction, veritabanını bir geçerli durumdan başka bir
  geçerli duruma götürür (constraint'ler, foreign key'ler, uygulama invariant'ları
  korunur).
- **CAP Consistency**: daha spesifik olarak **linearizability**/**strong consistency**
  anlamına gelir - dağıtık bir sistemde, HERHANGİ bir node'a yapılan bir okuma, en son
  yazılan değeri (ya da bir hata) döndürür, asla bayat bir değer değil.

Ayrıca CAP teoremi **sadece network partition GERÇEKLEŞTİĞİNDE** geçerlidir - normal
çalışma sırasında (partition yokken) bir sistem hem C hem A olabilir (ve genelde
olur). "İkisinden birini seç" ifadesi, partition anındaki bir zorunlu tercihi tarif eder,
sistemin GENEL tasarım felsefesini değil.

**WHY:**
CAP'in C'sini ACID'in C'siyle karıştırmak, "bu veritabanı CAP-consistent değil, o yüzden
ACID garantisi de yok" gibi yanlış çıkarımlara yol açar - ikisi bağımsız kavramlardır
(bir sistem ACID-compliant AMA CAP-AP olabilir, örn. tek-node PostgreSQL partition
kavramı olmadığı için CAP dışıdır ama tam ACID'dir).

**INTERVIEW ANSWER:**
"CAP'teki Consistency, linearizability demek - her node aynı, en güncel veriyi görür.
ACID'teki Consistency ise constraint'lerin korunması demek - farklı kavramlar. Ayrıca CAP
sadece network partition anında geçerli bir trade-off; partition yokken bir sistem hem
C hem A olabilir."

---

## 7. BASE

**NOTE SAID:**
"BASE, ACID'in tam tersidir."

**CORRECT VERSION:**
BASE (**B**asically **A**vailable, **S**oft state, **E**ventual consistency), ACID'in
"zıttı" değil, **farklı bir tasarım felsefesidir** - CAP teoreminde Availability'yi
Consistency'ye tercih eden dağıtık sistemler için (ör. DynamoDB, Cassandra). ACID ve BASE
bir spektrumun iki ucu gibi düşünülebilir ama "tam tersi" demek yanlıştır çünkü:
- **Basically Available**: sistem her zaman bir yanıt verir (garantili en güncel olmasa
  bile) - ACID'in "Isolation" ile çelişmez, farklı bir eksende çalışır.
- **Soft state**: sistemin durumu, açık bir yazma olmadan da zamanla değişebilir (ör.
  TTL ile expire olan cache) - bu ACID'de yok, ama ACID'in bir "zıttı" da değil, ilgisiz
  bir kavram.
- **Eventual consistency**: yazma durduktan SONRA, tüm node'lar er ya da geç aynı değere
  yakınsar - ACID Isolation'ın kesin anlık garantisinin daha gevşek bir versiyonu.

**WHY:**
"Tam tersi" ifadesi, BASE sistemlerin veri bütünlüğünü hiç önemsemediği gibi yanlış bir
izlenim verir - gerçekte sadece FARKLI bir tutarlılık/kullanılabilirlik dengesi seçerler.

**INTERVIEW ANSWER:**
"BASE, ACID'in zıttı değil, CAP'te Availability'yi Consistency'ye tercih eden sistemlerin
felsefesi - eventual consistency ile 'okumalar her zaman en güncel değeri görmeyebilir
ama sistem her zaman yanıt verir' dengesi kurar. Genelde yüksek ölçekli, coğrafi olarak
dağıtık NoSQL sistemlerinde görülür."

---

## 8. HTTP 401 vs 403

**NOTE SAID:**
"401 = giriş yapmamışsın, 403 = yetkin yok" (genelde doğru ama sıkça ters karıştırılır
veya "401 = kimlik doğrulama hatası, kullanıcı yok" gibi eksik anlatılır).

**CORRECT VERSION (RFC 7235 / RFC 7231):**
- **401 Unauthorized**: "Unauthenticated" demek daha doğru olurdu (isimlendirme tarihsel
  bir hata) - istek, geçerli authentication credential'ları İÇERMİYOR ya da sağlanan
  credential'lar geçersiz/süresi dolmuş. Sunucu, `WWW-Authenticate` header'ı ile HANGİ
  authentication şemasının kullanılması gerektiğini belirtmek ZORUNDADIR (RFC 7235 §3.1).
- **403 Forbidden**: sunucu isteği ANLADI ve kimliği (varsa) biliyor OLABİLİR, ama
  isteği yerine getirmeyi REDDEDİYOR - yetki yetersiz. RFC 7231 §6.5.3 açıkça der ki:
  sunucu isteyen tarafça isteği "neden" reddettiğini açıklamak İSTEMİYORSA (güvenlik
  nedeniyle), bunun yerine 404 döndürebilir.
- Pratikte: 401 → "önce giriş yap", 403 → "giriş yaptın (ya da yapmana gerek yok) ama bu
  kaynağa erişimin yok".

**WHY:**
"401 = kullanıcı yok" gibi basitleştirmeler, "zaten giriş yapmış ama yetkisiz bir
kullanıcıya ne dönülür" sorusunda kafa karışıklığına yol açar (doğrusu: 403, 401 değil).

**INTERVIEW ANSWER:**
"401 Unauthorized aslında 'unauthenticated' anlamına gelir - credential eksik/geçersiz,
`WWW-Authenticate` header'ı ile hangi auth şemasının bekleneceği belirtilir. 403 Forbidden,
kimlik biliniyor (ya da önemli değil) ama yetki yetersiz demektir. Bir zaten-giriş-yapmış
ama yetkisiz kullanıcıya 403 dönülür, 401 değil."

---

## 9. Spring Proxy Sınırlamaları

**NOTE SAID:**
"Spring AOP, final class/method'ları proxy'leyemez" (genel, sürümsüz bir iddia).

**CORRECT VERSION:**
Bu iddia **sadece CGLIB (class-based) proxy** için doğrudur, ve hatta o durumda bile
NÜANSLIDIR:
- **JDK dynamic proxy** (interface tabanlı): hedef sınıf bir interface implemente
  ediyorsa kullanılır, `final` sınıf/metod sorunu YOKTUR çünkü proxy, interface'i
  implemente eden TAMAMEN AYRI bir sınıftır (hedef sınıfı extend etmez).
- **CGLIB proxy** (subclass tabanlı, Spring 3.2+'dan beri Spring'in kendi jar'ına
  gömülüdür, ayrı bağımlılık gerekmez): hedef sınıfı **runtime'da extend eder** - bu
  yüzden `final class` proxy'lenemez (extend edilemez) ve `final`/`private`/`static`
  metodlar override edilemediği için advice UYGULANMAZ (sessizce atlanır, hata
  vermez).
- Spring Boot varsayılan olarak (`proxyTargetClass` implicit true olduğu durumlar
  hariç) bir bean bir interface implemente ediyorsa JDK proxy, etmiyorsa CGLIB
  kullanır - `@EnableAspectJAutoProxy(proxyTargetClass = true)` ile HER ZAMAN CGLIB
  zorlanabilir.
- **Spring AOP proxy'lerinin ORTAK sınırlaması (ikisi için de geçerli)**, bu projenin
  ana teması olan **self-invocation**'dır - final ile ilgisi yoktur, proxy TÜRÜNDEN
  bağımsızdır.

**WHY:**
"final proxy'lenemez" demek eksik/yanıltıcıdır - hangi proxy türünün kullanıldığına
bağlıdır, ve projenin gerçekte defalarca karşılaştığı, çok daha yaygın sınırlama
self-invocation'dır (final class değil).

**INTERVIEW ANSWER:**
"final sınırlaması sadece CGLIB (subclass) proxy'ler için geçerli - final class extend
edilemez, final metod override edilemez. JDK dynamic proxy (interface tabanlı) bu
sorunu hiç yaşamaz çünkü hedefi extend etmez, ayrı bir sınıftır. Ama her iki proxy
türünün de ORTAK ve çok daha sık karşılaşılan sınırlaması self-invocation - `this.foo()`
hiçbir zaman proxy'den geçmez."

*(Bu projenin `docs/aop.md`, `docs/transactions.md`, `docs/async.md` dosyalarının hepsi
bunu gerçek kodla kanıtlıyor.)*

---

## 10. @Transactional(readOnly = true)

**NOTE SAID:**
"`readOnly = true`, veritabanının write yapmasını FİZİKSEL OLARAK engeller" (bir
güvenlik/koruma mekanizması gibi anlatılır).

**CORRECT VERSION:**
`readOnly = true`, veritabanı seviyesinde bir **garanti DEĞİL**, bir **optimizasyon
İPUCUDUR**:
- Hibernate'e: flush mode'u `MANUAL`/`COMMIT`'e ayarlayarak gereksiz dirty-checking
  flush'larını atla, bazı durumlarda persistence context'i daha hafif tut.
- JDBC driver'a: bazı driver'lar (PostgreSQL JDBC dahil, `Connection.setReadOnly(true)`
  çağrısı üzerinden) bunu bir performans ipucu olarak kullanabilir (ör. bağlantı havuzu
  yönlendirmesi, replica'ya yönlendirme gibi altyapı seviyesinde).
- **AMA**: bir `readOnly = true` transaction içinde açıkça bir `INSERT`/`UPDATE`/`DELETE`
  çalıştırırsanız, PostgreSQL + standart JDBC sürücüsüyle bu genellikle YİNE DE ÇALIŞIR
  (bazı veritabanları/driver'lar bunu reddeder, ama bu evrensel bir garanti değildir -
  veritabanına/driver'a bağlıdır). Gerçek bir "hard" salt-okunur garanti istiyorsanız,
  veritabanı seviyesinde bir salt-okunur replica'ya bağlanmak ya da veritabanının kendi
  yetkilendirme mekanizmasını (salt-okunur DB kullanıcısı) kullanmak gerekir.

**WHY:**
`readOnly = true`'yu bir güvenlik sınırı sanmak, birinin "zaten readOnly işaretledim,
yanlışlıkla yazamaz" varsayımıyla gerçek bir yetkilendirme kontrolü eklemeyi atlamasına
yol açabilir.

**INTERVIEW ANSWER:**
"readOnly=true bir GARANTİ değil, bir optimizasyon ipucu - Hibernate'in dirty-checking
flush'larını atlamasını sağlar, bazı driver'larda replica-routing gibi altyapısal
kararlara işaret edebilir. Ama içinde açıkça bir UPDATE çalıştırırsam, PostgreSQL +
standart JDBC ile bu genellikle yine de çalışır - fiziksel bir write engeli değil.
Gerçek salt-okunur garanti için DB kullanıcı yetkisi ya da salt-okunur replica gerekir."

---

## 11. Cache Stratejileri

**NOTE SAID:**
"Cache Aside, Read Through, Write Through, Write Behind hepsi aynı şey, hangisini
kullanırsan kullan sonuç aynı" (ya da stratejiler birbirine karıştırılır).

**CORRECT VERSION:**
Dördü ÖNEMLİ ÖLÇÜDE farklı tutarlılık/performans garantileri verir:
- **Cache-Aside (Lazy Loading)**: uygulama kodu cache'i YÖNETİR - okuma: önce cache'e
  bak, yoksa DB'den oku ve cache'e yaz; yazma: DB'ye yaz, sonra cache'i invalidate et
  (ya da güncelle). Cache kütüphanesi DB'den habersizdir. En yaygın, en esnek, ama
  uygulama kodunda tutarlılık hatası riski en yüksek olan (invalidation unutulursa
  bayat veri).
- **Read-Through**: cache KÜTÜPHANESİNİN KENDİSİ, cache miss'te DB'den okumayı bilir -
  uygulama sadece cache'e sorar, cache DB'yi kendi yönetir. Cache-Aside'a benzer
  ama sorumluluk cache katmanına taşınmıştır.
- **Write-Through**: her write, SENKRON olarak hem cache'e hem DB'ye yazılır (cache
  kütüphanesi ikisini de yönetir) - cache her zaman güncel, ama her yazma iki katmana
  gider (gecikme artar).
- **Write-Behind (Write-Back)**: write ÖNCE cache'e yazılır, DB'ye yazma ASENKRON
  olarak ERTELENİR (batch'lenebilir) - en düşük yazma gecikmesi, ama DB'ye yazılmadan
  önce cache/uygulama çökerse VERİ KAYBI RİSKİ vardır.

**WHY:**
Bu dört strateji arasındaki fark, tam olarak "tutarlılık ne kadar hızlı sağlanır" ve
"veri kaybı riski nerede" sorularının cevabıdır - bir mülakatta "hangisini seçersin"
sorusu bu trade-off'ları bilmeyi gerektirir.

**INTERVIEW ANSWER:**
"Cache-Aside'da uygulama cache'i yönetir, en esnek ama tutarlılık riski uygulama kodunun
elinde. Write-Through her yazmada cache+DB'yi senkron günceller - tutarlı ama yavaş.
Write-Behind yazmayı cache'e hemen yapar, DB'ye asenkron erteler - hızlı ama çökme
anında veri kaybı riski var. Hangisini seçeceğim, 'ne kadar taze veri' ile 'ne kadar
yazma gecikmesi kabul edilebilir' dengesine bağlı."

---

## Denetim durumu

Yukarıdaki 11 madde, kullanıcının açıkça "kontrol et" dediği listenin TAMAMINI kapsar.
Bu proje ilerledikçe (Faz 3'ün geri kalanı) yeni denetimler bu dosyaya eklenmeye devam
edecektir - her yeni Faz 3 konusu eklenirken, notlarda o konuyla ilgili bilinen bir
yanlış/eski iddia varsa buraya not düşülür.
