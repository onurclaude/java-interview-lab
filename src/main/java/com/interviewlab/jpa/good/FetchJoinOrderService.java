package com.interviewlab.jpa.good;

import com.interviewlab.jpa.entity.Order;
import com.interviewlab.jpa.repository.OrderRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@code jpa.bad.NPlusOneOrderService}'i bir fetch join ile düzeltir: tek sorgu, toplam. */
@Service
public class FetchJoinOrderService {

    private final OrderRepository orderRepository;

    public FetchJoinOrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public int countAllItemsAcrossOrders() {
        List<Order> orders = orderRepository.findAllWithItemsFetchJoin(); // tek sorgu, item'lar zaten dolu
        int total = 0;
        for (Order order : orders) {
            total += order.getOrderItems().size(); // ek sorgu yok - zaten getirilmiş durumda
        }
        return total;
    }
}
