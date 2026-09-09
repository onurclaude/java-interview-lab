package com.interviewlab.jpa.bad;

import com.interviewlab.jpa.entity.Order;
import com.interviewlab.jpa.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NE YANLIŞ?
 * {@link #loadOrderWithoutTouchingItems}, bir {@code @Transactional} metot içinden bir
 * {@code Order} yükler ve {@code orderItems}'a hiç dokunmadan onu döndürür. Transaction (ve
 * onunla birlikte Hibernate session/persistence context'i), bu metot döner dönmez sona erer.
 *
 * <p>NEDEN YANLIŞ?
 * Lazy bir ilişki, gerçek verisini yalnızca açık bir Hibernate session'ı ÜZERİNDEN
 * getirebilen bir proxy/başlatılmamış koleksiyondur. Entity'yi yükleyen transaction commit
 * edildiğinde, o session kapanır. Çağıran kod - bu metodun dışında, herhangi bir
 * transaction'ın dışında - DAHA SONRA {@code order.getOrderItems().size()} çağırırsa, fetch
 * sorgusunu çalıştıracak bir session kalmamıştır ve Hibernate
 * {@link org.hibernate.LazyInitializationException} fırlatır.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Bir servis metodu bir entity'yi bir controller'a döndürür, controller (ya da bir JSON
 * serializer) response body'si için lazy bir alanı okumaya çalışır ve istek bir
 * {@code LazyInitializationException} ile başarısız olur - lazy/session yaşam döngüsü
 * ilişkisine yeni olan geliştiriciler için JPA ile çok yaygın bir ilk karşılaşmadır bu.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code NPlusOneTest.shouldThrowLazyInitializationExceptionWhenAccessingLazyCollectionOutsideTransaction()}
 * bu metodu çağırır, ardından {@code getOrderItems()}'a erişir ve istisnayı gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * Ya ihtiyaç duyduğun şeyi transaction/session HÂLÂ açıkken getir (fetch join / entity graph -
 * bkz. {@code jpa.good}), ya da - bazı Spring MVC uygulamalarının etkinleştirdiği "Open
 * Session in View" deseninde olduğu gibi - session'ı tüm HTTP isteği boyunca açık tut, böylece
 * view katmanından lazy erişim yine çalışır. OSIV kullanışlıdır ama bu kolaylığı gerçek bir
 * bedelle takas eder: veritabanı bağlantıları isteğin tüm süresi boyunca (template render
 * etme dahil) elde tutulur ve lazy-loading sorguları view kodundan, nereden geldikleri
 * hiç görünür olmadan tetiklenebilir. Bu proje, lazy-access hatalarının sessizce
 * örtbas edilmek yerine burada, açıkça ortaya çıkması için bilinçli olarak
 * {@code spring.jpa.open-in-view=false} ayarını kullanır (bkz. application.yml).
 */
@Service
public class LazyInitializationDemoService {

    private final OrderRepository orderRepository;

    public LazyInitializationDemoService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public Order loadOrderWithoutTouchingItems(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow();
        // Transaction/session burada sona erer - orderItems hâlâ başlatılmamış bir lazy proxy'dir.
    }
}
