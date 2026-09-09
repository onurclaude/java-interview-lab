package com.interviewlab.transaction.propagation;

/** Bilinçli olarak unchecked: dış @Transactional'ın varsayılan olarak rollback olmasını garanti eder. */
public class OrderProcessingException extends RuntimeException {
    public OrderProcessingException(String message) {
        super(message);
    }
}
