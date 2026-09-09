package com.interviewlab.transaction.rollback;

/** Spring'in varsayılan rollback kurallarını göstermek için kullanılan unchecked bir exception. */
public class SimulatedUncheckedFailureException extends RuntimeException {
    public SimulatedUncheckedFailureException(String message) {
        super(message);
    }
}
