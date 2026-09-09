package com.interviewlab.equalshashcode.good;

import java.util.Objects;

/**
 * {@link com.interviewlab.equalshashcode.bad.MutableFieldHashCodeEntity}'nin doğru karşılığı:
 * {@code hashCode()} SABİTTİR (mutable {@code id}'ye hiç bağlı değildir), bu yüzden nesne,
 * {@code id} ne zaman atanırsa atansın her zaman aynı bucket'a düşer. {@code equals()} yine
 * de id üzerinden karşılaştırma yapar ({@code id} null iken referans eşitliğine geri döner,
 * böylece henüz kaydedilmemiş iki farklı entity birbirine yanlışlıkla eşit sayılmaz).
 */
public class StableHashCodeJpaStyleEntity {

    private Long id;

    public void assignId(Long id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StableHashCodeJpaStyleEntity other)) {
            return false;
        }
        if (id == null || other.id == null) {
            return false; // kaydedilmemiş entity'ler sadece kendilerine eşittir
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode(); // sabit: id daha sonra atansa bile asla değişmez
    }
}
