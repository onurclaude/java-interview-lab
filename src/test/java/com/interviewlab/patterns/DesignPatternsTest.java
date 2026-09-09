package com.interviewlab.patterns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.interviewlab.AbstractPostgresIntegrationTest;
import com.interviewlab.patterns.adapter.ExternalPaymentProviderAdapter;
import com.interviewlab.patterns.builder.OrderRequest;
import com.interviewlab.patterns.chainofresponsibility.good.OrderValidationChain;
import com.interviewlab.patterns.chainofresponsibility.good.OrderValidationRequest;
import com.interviewlab.patterns.decorator.BasePricedItem;
import com.interviewlab.patterns.decorator.HandlingFeeDecorator;
import com.interviewlab.patterns.decorator.PercentageDiscountDecorator;
import com.interviewlab.patterns.decorator.PricedItem;
import com.interviewlab.patterns.facade.CheckoutFacade;
import com.interviewlab.patterns.observer.EmailNotificationListener;
import com.interviewlab.patterns.observer.InventoryReservationListener;
import com.interviewlab.patterns.observer.OrderPlacementService;
import com.interviewlab.patterns.proxy.Greeter;
import com.interviewlab.patterns.proxy.GreeterProxyFactory;
import com.interviewlab.patterns.singleton.ClassicSingleton;
import com.interviewlab.patterns.strategy.bad.IfElsePaymentProcessor;
import com.interviewlab.patterns.strategy.good.PaymentProcessor;
import com.interviewlab.patterns.templatemethod.CreditCardPaymentProcessing;
import com.interviewlab.patterns.templatemethod.WalletPaymentProcessing;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Her pattern için Problem/Bad/Why/Pattern/Good/When-not-to-use/Interview-answer bölümlerine docs/design-patterns.md dosyasından bakın. */
class DesignPatternsTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private IfElsePaymentProcessor ifElsePaymentProcessor;

    @Autowired
    private PaymentProcessor paymentProcessor;

    @Autowired
    private ExternalPaymentProviderAdapter externalPaymentProviderAdapter;

    @Autowired
    private OrderValidationChain orderValidationChain;

    @Autowired
    private CheckoutFacade checkoutFacade;

    @Autowired
    private OrderPlacementService orderService;

    @Autowired
    private InventoryReservationListener inventoryReservationListener;

    @Autowired
    private EmailNotificationListener emailNotificationListener;

    @Test
    void strategyAndFactoryShouldProduceSameResultAsIfElseChain() {
        assertThat(paymentProcessor.process("CREDIT_CARD", 100))
                .isEqualTo(ifElsePaymentProcessor.process("CREDIT_CARD", 100));
        assertThat(paymentProcessor.process("WALLET", 100))
                .isEqualTo(ifElsePaymentProcessor.process("WALLET", 100));
    }

    @Test
    void templateMethodShouldRunSameSkeletonWithDifferentAuthorizeStep() {
        List<String> creditCardSteps = new CreditCardPaymentProcessing().process(50);
        List<String> walletSteps = new WalletPaymentProcessing().process(50);

        assertThat(creditCardSteps).hasSize(4);
        assertThat(walletSteps).hasSize(4);
        assertThat(creditCardSteps.get(1)).contains("3ds");
        assertThat(walletSteps.get(1)).contains("balance-check");
        // validate/execute/audit adımları (indeks 0, 2, 3) paylaşılan template'ten gelir, değişmez.
        assertThat(creditCardSteps.get(0)).isEqualTo(walletSteps.get(0));
        assertThat(creditCardSteps.get(2)).isEqualTo(walletSteps.get(2));
        assertThat(creditCardSteps.get(3)).isEqualTo(walletSteps.get(3));
    }

    @Test
    void observerShouldNotifyEveryRegisteredListenerWithoutOrderServiceKnowingAboutThem() {
        orderService.placeOrder("order-xyz", 42.0);

        await().atMost(Duration.ofSeconds(2)).until(() -> inventoryReservationListener.reservedOrderIds().contains("order-xyz"));
        assertThat(emailNotificationListener.notifiedOrderIds()).contains("order-xyz");
    }

    @Test
    void decoratorShouldStackAdjustmentsWithoutASubclassPerCombination() {
        PricedItem base = new BasePricedItem(100.0);
        PricedItem discounted = new PercentageDiscountDecorator(base, 0.10);
        PricedItem discountedWithFee = new HandlingFeeDecorator(discounted, 5.0);

        assertThat(base.price()).isEqualTo(100.0);
        assertThat(discounted.price()).isEqualTo(90.0);
        assertThat(discountedWithFee.price()).isEqualTo(95.0);
    }

    @Test
    void adapterShouldLetIncompatibleSdkBeUsedAsAPaymentStrategy() {
        assertThat(externalPaymentProviderAdapter.pay(10.0)).contains("Charged via external provider");
        assertThatThrownBy(() -> externalPaymentProviderAdapter.pay(-5.0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void facadeShouldOrchestrateValidationPaymentAndNotificationInOneCall() {
        String result = checkoutFacade.checkout("WALLET", 25.0);
        assertThat(result).contains("Order").contains("Debited wallet: 25.0");
    }

    @Test
    void builderShouldRejectAnIncompleteOrderAtBuildTime() {
        assertThatThrownBy(() -> OrderRequest.builder("customer-1").build())
                .as("hiç öğe eklenmedi - builder, geçersiz bir nesne vermeyi reddetmeli")
                .isInstanceOf(IllegalStateException.class);

        OrderRequest request = OrderRequest.builder("customer-1")
                .items(List.of("item-1", "item-2"))
                .giftWrap(true)
                .build();
        assertThat(request.itemIds()).hasSize(2);
        assertThat(request.giftWrap()).isTrue();
        assertThat(request.couponCode()).isNull(); // opsiyonel alan, hiç ayarlanmadı
    }

    @Test
    void classicSingletonShouldAlwaysReturnTheSameInstance() {
        ClassicSingleton first = ClassicSingleton.getInstance();
        ClassicSingleton second = ClassicSingleton.getInstance();
        assertThat(first).isSameAs(second);
    }

    @Test
    void proxyShouldInterceptEveryCallBeforeDelegatingToTheRealObject() {
        List<String> log = new CopyOnWriteArrayList<>();
        Greeter real = name -> "Hello, " + name;
        Greeter proxied = GreeterProxyFactory.withLogging(real, log::add);

        String result = proxied.greet("Ada");

        assertThat(result).isEqualTo("Hello, Ada");
        assertThat(log).containsExactly("before:greet", "after:greet");
    }

    @Test
    void chainOfResponsibilityShouldRunEachIndependentValidatorInOrder() {
        OrderValidationRequest valid = new OrderValidationRequest(10, 2, false, 50, 100);
        orderValidationChain.validate(valid); // exception fırlatmamalı

        OrderValidationRequest outOfStock = new OrderValidationRequest(1, 2, false, 50, 100);
        assertThatThrownBy(() -> orderValidationChain.validate(outOfStock)).hasMessageContaining("stock");

        OrderValidationRequest fraud = new OrderValidationRequest(10, 2, true, 50, 100);
        assertThatThrownBy(() -> orderValidationChain.validate(fraud)).hasMessageContaining("fraud");

        OrderValidationRequest overLimit = new OrderValidationRequest(10, 2, false, 500, 100);
        assertThatThrownBy(() -> orderValidationChain.validate(overLimit)).hasMessageContaining("limit");
    }
}
