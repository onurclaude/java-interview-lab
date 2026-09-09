package com.interviewlab.patterns.adapter;

import com.interviewlab.patterns.strategy.good.PaymentStrategy;
import org.springframework.stereotype.Component;

/**
 * Problem: {@link ExternalPaymentProviderSdk}'ın arayüzü (cent cinsinden {@code long}, bir
 * merchant id parametresi, tamsayı bir status kodu) bu projenin kendi {@link PaymentStrategy}
 * kontratıyla uyuşmuyor ve üçüncü taraf SDK'yı değiştiremiyoruz.
 *
 * <p>Kötü alternatif: {@code externalSdk.submitPayment(...)} çağrılarını ve onun
 * cent-dönüşümü/status-kodu-kontrolü mantığını, bu sağlayıcı üzerinden ödeme alması gereken
 * kod tabanındaki her yere dağıtmak - her çağrı noktası aynı dönüşümü yeniden uygular ve
 * arayüzünü değiştiren bir SDK güncellemesi, tüm çağrı noktalarının tek tek bulunmasını
 * gerektirir.
 *
 * <p>PATTERN: Adapter - tek bir sınıf, iki arayüz arasındaki çeviriyi tek bir yerde yapar.
 * Uygulamanın geri kalanı yalnızca {@link PaymentStrategy}'ye bağımlıdır; harici bir SDK'nın
 * işin içinde olduğundan bile haberdar değildir.
 *
 * <p>NE ZAMAN KULLANILMAMALI: her iki arayüzü de kontrol ediyorsanız (örn. bu üçüncü taraf
 * bir SDK değil, kendi iç sınıfınızsa), sebepsiz yere bir adapter katmanı eklemek yerine
 * doğrudan arayüzü değiştirin.
 */
@Component
public class ExternalPaymentProviderAdapter implements PaymentStrategy {

    private static final String MERCHANT_ID = "lab-merchant-1";

    private final ExternalPaymentProviderSdk externalSdk;

    public ExternalPaymentProviderAdapter(ExternalPaymentProviderSdk externalSdk) {
        this.externalSdk = externalSdk;
    }

    @Override
    public String paymentType() {
        return "EXTERNAL_PROVIDER";
    }

    @Override
    public String pay(double amount) {
        long amountInCents = Math.round(amount * 100);
        int statusCode = externalSdk.submitPayment(MERCHANT_ID, amountInCents);
        if (statusCode != 0) {
            throw new IllegalStateException("external provider rejected payment, status=" + statusCode);
        }
        return "Charged via external provider: " + amount;
    }
}
