# Garbage Collection (Java 21 bağlamında)

## Temeller: reachability, GC roots

Bir nesne, **GC roots**'tan (local değişkenler [thread stack'lerindeki], static alanlar,
aktif thread'ler, JNI referansları) başlayan bir referans zincirinde ulaşılabilir
("reachable") olduğu sürece GC tarafından toplanmaz. Ulaşılabilir OLMAYAN ("unreachable")
her şey, bir sonraki GC döngüsünde toplanmaya UYGUN hale gelir (ne zaman GERÇEKTEN
toplanacağı garanti edilmez - `System.gc()` bile sadece bir "öneridir", garanti değildir).

```
GC Root (ör. bir static alan)
   |
   v
Object A  ---->  Object B  ---->  Object C     (hepsi REACHABLE)

Object D  ---->  Object E                       (D'ye hiçbir GC root'tan yol yok
                                                   -> UNREACHABLE -> GC'ye aday)
```

### Güvenli bir "yanlışlıkla reachable tutma" demosu (memory leak)

Bu proje, gerçek bir OOM ÜRETMEDEN (bilinçli - production'da bir OOM üretmek riskli ve
gereksiz), bir nesnenin normalde GC'ye aday olması gerekirken bir `static` collection
tarafından YANLIŞLIKLA reachable tutulmasının NASIL mümkün olduğunu gösterir:
`com.interviewlab.javacore.staticdemo.bad.SharedMutableStaticListService` - `static`
`users` listesi, JVM'in ömrü boyunca YAŞAR; ona eklenen her nesne, listeden açıkça
`remove()` edilmediği sürece GC'ye aday OLAMAZ - hiçbir yerel değişken ona artık
işaret etmese bile. Bu, `WeakHashMap`/`WeakReference` kullanılmadığı sürece "cache"
olarak kullanılan static collection'ların KLASİK sızıntı riskidir (bkz.
`docs/NOTES_CORRECTIONS.md` ve `JavaCoreTest.shouldLoseElementsWithUnsynchronizedStaticList`
- aynı sınıf, hem concurrency hem reachability dersi için kullanılıyor).

## Collector'lar (Java 21)

| Collector | Durum (Java 21) | Ne zaman kullanılır |
|---|---|---|
| **G1 (Garbage First)** | **Varsayılan** (Java 9'dan beri) | Genel amaçlı, öngörülebilir duraklama hedefi (`-XX:MaxGCPauseMillis`) isteyen çoğu uygulama |
| **ZGC (generational, Java 21'de JEP 439)** | Mevcut, opt-in (`-XX:+UseZGC`) | Çok büyük heap (TB mertebesi), alt-milisaniye duraklama gereksinimi |
| **Shenandoah** | Mevcut (OpenJDK build'lerinde), opt-in | Düşük duraklama, ZGC'ye alternatif |
| **Serial** | Mevcut, opt-in (`-XX:+UseSerialGC`) | Küçük heap, tek CPU'lu ortamlar (ör. konteynerler) |
| **Parallel** | Mevcut, opt-in (`-XX:+UseParallelGC`) | Duraklamadan çok THROUGHPUT'un önemli olduğu batch işler |
| ~~CMS~~ | **Java 14'te (JEP 363) KALDIRILDI** | Artık mevcut değil - notlarda geçiyorsa güncelliğini yitirmiştir |

## Stop-The-World (STW) ve safepoint — aşırı basitleştirmeden kaçının

"GC çalışırken HER ŞEY TAMAMEN DURUR" doğru ama EKSİK bir ifadedir. Gerçekte:
- Bir **safepoint**, tüm uygulama thread'lerinin GÜVENLİ bir şekilde duraklatılabileceği
  bir JVM iç noktasıdır (thread'ler rastgele bir bytecode talimatının ortasında değil,
  belirli noktalarda durur).
- **Bazı GC fazları GERÇEKTEN Stop-The-World'dür** (ör. G1'in "evacuation pause"ı - canlı
  nesneleri bir bölgeden diğerine taşırken uygulama thread'leri GERÇEKTEN durur, çünkü
  referansları güvenle güncellemek için bu gerekir).
- **Ama modern collector'ların (G1, ZGC, Shenandoah) çoğu fazı CONCURRENT'tir** -
  uygulama thread'leriyle AYNI ANDA çalışır (ör. mark fazı - hangi nesnelerin canlı
  olduğunu bulma). ZGC özellikle, evacuation'ı bile büyük ölçüde concurrent yapacak
  şekilde tasarlanmıştır - bu yüzden "alt-milisaniye duraklama" iddiası edebilir.
- **Doğru ifade:** "GC'nin bazı KISA fazları Stop-The-World'dür, ama modern collector'lar
  bunu minimize etmek için mümkün olan her şeyi concurrent yapar."

## GC loglarını açma

```bash
# Java 9+ unified logging (eski -XX:+PrintGCDetails yerine)
java -Xlog:gc*:file=gc.log:time,uptime,level,tags -jar app.jar
```

Bu proje ile denemek için (uygulamayı Maven ile başlatırken JVM argümanı ekleyerek):
```bash
MAVEN_OPTS="-Xlog:gc*:stdout:time,level,tags" ./mvnw spring-boot:run
```

## Sık Sorulan Mülakat Soruları

- **Q:** Java'nın varsayılan GC'si nedir? — G1, Java 9'dan beri.
- **Q:** CMS hâlâ kullanılabilir mi? — Hayır, Java 14'te (JEP 363) kaldırıldı.
- **Q:** GC çalışırken uygulama tamamen durur mu? — Sadece bazı kısa fazlarda (STW); modern
  collector'ların çoğu fazı concurrent'tir.
- **Q:** `System.gc()` çağırmak GC'yi zorlar mı? — Hayır, sadece bir ÖNERİDİR (JVM'in
  dikkate alma zorunluluğu yoktur).

## 30 Saniyelik Mülakat Cevabı

"Java 21'de varsayılan collector G1 - bölgesel, çoğunlukla concurrent, öngörülebilir
duraklama hedefi ayarlanabilir. Çok düşük duraklama gerekiyorsa generational ZGC (Java
21'de mevcut) tercih edilir. CMS artık yok, Java 14'te kaldırıldı. 'GC her şeyi durdurur'
aşırı basitleştirmesinden kaçınıyorum - sadece belirli kısa fazlar (ör. evacuation pause)
Stop-The-World, mark gibi fazlar concurrent çalışabiliyor."

## Takip Soruları

- G1'in bölge (region) tabanlı yaklaşımı, eski nesil/genç nesil (generational) GC'den nasıl farklı?
- ZGC'nin alt-milisaniye duraklamayı nasıl başardığı (renklendirilmiş işaretçiler - colored pointers) hakkında ne biliyorsun?
