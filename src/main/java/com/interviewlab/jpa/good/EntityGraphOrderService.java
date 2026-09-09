package com.interviewlab.jpa.good;

import com.interviewlab.jpa.entity.Order;
import com.interviewlab.jpa.repository.OrderRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@code jpa.good.FetchJoinOrderService} ile aynı çözüm, {@code @EntityGraph} aracılığıyla deklaratif olarak ifade edilmiştir. */
@Service
public class EntityGraphOrderService {

    private final OrderRepository orderRepository;

    public EntityGraphOrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public int countAllItemsAcrossOrders() {
        List<Order> orders = orderRepository.findAllWithEntityGraph();
        int total = 0;
        for (Order order : orders) {
            total += order.getOrderItems().size();
        }
        return total;
    }
}
