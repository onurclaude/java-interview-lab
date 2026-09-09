package com.interviewlab.exception.good;

import com.interviewlab.exception.InsufficientBalanceException;
import com.interviewlab.exception.PaymentGatewayCheckedException;
import com.interviewlab.exception.PaymentGatewayClient;
import org.springframework.stereotype.Service;

/**
 * {@link com.interviewlab.exception.bad.SwallowingExceptionService} ve
 * {@link com.interviewlab.exception.bad.LossyRethrowService}'in doğru karşılığı: checked
 * exception, özgünü cause olarak eklenmiş HALDE anlamlı, unchecked bir business exception'a
 * çevrilir - {@code getCause()} hâlâ özgün gateway detayına erişim sağlar ve
 * {@code printStackTrace()}/log çıktısı tam "Caused by:" zincirini gösterir.
 */
@Service
public class WrappingExceptionService {

    private final PaymentGatewayClient paymentGatewayClient;

    public WrappingExceptionService(PaymentGatewayClient paymentGatewayClient) {
        this.paymentGatewayClient = paymentGatewayClient;
    }

    public void chargeCard(String cardToken, double amount) {
        try {
            paymentGatewayClient.charge(cardToken, amount);
        } catch (PaymentGatewayCheckedException e) {
            throw new InsufficientBalanceException("payment failed for card token=" + cardToken, e);
        }
    }
}
