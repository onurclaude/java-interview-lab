package com.interviewlab.exception.bad;

import com.interviewlab.exception.PaymentGatewayCheckedException;
import com.interviewlab.exception.PaymentGatewayClient;
import org.springframework.stereotype.Service;

/**
 * NE YANLIŞ?
 * {@link #chargeCardSilently}, gateway'in checked exception'ını yakalar ve onunla hiçbir şey
 * yapmaz - bir log satırı bile yok.
 *
 * <p>NEDEN YANLIŞ?
 * Exception, artık sonsuza dek kaybolmuş belirli ve faydalı bir tanı mesajı (bu
 * simülasyonda bir decline kodu) taşıyordu. Bu metodun çağıranı, ücretlendirmenin
 * başarısız olduğunu bilmenin hiçbir yoluna sahip değil - metot normal şekilde döner, sanki
 * ücretlendirme başarılı olmuş gibi.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Ödeme aslında başarısız olduğu halde bir sipariş "ödendi" olarak işaretlenir, çünkü hiçbir
 * şey başarısızlığı bu metodun ötesine yaymadı. Daha kötüsü, birisi sonunda "bu müşteriden
 * neden ücret alınmadı" sorununu debug etmesi gerektiğinde, ortada ne log, ne exception, ne
 * de başarısızlığın hiç yaşandığına dair bir iz vardır - vaka, mevcut verilerden pratikte
 * çözülemez haldedir.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code ExceptionHierarchyTest.shouldSilentlySwallowFailureAndReturnNormally()} bu metodu
 * çağırır ve gateway başarısız olmuş olmasına rağmen {@code true} (başarı) döndürdüğünü
 * gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * En azından logla; daha iyisi, özgün cause'u koruyan anlamlı bir unchecked business
 * exception olarak sarıp yeniden fırlat - bkz.
 * {@link com.interviewlab.exception.good.WrappingExceptionService}.
 */
@Service
public class SwallowingExceptionService {

    private final PaymentGatewayClient paymentGatewayClient;

    public SwallowingExceptionService(PaymentGatewayClient paymentGatewayClient) {
        this.paymentGatewayClient = paymentGatewayClient;
    }

    public boolean chargeCardSilently(String cardToken, double amount) {
        try {
            paymentGatewayClient.charge(cardToken, amount);
            return true;
        } catch (PaymentGatewayCheckedException e) {
            // Yutulmuş (swallowed): log yok, yeniden fırlatma yok, bunun yaşandığına dair iz yok.
        }
        return true; // çağıranın ücretlendirmenin aslında başarısız olduğunu bilmesinin hiçbir yolu yok
    }
}
