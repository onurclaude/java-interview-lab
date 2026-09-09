package com.interviewlab.patterns.chainofresponsibility.good;

/** Zincirdeki her halka yalnızca kendi kuralını ve bir sonraki halkaya nasıl ulaşacağını bilir. */
public interface OrderValidator {
    void validate(OrderValidationRequest request);
}
