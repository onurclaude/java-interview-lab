package com.interviewlab.javacore.staticdemo.bad;

import java.util.ArrayList;
import java.util.List;

/**
 * NE YANLIŞ?
 * {@code users}, instance alanı değil {@code static} bir alandır ve senkronizasyon olmadan
 * mutate edilir.
 *
 * <p>NEDEN YANLIŞ?
 * {@code static} bir alan, sınıfın TÜM instance'ları arasında - hatta hiç instance
 * yaratılmasa bile - tek bir paylaşılan kopyadır (class-level state, instance-level değil).
 * Ham bir {@code ArrayList}, thread-safe değildir: eşzamanlı {@code add()} çağrıları iç
 * dizi state'ini bozabilir (kayıp eleman, `ArrayIndexOutOfBoundsException`, hatta sonsuz
 * döngü - `ArrayList` iç kaynak kodunda belgelenen bilinen bir risktir).
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Aynı JVM'de çalışan tamamen alakasız iki request handler, aynı static listeyi paylaşır -
 * biri diğerinin verisini görür/bozar. Bu genelde "neden bu kullanıcı listesinde başkasının
 * verisi var?" gibi açıklanamaz bug raporlarına yol açar.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code StaticStateTest.shouldLoseElementsWithUnsynchronizedStaticList()}, çok sayıda
 * eşzamanlı thread'den {@code addUser()} çağırır ve son boyutun beklenenden küçük
 * olduğunu gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * {@code static} paylaşılan state'i thread-safe bir yapıya taşı (bkz.
 * {@link com.interviewlab.javacore.staticdemo.good.SynchronizedStaticListService}), ya da -
 * çoğunlukla daha iyisi - hiç {@code static} mutable state tutma, instance state kullan.
 */
public final class SharedMutableStaticListService {

    private static final List<String> users = new ArrayList<>();

    public void addUser(String user) {
        users.add(user); // senkronizasyon yok - tüm instance'lar arasında paylaşılan liste
    }

    public static int userCount() {
        return users.size();
    }

    public static void clear() {
        users.clear();
    }
}
