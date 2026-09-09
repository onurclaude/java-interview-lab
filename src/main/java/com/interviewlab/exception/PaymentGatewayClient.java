package com.interviewlab.exception;

import org.springframework.stereotype.Component;

/** Checked exception'ı faydalı tanı detayı taşıyan, simüle edilmiş düşük seviyeli bir bağımlılık. */
@Component
public class PaymentGatewayClient {

    public void charge(String cardToken, double amount) throws PaymentGatewayCheckedException {
        throw new PaymentGatewayCheckedException("gateway declined card token=" + cardToken + " (code=DECLINED_051)");
    }
}
