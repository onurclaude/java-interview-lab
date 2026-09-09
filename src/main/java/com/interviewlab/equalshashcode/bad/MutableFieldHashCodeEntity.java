package com.interviewlab.equalshashcode.bad;

import java.util.Objects;

/**
 * NE YANLIŞ?
 * {@code hashCode()}, {@code id}'den türetiliyor ve {@code id} MUTABLE (değişebilir) - bu
 * tam olarak, {@code @GeneratedValue} id'si entity kalıcı hale getirilene kadar {@code null}
 * olan ve ancak o zaman Hibernate tarafından atanan gerçek bir JPA entity'sinin şeklidir.
 *
 * <p>NEDEN YANLIŞ?
 * Hash tabanlı bir koleksiyon ({@link java.util.HashSet}/{@link java.util.HashMap}), bir
 * elemanı EKLEME ANINDAKİ hash kodu tarafından seçilen bir bucket'a yerleştirir. Eğer o
 * nesnenin hash kodu daha sonra değişirse (bağlı olduğu bir alan değiştiği için), nesne
 * artık yeni hash kodu için "yanlış bucket'ta" demektir - CURRENT (mevcut) hash kodunu
 * yeniden hesaplayarak bir bucket seçen sonraki bir {@code contains()}/{@code remove()}
 * çağrısı, yanlış yerde arama yapacak ve referans olarak set'in içinde tam orada duran bir
 * nesneyi bulamayacaktır.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Yepyeni (transient/geçici) bir JPA entity'si, kaydedilmeden önce bir
 * {@code Set&lt;Entity&gt;}'e eklenir, sonra kaydedilir (id alanı Hibernate tarafından
 * doldurulur) - ve set artık bu entity'yi sessizce "kaybeder": hiçbir şey onu kaldırmamış
 * olsa bile {@code set.contains(theSameEntity)} {@code false} döndürür. Bu, iyi bilinen,
 * özellikle JPA'ya özgü bir tuzaktır.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code EqualsHashCodeTest.shouldLoseElementInHashSetWhenHashCodeFieldMutatesAfterInsertion()},
 * null id'li bir entity'yi bir set'e ekler, id'sini "atar" (bir kaydetmeyi simüle eder) ve
 * tam olarak aynı instance üzerinde {@code contains()}'in artık false döndürdüğünü gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * Özellikle JPA entity'leri için: eğer varsa {@code equals()}/{@code hashCode()}'u kararlı
 * bir iş anahtarına dayandırın, ya da - pragmatik, yaygın kullanılan uzlaşma - SABİT bir
 * {@code hashCode()} (örn. entity sınıfının hash kodunu döndürmek) ile mevcut olduğunda
 * id-tabanlı bir {@code equals()}'u birleştirerek kullanın; bu, geçici-den-kalıcıya geçiş
 * boyunca doğruluk karşılığında biraz daha kötü hash dağılımını kabul etmek demektir.
 * Genel olarak (sadece JPA değil): {@code hashCode()}'u, nesne hash tabanlı bir koleksiyona
 * yerleştirildikten sonra değişebilecek bir alandan asla türetmeyin.
 */
public class MutableFieldHashCodeEntity {

    private Long id; // bilerek mutable - bir JPA @GeneratedValue id'sini yansıtıyor

    public void assignId(Long id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MutableFieldHashCodeEntity other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id); // eklemeden sonra değişen bir alandan türetiliyor - hata bu
    }
}
