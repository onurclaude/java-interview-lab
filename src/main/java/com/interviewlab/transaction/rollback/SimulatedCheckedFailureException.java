package com.interviewlab.transaction.rollback;

/** Sadece Spring'in varsayılan rollback kurallarını göstermek için kullanılan checked bir exception. */
public class SimulatedCheckedFailureException extends Exception {
    public SimulatedCheckedFailureException(String message) {
        super(message);
    }
}
