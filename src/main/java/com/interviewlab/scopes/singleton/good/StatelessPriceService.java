package com.interviewlab.scopes.singleton.good;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * {@link com.interviewlab.scopes.singleton.bad.MutableSingletonPriceService}'in doğru
 * karşılığı: hâlâ bir singleton bean'dir (bu kısım hiçbir zaman sorun değildi), ama
 * hesaplamaya dahil olan her değer bir yerel değişkende veya metot parametresinde yaşar -
 * her thread'in kendi stack'i vardır, bu yüzden bu aynı instance'ı ne kadar çok eşzamanlı
 * istek çağırırsa çağırsın, üzerinde yarışılacak paylaşılan hiçbir şey yoktur.
 */
@Service
public class StatelessPriceService {

    public BigDecimal calculateDiscountedPrice(BigDecimal basePrice, BigDecimal discountPercentage) {
        BigDecimal discountAmount = basePrice.multiply(discountPercentage);
        simulateSlowStep();
        return basePrice.subtract(discountAmount);
    }

    private static void simulateSlowStep() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
