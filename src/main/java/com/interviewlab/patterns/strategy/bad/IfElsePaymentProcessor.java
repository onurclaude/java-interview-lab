package com.interviewlab.patterns.strategy.bad;

import org.springframework.stereotype.Service;

/**
 * Problem: bir ödemeyi türüne göre farklı şekilde işlemek.
 *
 * <p>NE YANLIŞ? Bir string/enum üzerinden dallanan, giderek büyüyen bir {@code if/else if}
 * zinciri.
 *
 * <p>NEDEN YANLIŞ? Her yeni ödeme yöntemi bu tek metodun düzenlenmesini gerektirir
 * (open/closed prensibini ihlal eder), dallar ortak bir kontratı paylaşmaz, dolayısıyla
 * şekillerinin birbirinden sapmasını engelleyen hiçbir şey yoktur, ve daha fazla tür
 * eklendikçe metodun cyclomatic complexity'si sonsuza kadar artar - sonunda kapsamlı bir
 * şekilde unit test etmek ya da akıl yürütmek imkansız hale gelir.
 *
 * <p>PRODUCTION'DA NE OLABİLİR? "Kripto" ödemeleri eklemek bu metodun (ve kod tabanına
 * dağılmış aynı tür kontrolü için benzer her if/else zincirinin) değiştirilmesi anlamına
 * gelir; bu da sadece aynı metotta bulunmaları nedeniyle ilgisiz bir dalda regresyon riski
 * yaratır.
 *
 * <p>PATTERN: Strategy - dallanmayı polimorfizmle değiştiren
 * {@link com.interviewlab.patterns.strategy.good.PaymentProcessor}'a bakın: her ödeme türü
 * ortak bir arayüzü implemente eden kendi sınıfıdır ve çağıran tarafından (dallanma yerine)
 * seçilir.
 */
@Service
public class IfElsePaymentProcessor {

    public String process(String paymentType, double amount) {
        if ("CREDIT_CARD".equals(paymentType)) {
            return "Charged credit card: " + amount;
        } else if ("WALLET".equals(paymentType)) {
            return "Debited wallet: " + amount;
        } else if ("BANK_TRANSFER".equals(paymentType)) {
            return "Initiated bank transfer: " + amount;
        } else {
            throw new IllegalArgumentException("Unknown payment type: " + paymentType);
        }
    }
}
