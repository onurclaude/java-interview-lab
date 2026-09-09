package com.interviewlab.patterns.adapter;

import org.springframework.stereotype.Component;

/**
 * Kontrol etmediğimiz üçüncü taraf bir SDK'nın yerini tutar: metot isimleri, parametre
 * tipleri (cent cinsinden, {@code long} olarak bir tutar) ve dönüş tipi (tamsayı bir status
 * kodu) bu projenin kendi {@code PaymentStrategy} kontratıyla hiç uyuşmaz. Sadece SDK'nın
 * client nesnesinin Spring context'inde bulunmasını simüle etmek için {@code @Component}
 * olarak kaydedilmiştir (gerçek bir üçüncü taraf client bean'inin genelde olduğu gibi) -
 * gerçekten harici bir bağımlılık genellikle bir kez oluşturulup kendi property'leri
 * üzerinden yapılandırılan düz bir nesne olurdu, projeye ait bir {@code @Component} değil.
 */
@Component
public class ExternalPaymentProviderSdk {

    public int submitPayment(String merchantId, long amountInCents) {
        return amountInCents > 0 ? 0 : 1; // 0 = başarılı, 1 = başarısız, bu SDK'nın kendi konvansiyonuna göre
    }
}
