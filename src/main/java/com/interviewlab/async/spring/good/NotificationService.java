package com.interviewlab.async.spring.good;

import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final AsyncSender asyncSender;

    public NotificationService(AsyncSender asyncSender) {
        this.asyncSender = asyncSender;
    }

    public String notifyUser(String message) {
        asyncSender.sendAsync(message); // gerçek proxy çağrısı -> gerçekten async executor üzerinde çalışır
        return Thread.currentThread().getName();
    }
}
