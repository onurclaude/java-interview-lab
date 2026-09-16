package com.interviewlab.javacore;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewlab.javacore.hashmapinternals.CollidingKey;
import com.interviewlab.javacore.staticdemo.bad.SharedMutableStaticListService;
import com.interviewlab.javacore.staticdemo.good.SynchronizedStaticListService;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Java Core / JVM konularının çalıştırılabilir kanıtı - docs/NOTES_CORRECTIONS.md ve
 * docs/DEBUGGER_LABS.md ile birlikte okunmalı. Bu sınıf bilinçli olarak Spring context'i
 * ya da DB gerektirmez - konu tamamen çıplak JVM davranışıdır.
 */
class JavaCoreTest {

    // ---------- static: paylaşılan class-level state ----------

    @Test
    void shouldLoseElementsWithUnsynchronizedStaticList() throws Exception {
        SharedMutableStaticListService.clear();
        SharedMutableStaticListService service = new SharedMutableStaticListService();
        int threads = 20;
        int addsPerThread = 500;
        runConcurrently(threads, () -> {
            for (int i = 0; i < addsPerThread; i++) {
                service.addUser("user");
            }
        });

        assertThat(SharedMutableStaticListService.userCount())
                .as("senkronize olmayan static ArrayList, eşzamanlı add() altında eleman kaybetmeli (ya da worst-case exception)")
                .isLessThan(threads * addsPerThread);
    }

    @Test
    void shouldNeverLoseElementsWithConcurrentSafeStaticList() throws Exception {
        SynchronizedStaticListService.clear();
        SynchronizedStaticListService service = new SynchronizedStaticListService();
        int threads = 20;
        int addsPerThread = 500;
        runConcurrently(threads, () -> {
            for (int i = 0; i < addsPerThread; i++) {
                service.addUser("user");
            }
        });

        assertThat(SynchronizedStaticListService.userCount())
                .as("CopyOnWriteArrayList, eşzamanlı add() altında hiçbir eleman kaybetmemeli")
                .isEqualTo(threads * addsPerThread);
    }

    // ---------- HashMap internals: collision, load factor, equals/hashCode contract ----------

    @Test
    void shouldStoreBothCollidingKeysSeparatelyUsingEqualsWithinTheSameBucket() {
        Map<CollidingKey, String> map = new HashMap<>();
        CollidingKey keyA = new CollidingKey("A");
        CollidingKey keyB = new CollidingKey("B");

        map.put(keyA, "value-A");
        map.put(keyB, "value-B");

        assertThat(keyA.hashCode())
                .as("iki key de KASITLI olarak aynı hash'e sahip - aynı bucket'a düşmeleri garanti")
                .isEqualTo(keyB.hashCode());
        assertThat(map)
                .as("aynı bucket'taki iki farklı key, equals() ile doğru şekilde ayırt edilip AYRI saklanmalı")
                .hasSize(2);
        assertThat(map.get(new CollidingKey("A"))).isEqualTo("value-A");
        assertThat(map.get(new CollidingKey("B"))).isEqualTo("value-B");
    }

    @Test
    void shouldTreeifyBucketWhenCollisionCountReachesThresholdWithSufficientCapacity() throws Exception {
        // Treeification İKİ koşul GEREKTİRİR (JDK'nın HashMap kaynak kodunda sabitler):
        // TREEIFY_THRESHOLD=8 (bir bucket'taki eleman sayısı) VE MIN_TREEIFY_CAPACITY=64
        // (tablonun TOPLAM kapasitesi) - kapasite küçükse, HashMap bucket'ı ağaca çevirmek
        // yerine ÖNCE tabloyu resize etmeyi dener (treeifyBin() içindeki kontrol).
        Map<CollidingKey, String> map = new HashMap<>(64); // kapasiteyi doğrudan 64 yap
        for (int i = 0; i < 9; i++) {
            map.put(new CollidingKey("k" + i), "v" + i); // hepsi AYNI bucket'a düşer (hashCode()=42 sabit)
        }
        assertThat(map).hasSize(9);

        Field tableField = HashMap.class.getDeclaredField("table");
        tableField.setAccessible(true);
        Object[] table = (Object[]) tableField.get(map);
        assertThat(table.length).as("kapasite 64 olmalı").isEqualTo(64);

        int bucketIndex = (table.length - 1) & spreadHash(new CollidingKey("k0").hashCode());
        Object bucketHead = table[bucketIndex];
        assertThat(bucketHead)
                .as("9 çakışan key (eşik=8) + kapasite>=64 (eşik=64) sonrası, bucket'ın baş düğümü "
                        + "artık sıradan bir Node DEĞİL, bir TreeNode (kırmızı-siyah ağaç) olmalı")
                .isNotNull()
                .satisfies(node -> assertThat(node.getClass().getSimpleName()).isEqualTo("TreeNode"));
    }

    /** HashMap.hash(key) ile aynı spread fonksiyonu - bucket index'ini elle hesaplamak için. */
    private static int spreadHash(int h) {
        return h ^ (h >>> 16);
    }

    @Test
    void shouldTriggerResizeWhenLoadFactorThresholdIsExceeded() {
        // Varsayılan initial capacity 16, load factor 0.75 -> 12. eleman eklenince resize (32) tetiklenir.
        Map<Integer, String> map = new HashMap<>();
        for (int i = 0; i < 13; i++) {
            map.put(i, "v" + i);
        }
        assertThat(map)
                .as("resize sonrası tüm elemanlar erişilebilir kalmalı - hiçbiri kaybolmamalı")
                .hasSize(13);
        for (int i = 0; i < 13; i++) {
            assertThat(map.get(i)).isEqualTo("v" + i);
        }
    }

    // ---------- String Pool ----------

    @Test
    void shouldShareSameInternedInstanceForStringLiterals() {
        String a = "java";
        String b = "java";
        assertThat(a).as("iki aynı string literal, derleme zamanında AYNI pool instance'ına interned edilir").isSameAs(b);
    }

    @Test
    void shouldCreateDistinctInstanceWithNewStringButEqualValue() {
        String pooled = "java";
        String heap = new String("java");
        assertThat(pooled)
                .as("new String(...), pool'dan bağımsız YENİ bir heap nesnesi oluşturur")
                .isNotSameAs(heap);
        assertThat(pooled.equals(heap)).as("değerler eşit - equals() bunu doğru raporlar").isTrue();
    }

    @Test
    void shouldReturnPooledInstanceWhenInterned() {
        String pooled = "java";
        String heap = new String("java");
        assertThat(heap.intern())
                .as("intern(), pool'daki mevcut instance'a referansı döndürür")
                .isSameAs(pooled);
    }

    // ---------- Integer Cache ----------

    @Test
    void shouldReuseCachedInstanceWithinDefaultRange() {
        Integer a = 127;
        Integer b = 127;
        assertThat(a)
                .as("JLS 5.1.7, autoboxing'in -128..127 aralığını CACHE'lemesini GARANTİ eder")
                .isSameAs(b);
    }

    @Test
    void shouldCreateDistinctInstancesOutsideDefaultCacheRange() {
        Integer a = 128;
        Integer b = 128;
        assertThat(a)
                .as("128, varsayılan cache aralığının (-128..127) dışında - autoboxing yeni bir instance oluşturur")
                .isNotSameAs(b);
        assertThat(a.equals(b)).as("değer eşitliği hâlâ doğru - == kimlik, equals() değer karşılaştırır").isTrue();
    }

    // ---------- Virtual Threads (Java 21) vs Platform Threads ----------

    @Test
    void shouldMarkVirtualThreadsDifferentlyFromPlatformThreads() throws InterruptedException {
        boolean[] isVirtual = new boolean[1];
        boolean[] isPlatform = new boolean[1];

        Thread virtualThread = Thread.ofVirtual().unstarted(() -> isVirtual[0] = Thread.currentThread().isVirtual());
        virtualThread.start();
        virtualThread.join();

        Thread platformThread = Thread.ofPlatform().unstarted(() -> isPlatform[0] = !Thread.currentThread().isVirtual());
        platformThread.start();
        platformThread.join();

        assertThat(isVirtual[0]).as("Thread.ofVirtual() ile oluşturulan thread isVirtual()==true olmalı").isTrue();
        assertThat(isPlatform[0]).as("Thread.ofPlatform() ile oluşturulan thread isVirtual()==false olmalı").isTrue();
    }

    @Test
    void shouldCompleteManyBlockingVirtualThreadsWithoutExhaustingPlatformThreads() throws InterruptedException {
        // Bilimsel bir benchmark İDDİASI değil - sadece 2.000 sanal thread'in her biri kısa bir
        // blocking sleep yaptığında, hepsinin makul bir sürede tamamlanabildiğini kanıtlıyor.
        // Bunu AYNI sayıda platform thread ile yapmak - her biri kendi ~1MB'lık OS stack'ini
        // tüketir - pratikte OS thread limitlerine çarpabilir; sanal thread'ler bunu
        // continuation + carrier thread paylaşımıyla önler (bkz. docs/NOTES_CORRECTIONS.md #1).
        int taskCount = 2000;
        CountDownLatch done = new CountDownLatch(taskCount);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < taskCount; i++) {
                executor.submit(() -> {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            boolean completed = done.await(30, TimeUnit.SECONDS);
            assertThat(completed)
                    .as("2000 bloklayan görev, sanal thread'lerle (her biri kendi OS thread'ini tüketmeden) tamamlanmalı")
                    .isTrue();
        }
    }

    private static void runConcurrently(int threadCount, Runnable task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threadCount);
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    awaitUninterruptibly(start);
                    try {
                        task.run();
                    } finally {
                        done.countDown();
                    }
                });
            }
            ready.await();
            start.countDown();
            done.await(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
