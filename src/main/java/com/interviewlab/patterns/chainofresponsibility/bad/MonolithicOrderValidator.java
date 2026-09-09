package com.interviewlab.patterns.chainofresponsibility.bad;

import org.springframework.stereotype.Component;

/**
 * NE YANLIŞ? Her doğrulama kuralı (stok, fraud, harcama limiti) tek bir büyük metot içinde
 * yaşıyor.
 *
 * <p>NEDEN YANLIŞ? Kurallar bu metodu düzenlemeden yeniden sıralanamaz, tek tek yeniden
 * kullanılamaz, izole olarak unit test edilemez ya da ortama göre etkinleştirilip
 * kapatılamaz. Dördüncü bir kural eklemek, zaten birbiriyle ilgisiz üç iş yapan bir metodu
 * düzenlemek anlamına gelir.
 *
 * <p>PRODUCTION'DA NE OLABİLİR? Fraud kuralındaki bir düzeltme, aynı metot gövdesinde
 * düzenleme yapılıyor olması nedeniyle stok veya limit kontrollerini bozma riski taşır;
 * kimse önce ayıklamadan "sadece fraud kontrolünü" başka bir yerde yeniden kullanamaz.
 *
 * <p>PATTERN: Chain of Responsibility - her kuralın kendi sınıfı olduğu ve zincirin bunların
 * sadece sıralı bir listesi olduğu
 * {@link com.interviewlab.patterns.chainofresponsibility.good.OrderValidationChain}'e bakın.
 */
@Component
public class MonolithicOrderValidator {

    public void validate(int stockAvailable, int quantity, boolean flaggedForFraud, double amount, double spendingLimit) {
        if (quantity > stockAvailable) {
            throw new IllegalStateException("insufficient stock");
        }
        if (flaggedForFraud) {
            throw new IllegalStateException("failed fraud check");
        }
        if (amount > spendingLimit) {
            throw new IllegalStateException("exceeds spending limit");
        }
    }
}
