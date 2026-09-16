package com.interviewlab.labrunner.spring;

import com.interviewlab.labrunner.LabRunnerPrint;
import com.interviewlab.patterns.factory.PaymentStrategyFactory;
import com.interviewlab.patterns.observer.EmailNotificationListener;
import com.interviewlab.patterns.observer.InventoryReservationListener;
import com.interviewlab.patterns.observer.OrderPlacementService;
import com.interviewlab.patterns.strategy.bad.IfElsePaymentProcessor;
import com.interviewlab.patterns.strategy.good.PaymentProcessor;

/** IntelliJ'de sağ tık -> Run/Debug. Postman/HTTP GEREKMEZ. `docker compose up -d` GEREKİR. */
public final class DesignPatternsStrategyFactoryObserverSpringLabRunner {

    public static void main(String[] args) {
        SpringLabRunnerSupport.run(ctx -> {
            LabRunnerPrint.banner("DESIGN PATTERN — Strategy+Factory ve Observer (gerçek Spring bean discovery/event)");

            IfElsePaymentProcessor ifElse = ctx.getBean(IfElsePaymentProcessor.class);
            PaymentProcessor strategy = ctx.getBean(PaymentProcessor.class);
            PaymentStrategyFactory factory = ctx.getBean(PaymentStrategyFactory.class);

            LabRunnerPrint.section("STRATEGY + FACTORY");
            String badResult = ifElse.process("CREDIT_CARD", 100.0); // <- BREAKPOINT 1: if/else dallanma
            LabRunnerPrint.fact("BAD (if/else) result", badResult);
            String goodResult = strategy.process("CREDIT_CARD", 100.0); // <- BREAKPOINT 2: PaymentStrategyFactory.getStrategy() ile Map lookup
            LabRunnerPrint.fact("GOOD (Strategy+Factory) result", goodResult);
            LabRunnerPrint.fact("Factory ile EXTERNAL_PROVIDER (Adapter) de AYNI şekilde çözülür",
                    factory.getStrategy("EXTERNAL_PROVIDER").pay(50.0));

            LabRunnerPrint.section("OBSERVER");
            OrderPlacementService orderPlacementService = ctx.getBean(OrderPlacementService.class);
            InventoryReservationListener inventoryListener = ctx.getBean(InventoryReservationListener.class);
            EmailNotificationListener emailListener = ctx.getBean(EmailNotificationListener.class);
            orderPlacementService.placeOrder("runner-order-1", 75.0); // <- BREAKPOINT 3: publishEvent - kimin dinlediğini BİLMİYOR
            LabRunnerPrint.fact("reservedByInventoryListener", inventoryListener.reservedOrderIds().contains("runner-order-1")); // <- BREAKPOINT 4
            LabRunnerPrint.fact("notifiedByEmailListener", emailListener.notifiedOrderIds().contains("runner-order-1")); // <- BREAKPOINT 5

            LabRunnerPrint.section("WHY");
            LabRunnerPrint.line("Factory, yeni bir @Component PaymentStrategy eklendiğinde HİÇBİR DEĞİŞİKLİK gerektirmez -");
            LabRunnerPrint.line("Adapter (EXTERNAL_PROVIDER) bile AYNI Map lookup'ından geçiyor. TEK bir publishEvent()");
            LabRunnerPrint.line("çağrısı, BİRBİRİNDEN BAĞIMSIZ İKİ listener'ı TETİKLEDİ - subject bunların hiçbirini bilmiyor.");
        });
    }
}
