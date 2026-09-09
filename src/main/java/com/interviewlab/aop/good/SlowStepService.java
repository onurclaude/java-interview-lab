package com.interviewlab.aop.good;

import com.interviewlab.aop.TrackExecutionTime;
import org.springframework.stereotype.Service;

/** {@link OrderProcessingService}'in onu gerçek bir proxy üzerinden çağırabilmesi için kendi bean'inde yaşar. */
@Service
public class SlowStepService {

    @TrackExecutionTime
    public void slowStep() {
        sleep(20);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
