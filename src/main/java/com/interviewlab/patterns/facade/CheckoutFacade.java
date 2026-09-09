package com.interviewlab.patterns.facade;

import com.interviewlab.patterns.factory.PaymentStrategyFactory;
import com.interviewlab.patterns.observer.OrderPlacementService;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Problem: gerçek bir checkout işlemi; stok doğrulama, bir ödeme yöntemi seçme/tahsil etme,
 * siparişi kaydetme ve diğer sistemleri bilgilendirme adımlarını içerir - her biri, bu
 * projenin başka yerlerde de gösterdiği farklı bir alt sistem tarafından ele alınır (ödeme
 * için Strategy/Factory, sipariş sonrası bildirimler için Observer, doğrulama için Chain of
 * Responsibility).
 *
 * <p>Kötü alternatif: ÇAĞIRANIN (örn. bir REST controller'ının) bu alt sistemlerin tamamını
 * doğrudan orkestre etmesini sağlamak - bu durumda ödeme stratejisi arama, sipariş event'i
 * yayınlama ve doğrulama zinciri bağlantısı hakkında bilgi sahibi olması gerekir, bu da bir
 * sunum katmanı sınıfını her alt sistemin iç detaylarına bağlar.
 *
 * <p>PATTERN: Facade - tek bir basit metot, çok adımlı orkestrasyonu tek bir çağrının
 * arkasına gizler. Koordine ettiği alt sistemler hâlâ var olmaya devam eder ve bağımsız
 * olarak kullanılabilir/test edilebilir durumdadır (bir Facade onları değiştirmez, birlikte
 * kullanımlarını basitleştirir).
 *
 * <p>NE ZAMAN KULLANILMAMALI: çağıranların tek tek adımlar üzerinde gerçekten ince ayarlı bir
 * kontrole ihtiyacı varsa (örn. "şimdi doğrula ama kullanıcı tahsilattan önce bir ödeme
 * yöntemi seçsin"), her şeyi her zaman tek bir çağrıda çalıştıran bir facade işin önüne
 * geçer - bu tür çağıranlara alt sistemleri doğrudan açın.
 */
@Service
public class CheckoutFacade {

    private final PaymentStrategyFactory paymentStrategyFactory;
    private final OrderPlacementService orderService;

    public CheckoutFacade(PaymentStrategyFactory paymentStrategyFactory, OrderPlacementService orderService) {
        this.paymentStrategyFactory = paymentStrategyFactory;
        this.orderService = orderService;
    }

    public String checkout(String paymentType, double amount) {
        String paymentResult = paymentStrategyFactory.getStrategy(paymentType).pay(amount);
        String orderId = UUID.randomUUID().toString();
        orderService.placeOrder(orderId, amount);
        return "Order " + orderId + " completed: " + paymentResult;
    }
}
