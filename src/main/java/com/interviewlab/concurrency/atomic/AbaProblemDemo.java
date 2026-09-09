package com.interviewlab.concurrency.atomic;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * ABA problemini ve buna karşı çözümü gösterir.
 *
 * <p><b>Problem:</b> {@link AtomicReference} üzerindeki sıradan bir CAS yalnızca nesne
 * kimliğini (identity) karşılaştırır. Bir thread okuma-sonra-CAS dizisinin ortasındayken
 * değer A -&gt; B -&gt; A şeklinde değişirse, o thread'in CAS'i "hâlâ A" görür ve başarılı olur -
 * oysa değer aslında değişmiş ve arada tekrar eski haline dönmüştür. Basit bir sayaç için bu
 * zararsızdır (A yine A'dır), ama "değer gitti ve geri geldi" durumunun anlam taşıdığı
 * yapılarda - klasik örnek, lock-free stack/queue düğümleri; burada pop edilen bir düğüm
 * serbest bırakılıp yeniden kullanılabilir ve aynı bellek adresiyle geri push edilebilir -
 * bayat (stale) bir CAS, saf bir kimlik kontrolünün tespit edemeyeceği şekilde yapıyı
 * sessizce bozabilir.
 *
 * <p><b>Çözüm:</b> {@link AtomicStampedReference}, her değeri, CAS'in başarılı olması için
 * AYRICA eşleşmesi gereken bir tamsayı damga (stamp) ile eşleştirir. Referans, orijinal
 * değerle {@code equals()} veya {@code ==} açısından eşleşen bir değere geri dönse bile, her
 * değişimde artırılan damga aradaki mutasyonu görünür kılar ve compare-and-set doğru şekilde
 * başarısız olur.
 */
public final class AbaProblemDemo {

    private AbaProblemDemo() {
    }

    /** Değer arada değişip eski haline dönmüş olsa bile true döner (yanlış pozitif). */
    public static boolean casSucceedsDespiteIntermediateChange(AtomicReference<String> ref, String original, String intermediate) {
        ref.set(original);
        String observed = ref.get(); // thread "A" değerini okur

        // ...bu sırada, başka bir thread A -> B -> A şeklinde değiştiriyor...
        ref.compareAndSet(original, intermediate);
        ref.compareAndSet(intermediate, original);

        // thread'in CAS'i yine de başarılı olur: değerin hiçbir zaman "A" dışında bir şey olduğunu anlayamaz
        return ref.compareAndSet(observed, "final-value-after-first-thread");
    }

    /** Bir damga (stamp) ile aynı iç içe geçme (interleaving) doğru şekilde tespit edilir ve CAS başarısız olur. */
    public static boolean stampedCasDetectsIntermediateChange(AtomicStampedReference<String> ref, String original, String intermediate) {
        int[] stampHolder = new int[1];
        ref.set(original, 0);
        String observedValue = ref.get(stampHolder); // thread value="A", stamp=0 okur
        int observedStamp = stampHolder[0];

        // ...bu sırada, başka bir thread A -> B -> A şeklinde değiştiriyor ve her seferinde damgayı artırıyor...
        ref.compareAndSet(original, intermediate, 0, 1);
        ref.compareAndSet(intermediate, original, 1, 2);

        // thread'in CAS'i artık başarısız olur: değer aynı görünse de damga değerin hareket ettiğini kanıtlar
        return ref.compareAndSet(observedValue, "final-value-after-first-thread", observedStamp, observedStamp + 1);
    }
}
