package com.interviewlab.immutability.good;

import java.math.BigDecimal;

/**
 * Elle yazılmış immutable bir sınıf: her alan {@code final}, setter yok ve - unutulması
 * kolay olan kısım - bir alanı yerinde değiştirip {@code this} döndüren hiçbir metot yok.
 * {@link #add(BigDecimal)}, bu instance'ı değiştirmek yerine YENİ bir instance döndürür;
 * bu sınıfı sıfır senkronizasyonla thread'ler arasında paylaşmayı güvenli hale getiren de
 * tam olarak budur: construction sonrası asla değişemeyen bir nesne, kaç thread ona referans
 * tutarsa tutsun bir data race'e karışamaz. Sadece düz bir veri taşıyıcısına ihtiyacınız
 * olduğunda, bir {@code record} (bkz. {@code docs/immutability.md}) size daha az boilerplate
 * ile aynı şekli sağlar; constructor'da validasyon veya {@link #add} gibi ekstra davranış
 * istediğinizde elle yazılmış bir sınıf hâlâ faydalı olmaya devam eder.
 */
public final class ImmutableMoney {

    private final BigDecimal amount;
    private final String currency;

    public ImmutableMoney(BigDecimal amount, String currency) {
        if (amount == null || currency == null) {
            throw new IllegalArgumentException("amount and currency are required");
        }
        this.amount = amount;
        this.currency = currency;
    }

    public ImmutableMoney add(BigDecimal delta) {
        return new ImmutableMoney(amount.add(delta), currency); // YENİ bir instance döndürür
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }
}
