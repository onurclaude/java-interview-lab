package com.interviewlab.exception.web;

import com.interviewlab.exception.BusinessException;
import com.interviewlab.exception.InsufficientBalanceException;
import com.interviewlab.exception.PaymentException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Her {@code @RestController} metodunun kendi {@code try/catch}'ine ihtiyaç duyması yerine,
 * tüm exception hiyerarşisini HTTP yanıtlarına eşleyen tek bir yer. Handler'lar en-özelden
 * başlayarak eşleştirilir: {@link InsufficientBalanceException}, {@link PaymentException}
 * IS-A {@link BusinessException} olmasına rağmen kendi status/kodunu alır; özel işlem
 * gerektirmeyen her şey için bu ikisi de burada ele alınır.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(InsufficientBalanceException ex) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(new ErrorResponse("INSUFFICIENT_BALANCE", ex.getMessage()));
    }

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ErrorResponse> handlePayment(PaymentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("PAYMENT_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("BUSINESS_ERROR", ex.getMessage()));
    }
}
