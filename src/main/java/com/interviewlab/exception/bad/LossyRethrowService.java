package com.interviewlab.exception.bad;

import com.interviewlab.exception.PaymentGatewayCheckedException;
import com.interviewlab.exception.PaymentGatewayClient;
import org.springframework.stereotype.Service;

/**
 * NE YANLIŞ?
 * {@link #chargeCardLosingCause}, yakaladıktan sonra gerçekten yeniden fırlatır - ama
 * özgün {@link PaymentGatewayCheckedException}'ı cause olarak geçirmeden,
 * {@code new RuntimeException("payment failed")} olarak.
 *
 * <p>NEDEN YANLIŞ?
 * Yeni exception'ın stack trace'i bu satırda sıfırdan başlar; gerçek decline kodunu ve
 * gateway tarafı detayı taşıyan özgün exception atılır. Bu, swallowing'in daha ince bir
 * versiyonudur: en azından bir şey fırlatılır, ama NEDEN başarısız olduğunu açıklayacak tek
 * bilgi parçası kaybolmuştur.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Nöbetçi bir mühendis loglarda başka ayrıntı olmadan "payment failed" görür ve ya sorunu
 * yeniden üretmek ya da gateway tarafı logları ayrıca eşelemek zorunda kalır, çünkü
 * {@code catch} bloğunda tam orada duran özgün exception, cause olarak eklenmek yerine
 * atılmıştır.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code ExceptionHierarchyTest.shouldLoseOriginalCauseWithLossyRethrow()}, ortaya çıkan
 * exception'ı yakalar ve {@code getCause()}'un {@code null} olduğunu gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * Özgün exception'ı her zaman cause olarak geçir - bkz.
 * {@link com.interviewlab.exception.good.WrappingExceptionService}.
 */
@Service
public class LossyRethrowService {

    private final PaymentGatewayClient paymentGatewayClient;

    public LossyRethrowService(PaymentGatewayClient paymentGatewayClient) {
        this.paymentGatewayClient = paymentGatewayClient;
    }

    public void chargeCardLosingCause(String cardToken, double amount) {
        try {
            paymentGatewayClient.charge(cardToken, amount);
        } catch (PaymentGatewayCheckedException e) {
            throw new RuntimeException("payment failed"); // özgün exception 'e' atılıyor
        }
    }
}
