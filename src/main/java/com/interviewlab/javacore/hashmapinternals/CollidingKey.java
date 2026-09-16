package com.interviewlab.javacore.hashmapinternals;

import java.util.Objects;

/**
 * {@code hashCode()}'u KASITLI olarak sabit bir değer döndürecek şekilde yazılmış bir key -
 * bu, farklı {@code CollidingKey} instance'larının (farklı {@code value}'larına rağmen) HER
 * ZAMAN aynı bucket'a düşmesini garanti eder, bu yüzden {@link java.util.HashMap}'in collision
 * handling'ini (bucket içinde {@code equals()} ile doğru elemanı ayırt etme) deterministik
 * olarak gözlemleyebiliriz - gerçek hash dağılımına ve şansa güvenmek yerine.
 *
 * <p>Bu, KÖTÜ bir {@code hashCode()} implementasyonunun canlı bir örneğidir: teknik olarak
 * contract'ı ihlal etmez (eşit nesneler eşit hash döndürür - burada HERKES eşit hash
 * döndürüyor), ama {@link java.util.HashMap}'in O(1) ortalama karmaşıklığını tamamen
 * boşa çıkarır - her arama, tüm elemanların aynı bucket'ta olduğu bir linked list'te (ya da
 * treeification eşiğini aşarsa bir ağaçta) doğrusal/logaritmik arama yapar.
 */
public final class CollidingKey {

    private final String value;

    public CollidingKey(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public int hashCode() {
        return 42; // kasıtlı olarak sabit - her instance aynı bucket'a düşer
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CollidingKey other)) {
            return false;
        }
        return Objects.equals(value, other.value);
    }
}
