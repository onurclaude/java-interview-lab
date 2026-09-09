package com.interviewlab.patterns.observer;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Elle yazılmış bir listener listesi yerine Spring'in yerleşik event mekanizmasını kullanan
 * Observer: {@link OrderPlacementService} (subject), {@link OrderPlacedEvent}'i kimin
 * dinlediğinden -eğer dinleyen varsa- haberdar değildir; sadece publish eder. İstenildiği
 * kadar observer ({@link InventoryReservationListener}, {@link EmailNotificationListener},
 * daha sonra eklenecek başkaları) bu sınıf hiç değişmeden tepki verebilir. Bu, GoF Observer
 * pattern'inin tanımladığı ile aynı ayrıştırmadır; Spring'in {@link ApplicationEventPublisher}
 * + {@code @EventListener} kombinasyonu bunun hazır bir implementasyonundan ibarettir.
 */
@Service
public class OrderPlacementService {

    private final ApplicationEventPublisher eventPublisher;

    public OrderPlacementService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void placeOrder(String orderId, double amount) {
        eventPublisher.publishEvent(new OrderPlacedEvent(orderId, amount));
    }
}
