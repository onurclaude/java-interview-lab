package com.interviewlab.patterns.chainofresponsibility.good;

public record OrderValidationRequest(int stockAvailable, int quantity, boolean flaggedForFraud,
                                      double amount, double spendingLimit) {
}
