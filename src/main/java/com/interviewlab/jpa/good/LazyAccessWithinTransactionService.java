package com.interviewlab.jpa.good;

import com.interviewlab.jpa.entity.Order;
import com.interviewlab.jpa.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link com.interviewlab.jpa.bad.LazyInitializationDemoService}'in tersi: aynı lazy
 * {@code orderItems} koleksiyonuna, entity'yi yükleyen transaction HÂLÂ AÇIKKEN erişir - bu
 * yüzden Hibernate session hâlâ mevcuttur ve lazy proxy başarıyla başlatılabilir.
 */
@Service
public class LazyAccessWithinTransactionService {

    private final OrderRepository orderRepository;

    public LazyAccessWithinTransactionService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public int loadOrderAndCountItemsWithinTransaction(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        return order.getOrderItems().size(); // <- transaction/session HÂLÂ açık - lazy proxy başarıyla başlatılır
    }
}
