package com.interviewlab.exception;

/** Düşük seviyeli bir bağımlılığın (örn. bir HTTP client SDK'sı) fırlattığı checked exception'ın yerini tutar. */
public class PaymentGatewayCheckedException extends Exception {
    public PaymentGatewayCheckedException(String message) {
        super(message);
    }
}
