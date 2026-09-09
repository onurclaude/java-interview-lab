package com.interviewlab.streamtopic.bad;

import java.util.ArrayList;
import java.util.List;

/**
 * NE YANLIŞ?
 * {@code input.parallelStream().forEach(x -&gt; results.add(...))}, paralel stream'in o an
 * kullandığı worker thread'lerinden, paylaşılan tek bir düz {@link ArrayList}'i değiştiriyor.
 *
 * <p>NEDEN YANLIŞ?
 * {@code parallelStream()}, işi birden fazla thread'e böler (ortak
 * {@link java.util.concurrent.ForkJoinPool} tarafından desteklenir - başka yerde bunun
 * ima ettiği pool paylaşım riski için bkz.
 * {@code async.completablefuture.bad.DefaultCommonPoolService}). Harici, thread-safe
 * olmayan bir koleksiyona yazan, yan etkili bir {@code forEach}, {@code concurrency.race}'teki
 * senkronize edilmemiş paylaşılan-mutable-durum sorununun tam olarak aynısıdır, sadece
 * bariz bir şekilde thread'li bir kod parçası yerine bir stream operasyonu üzerinden
 * tanıtılmıştır - yapılması kolay bir hata olmasının tam nedeni de budur:
 * {@code .parallelStream().forEach(...)}'in hiçbir yanı manuel threading gibi GÖRÜNMEZ.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * {@link ArrayList#add} atomik değildir (bir boyut kontrolü, bir dizi yazma ve bir resize
 * içerebilir); eşzamanlı senkronize edilmemiş çağrılar sessizce eleman kaybedebilir,
 * {@code ArrayIndexOutOfBoundsException} fırlatabilir, ya da nadir durumlarda sonsuza kadar
 * döngüye bile girebilir - ve bu zamanlamaya bağlı olduğundan, genellikle dev ortamında/küçük
 * girdilerde "sorunsuz çalışır" ve yalnızca daha büyük production veri hacimleri altında
 * aralıklı olarak başarısız olur.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code StreamApiTest.shouldLoseElementsWithParallelStreamAndSharedMutableArrayList()}, bunu
 * büyük bir girdi üzerinde tekrar tekrar çalıştırır ve sonuç boyutunun her zaman girdi
 * boyutuna eşit olmadığını gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * Yan etkili bir {@code forEach} yerine {@code collect(Collectors.toList())} (ya da
 * {@code map().toList()}) kullanın - bkz.
 * {@link com.interviewlab.streamtopic.good.ParallelStreamCollectService}. Daha genel olarak:
 * paralel olsun ya da olmasın, bir stream pipeline'ın içinden paylaşılan, eşzamanlı olmayan
 * durumu asla değiştirmeyin.
 */
public class ParallelStreamSharedMutableStateService {

    public List<Integer> squareAll(List<Integer> input) {
        List<Integer> results = new ArrayList<>();
        input.parallelStream().forEach(x -> results.add(x * x)); // senkronize edilmemiş paylaşılan değişiklik
        return results;
    }
}
