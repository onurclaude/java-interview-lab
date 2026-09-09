package com.interviewlab.jpa.bad;

import com.interviewlab.jpa.entity.Order;
import com.interviewlab.jpa.repository.OrderRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NE YANLIŞ?
 * {@link #countAllItemsAcrossOrders}, tüm siparişleri tek bir sorguyla yükler, ardından bir
 * döngü içinde {@code order.getOrderItems()} çağırır.
 *
 * <p>NEDEN YANLIŞ?
 * {@code orderItems}, varsayılan olarak LAZY olan bir {@code @OneToMany}'dir (bkz. {@code Order}
 * entity'sinin javadoc'u). {@code findAll()} onu getirmez - HER siparişte
 * {@code getOrderItems()}'a ilk erişim kendi {@code SELECT ... FROM lab_jpa_order_item
 * WHERE order_id = ?} sorgusunu tetikler. N sipariş için bu, 1 (siparişler) + N (her
 * siparişin item'ları için birer tane) sorgu demektir - yani "N+1" problemi.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * 20 siparişi listeleyen bir ekran, dev ortamında birkaç test satırıyla gayet iyi görünür,
 * ama 200 gerçek sipariş olduğunda veritabanına 201 gidiş-dönüş yapar - veri hacmiyle
 * ölçeklenen ve veri büyüyene kadar görünmeyen bir gecikme problemi.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code NPlusOneTest.shouldIssueOneQueryPerOrderWhenTouchingLazyCollectionInALoop()}
 * yakalanan SQL ifadelerini sayar ve tam olarak {@code orders.size() + 1} SELECT olduğunu
 * gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * Bir fetch join, bir {@code @EntityGraph}, ya da (tam bir entity'ye bile ihtiyaç yoksa) bir
 * DTO projection - bkz. {@link com.interviewlab.jpa.good.FetchJoinOrderService},
 * {@link com.interviewlab.jpa.good.EntityGraphOrderService} ve
 * {@link com.interviewlab.jpa.good.DtoProjectionOrderService}.
 */
@Service
public class NPlusOneOrderService {

    private final OrderRepository orderRepository;

    public NPlusOneOrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public int countAllItemsAcrossOrders() {
        List<Order> orders = orderRepository.findAll(); // sorgu #1
        int total = 0;
        for (Order order : orders) {
            total += order.getOrderItems().size(); // HER sipariş için bir ek sorgu
        }
        return total;
    }
}
