package com.interviewlab.aop;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewlab.AbstractPostgresIntegrationTest;
import com.interviewlab.aop.bad.SelfInvocationTimingService;
import com.interviewlab.aop.good.OrderProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Yazı ve mülakat cevapları için docs/aop.md dosyasına bakın. */
class AopSelfInvocationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private SelfInvocationTimingService selfInvocationTimingService;

    @Autowired
    private OrderProcessingService orderProcessingService;

    @Autowired
    private ExecutionTimeAspect executionTimeAspect;

    @Test
    void shouldNotTrackExecutionTimeDuringSelfInvocation() {
        selfInvocationTimingService.processOrder();

        assertThat(executionTimeAspect.lastDurationMillis("SelfInvocationTimingService.slowStep()"))
                .as("self-invocation, aspect'i tamamen atlamalıdır - hiçbir süre asla kaydedilmemelidir")
                .isNull();
    }

    @Test
    void shouldTrackExecutionTimeWhenCalledThroughARealProxy() {
        orderProcessingService.processOrder();

        assertThat(executionTimeAspect.lastDurationMillis("SlowStepService.slowStep()"))
                .as("gerçek bir proxy çağrısı, aspect'in süreyi ölçüp kaydetmesine izin vermelidir")
                .isNotNull()
                .isGreaterThanOrEqualTo(0L);
    }
}
