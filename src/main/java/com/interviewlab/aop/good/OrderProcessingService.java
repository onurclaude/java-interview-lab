package com.interviewlab.aop.good;

import org.springframework.stereotype.Service;

@Service
public class OrderProcessingService {

    private final SlowStepService slowStepService;

    public OrderProcessingService(SlowStepService slowStepService) {
        this.slowStepService = slowStepService;
    }

    public void processOrder() {
        slowStepService.slowStep(); // gerçek proxy çağrısı -> aspect gerçekten çalışır
    }
}
