package com.interviewlab.collectionstopic.good;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link CopyOnWriteArrayList}, HER değişiklikte (add/remove/set) tüm alttaki diziyi
 * kopyalar. Iterator'lar, oluşturuldukları andaki dizinin sabit bir anlık görüntüsü
 * üzerinden alınır, bu yüzden iterasyon sırasındaki eşzamanlı bir değişiklik asla
 * {@link java.util.ConcurrentModificationException} fırlatamaz - iterator sadece
 * başladıktan sonra yapılan değişiklikleri görmez. Bu takas (pahalı yazmalar, anlık
 * görüntü-tutarlı ucuz okumalar, CME yok), onu event listener listesi gibi okuma-ağırlıklı,
 * yazma-nadir koleksiyonlar için iyi bir seçim yapar - her yazmada kopyalama maliyetinin
 * baskın olduğu yazma-ağırlıklı koleksiyonlar için değil.
 */
public class SafeIterationListHolder {

    private final List<String> listeners = new CopyOnWriteArrayList<>();

    public void add(String listener) {
        listeners.add(listener);
    }

    public List<String> listeners() {
        return listeners;
    }
}
