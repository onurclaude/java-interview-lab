package com.interviewlab.streamtopic.good;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link com.interviewlab.streamtopic.bad.ParallelStreamSharedMutableStateService}'in doğru
 * karşılığı: {@code collect(Collectors.toList())}, Stream implementasyonunun her worker'dan
 * gelen kısmi sonuçları doğru şekilde birleştirmesine izin verir (her thread kendi
 * container'ında biriktirir, bunlar daha sonra birleştirilir) - paylaşılan mutable duruma
 * asla aynı anda birden fazla thread tarafından dokunulmaz.
 *
 * <p><b>NEDEN her zaman doğrudan {@code parallelStream()}'e yönelmemeli?</b> Burada olduğu
 * gibi doğru kullanılsa bile, paralel stream'ler varsayılan olarak paylaşılan ortak
 * {@code ForkJoinPool}'u kullanır (bkz.
 * {@code async.completablefuture.bad.DefaultCommonPoolService}), küçük girdiler veya
 * eleman başına ucuz işler için gerçek bir ek yük getirir (bölme/birleştirme, kazanılan
 * işten daha maliyetli olabilir) ve I/O-yoğun veya bloklayan eleman operasyonları için
 * faydalı bir paralellik sağlamaz. Ona sadece gerçekten büyük koleksiyonlar üzerindeki
 * CPU-yoğun işler için başvurun.
 */
public class ParallelStreamCollectService {

    public List<Integer> squareAll(List<Integer> input) {
        return input.parallelStream()
                .map(x -> x * x)
                .collect(Collectors.toList());
    }
}
