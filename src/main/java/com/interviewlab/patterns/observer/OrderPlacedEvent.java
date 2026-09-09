package com.interviewlab.patterns.observer;

public record OrderPlacedEvent(String orderId, double amount) {
}
