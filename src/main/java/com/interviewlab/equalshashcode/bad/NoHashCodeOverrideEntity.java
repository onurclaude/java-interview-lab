package com.interviewlab.equalshashcode.bad;

import java.util.Objects;

/**
 * NE YANLIŞ?
 * {@link #equals(Object)}, {@code id} üzerinden karşılaştırma yapacak şekilde override
 * edilmiş, ama {@link #hashCode()} override EDİLMEMİŞ - {@link Object}'in varsayılan,
 * identity tabanlı hash kodunu koruyor.
 *
 * <p>NEDEN YANLIŞ?
 * Bu, equals/hashCode SÖZLEŞMESİNİ bozar: "eğer {@code a.equals(b)} ise
 * {@code a.hashCode() == b.hashCode()} olmalıdır" kuralı her hash tabanlı koleksiyon
 * ({@link java.util.HashSet}, {@link java.util.HashMap}) tarafından gereklidir. İki nesne
 * birbirine {@code .equals()} olabilir ama tamamen farklı hash kodları hesaplayabilir, çünkü
 * varsayılan hash kodu {@code id}'den değil, identity/bellek adresinden türetilir.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Bir {@link java.util.HashSet}, birbirine {@code .equals()} olan iki "duplicate" eleman
 * içerebilir, çünkü farklı bucket'lara hashlenmişlerdir ve set onları hiçbir zaman
 * {@code equals()} ile karşılaştırmamıştır. {@code set.contains(x)}, set'te zaten bulunan
 * bir elemana {@code .equals()} OLAN bir {@code x} için {@code false} döndürebilir, çünkü
 * arama doğrudan yanlış bucket'a gider ve asla {@code equals()} kontrolüne ulaşmaz.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code EqualsHashCodeTest.shouldAllowDuplicatesInHashSetWithoutHashCodeOverride()}, id
 * bakımından eşit iki instance'ı bir {@link java.util.HashSet}'e ekler ve set'in boyutunun
 * 1 değil 2 olduğunu gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * {@code hashCode()}'u {@code equals()} ile tutarlı şekilde override edin - bkz.
 * {@link com.interviewlab.equalshashcode.good.ProperEqualsHashCodeEntity}. Çoğu IDE ve
 * {@code record}, bu uyumsuzluğu önlemek için özellikle ikisini birlikte üretir.
 */
public class NoHashCodeOverrideEntity {

    private final Long id;

    public NoHashCodeOverrideEntity(Long id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NoHashCodeOverrideEntity other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    // hashCode() bilerek override EDİLMEDİ - hata bu.
}
