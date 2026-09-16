package com.interviewlab.javacore;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewlab.javacore.saga.OrderSagaOrchestrator;
import com.interviewlab.javacore.saga.OrderSagaOrchestrator.SagaResult;
import com.interviewlab.javacore.saga.OrderSagaOrchestrator.SagaStep;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * Order -> Payment -> Stock -> Shipping senaryosu: Stock adımı başarısız olur (yetersiz
 * stok), Payment compensating action (iade) ile geri alınır, Order compensating action
 * (iptal) ile geri alınır - Shipping adımına hiç ulaşılmaz. Bkz. docs/distributed-systems.md.
 */
class OrderSagaOrchestratorTest {

    @Test
    void shouldCompensatePreviousStepsInReverseOrderWhenAStepFails() {
        AtomicBoolean orderCreated = new AtomicBoolean(false);
        AtomicBoolean orderCancelled = new AtomicBoolean(false);
        AtomicBoolean paymentCharged = new AtomicBoolean(false);
        AtomicBoolean paymentRefunded = new AtomicBoolean(false);
        AtomicBoolean shippingArranged = new AtomicBoolean(false);

        List<SagaStep> steps = List.of(
                new SagaStep("CreateOrder", () -> {
                    orderCreated.set(true);
                    return true;
                }, () -> orderCancelled.set(true)),
                new SagaStep("ChargePayment", () -> {
                    paymentCharged.set(true);
                    return true;
                }, () -> paymentRefunded.set(true)),
                new SagaStep("ReserveStock", () -> false /* yetersiz stok - BAŞARISIZ */, () -> {
                }),
                new SagaStep("ArrangeShipping", () -> {
                    shippingArranged.set(true); // buraya ASLA ulaşılmamalı
                    return true;
                }, () -> {
                }));

        OrderSagaOrchestrator orchestrator = new OrderSagaOrchestrator();
        SagaResult result = orchestrator.run(steps);

        assertThat(result.success()).isFalse();
        assertThat(result.failedStep()).isEqualTo("ReserveStock");
        assertThat(orderCreated).isTrue();
        assertThat(paymentCharged).isTrue();
        assertThat(shippingArranged)
                .as("ReserveStock başarısız olduğu için ArrangeShipping'e hiç ulaşılmamalı")
                .isFalse();

        assertThat(orderCancelled)
                .as("CreateOrder, başarısızlık sonrası TELAFİ EDİLMELİ (compensating action çalışmalı)")
                .isTrue();
        assertThat(paymentRefunded)
                .as("ChargePayment de telafi edilmeli - para iade edilmeli")
                .isTrue();
        assertThat(result.compensatedSteps())
                .as("telafi TERS SIRADA olmalı: en son commit edilen (ChargePayment) ilk telafi edilir")
                .containsExactly("ChargePayment", "CreateOrder");
    }

    @Test
    void shouldNotCompensateAnythingWhenAllStepsSucceed() {
        List<SagaStep> steps = List.of(
                new SagaStep("CreateOrder", () -> true, () -> {
                    throw new AssertionError("compensation should never run");
                }),
                new SagaStep("ChargePayment", () -> true, () -> {
                    throw new AssertionError("compensation should never run");
                }));

        SagaResult result = new OrderSagaOrchestrator().run(steps);

        assertThat(result.success()).isTrue();
        assertThat(result.executedSteps()).containsExactly("CreateOrder", "ChargePayment");
        assertThat(result.compensatedSteps()).isEmpty();
    }
}
