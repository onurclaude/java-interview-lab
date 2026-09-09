package com.interviewlab.patterns.observer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationListener {

    private final List<String> notifiedOrderIds = new CopyOnWriteArrayList<>();

    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        notifiedOrderIds.add(event.orderId());
    }

    public List<String> notifiedOrderIds() {
        return List.copyOf(notifiedOrderIds);
    }
}
