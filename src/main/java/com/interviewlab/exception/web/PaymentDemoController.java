package com.interviewlab.exception.web;

import com.interviewlab.exception.good.WrappingExceptionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentDemoController {

    private final WrappingExceptionService wrappingExceptionService;

    public PaymentDemoController(WrappingExceptionService wrappingExceptionService) {
        this.wrappingExceptionService = wrappingExceptionService;
    }

    @PostMapping("/lab/exceptions/charge")
    public void charge(@RequestParam String cardToken, @RequestParam double amount) {
        wrappingExceptionService.chargeCard(cardToken, amount); // fırlatır -> GlobalExceptionHandler tarafından ele alınır
    }
}
