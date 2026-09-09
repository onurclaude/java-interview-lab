package com.interviewlab.jpa.good;

import com.interviewlab.jpa.repository.OrderRepository;
import com.interviewlab.jpa.repository.OrderSummary;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Çağıranın zaten tam {@code Order}/{@code OrderItem} entity'lerine hiç ihtiyaç duymadığı
 * durumlarda en verimli çözüm: tek sorgu, entity yok, persistence-context takip yükü yok,
 * sadece ihtiyaç duyulan tam kolonlar.
 */
@Service
public class DtoProjectionOrderService {

    private final OrderRepository orderRepository;

    public DtoProjectionOrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public int countAllItemsAcrossOrders() {
        List<OrderSummary> summaries = orderRepository.findAllSummaries();
        return summaries.stream().mapToInt(s -> (int) s.itemCount()).sum();
    }
}
