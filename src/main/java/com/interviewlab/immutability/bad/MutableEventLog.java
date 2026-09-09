package com.interviewlab.immutability.bad;

import java.util.ArrayList;
import java.util.List;

/**
 * NE YANLIŞ?
 * {@link #getEvents()}, dahili {@link List} alanını doğrudan döndürüyor.
 *
 * <p>NEDEN YANLIŞ?
 * {@code getEvents()}'i çağıran her kim ise artık BU nesnenin kendi dahili durumuna bir
 * referans tutuyor, onun bir kopyasına değil. Çağıranın döndürülen liste üzerinde yaptığı
 * herhangi bir değişiklik (add, remove, clear) bu nesnenin dahili durumunu da değiştirir -
 * alan {@code private} olsa bile geriye hiçbir encapsulation kalmaz.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Alakasız bir kod, kendi kopyasını temizlediğini düşünerek {@code getEvents().clear()}'ı
 * çağırır ve bu nesnenin gerçek olay geçmişini sessizce siler. Eşzamanlı erişim altında bu
 * aynı zamanda bir data race'dir: aynı döndürülen listeyi tutan iki thread, thread'ler
 * arasında paylaşılan senkronize edilmemiş herhangi bir {@code ArrayList} gibi onu
 * bozabilir.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code ImmutabilityTest.shouldExposeInternalStateWithoutDefensiveCopy()},
 * {@code getEvents()}'i çağırır, sonucu değiştirir, sonra değişikliğin log'a geri sızdığını
 * gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * Savunmacı bir kopya döndürün - bkz.
 * {@link com.interviewlab.immutability.good.DefensiveCopyEventLog}.
 * {@code Collections.unmodifiableList(internalList)}'in AYNI düzeltme OLMADIĞINI unutmayın:
 * bu, CALLER'ın (çağıranın) o belirli wrapper üzerinden değişiklik yapmasını engeller, ama
 * bu sınıf yine de aynı alttaki listeyi tutmaya (ve değiştirmeye) devam eder - savunmacı bir
 * KOPYANIN bağlantıyı gerçekten neden kestiğini görmek için bkz.
 * {@code ImmutabilityTest.shouldStillLeakChangesThroughAnUnmodifiableWrapperOfTheSameList()}.
 */
public class MutableEventLog {

    private final List<String> events = new ArrayList<>();

    public void record(String event) {
        events.add(event);
    }

    public List<String> getEvents() {
        return events; // hata bu: dahili durum, doğrudan dışarı veriliyor
    }
}
