package com.interviewlab.patterns.observer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryReservationListener {

    private final List<String> reservedOrderIds = new CopyOnWriteArrayList<>();

    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        reservedOrderIds.add(event.orderId());
    }

    public List<String> reservedOrderIds() {
        return List.copyOf(reservedOrderIds);
    }
}
