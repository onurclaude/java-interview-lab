package com.interviewlab.scopes.singleton.bad;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * NE YANLIŞ?
 * {@code currentPrice}, bir {@code @Service} üzerinde bir instance alanıdır ve
 * {@link #calculateDiscountedPrice} içinde iki ayrı ifade boyunca mutate edilir (değeri
 * değiştirilir).
 *
 * <p>NEDEN YANLIŞ?
 * Bir Spring {@code @Service}, <b>varsayılan olarak bir singleton bean'dir</b> - her
 * {@code ApplicationContext} başına tam olarak BİR instance vardır ve her HTTP request
 * thread'i o aynı instance üzerinde metot çağırır. Bu, GoF Singleton pattern'i değildir
 * (private bir constructor ile kendi tek örneklenmesini zorlayan bir sınıf) - thread
 * safety hakkında hiçbir şey söylemeyen bir container-scoping kararıdır. Her eşzamanlı
 * istek tarafından paylaşılan bir bean üzerindeki bir instance alanında çağrı başına,
 * istek başına veri saklamak, sıradan istek işlemeyi bir data race'e dönüştürür: aynı metodu
 * eşzamanlı çağıran iki thread AYNI alanı okur ve yazar.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Eşzamanlı istekler altında, bir müşterinin hesaplanan fiyatı sessizce başka bir müşterinin
 * taban fiyatı tarafından üzerine yazılabilir veya ondan hesaplanabilir - bu, yalnızca gerçek
 * eşzamanlı yük altında ortaya çıkan ve tarayıcıda tek bir manuel testle reprodüksiyonu çok
 * zor olan bir doğruluk hatasıdır (yanlış fiyat gösterilmesi veya tahsil edilmesi).
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code BeanScopesTest.shouldCorruptResultWithMutableSingletonUnderConcurrency()}, farklı
 * taban fiyatlarıyla birçok eşzamanlı çağrı yapar ve bazı çağrıların, o çağrının kendisinin
 * sağladığı taban fiyata karşılık gelmeyen bir indirimli fiyat döndürdüğünü gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * Çağrı başına tüm state'i yerel değişkenlerde tut (metot parametreleri ve yerel değişkenler
 * thread'in kendi stack'inde bulunur, asla paylaşılmaz) - bkz.
 * {@link com.interviewlab.scopes.singleton.good.StatelessPriceService}. Bir singleton bean
 * yalnızca değişmez (immutable) konfigürasyon/collaborator tutmalıdır, asla mutable istek
 * başına veri değil.
 */
@Service
public class MutableSingletonPriceService {

    private BigDecimal currentPrice;

    public BigDecimal calculateDiscountedPrice(BigDecimal basePrice, BigDecimal discountPercentage) {
        this.currentPrice = basePrice;
        simulateSlowStep(); // race penceresini genişletir, böylece bozulma güvenilir şekilde reprodüksiyona uğrar
        BigDecimal discountAmount = this.currentPrice.multiply(discountPercentage);
        return this.currentPrice.subtract(discountAmount);
    }

    private static void simulateSlowStep() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
