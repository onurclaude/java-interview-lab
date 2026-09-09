package com.interviewlab.patterns.strategy.good;

/**
 * Strategy: içeride dallanmak yerine çağıran tarafından seçilen, "ödeme yöntemleri" için
 * ortak bir kontrat. Her implementasyon bağımsız olarak test edilebilir ve yeni bir ödeme
 * yöntemi eklemek mevcut olanlardan hiçbirine dokunmayı gerektirmez.
 */
public interface PaymentStrategy {

    String paymentType();

    String pay(double amount);
}
