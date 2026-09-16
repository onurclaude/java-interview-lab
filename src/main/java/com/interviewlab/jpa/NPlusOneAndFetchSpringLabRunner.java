package com.interviewlab.jpa;

import com.interviewlab.labrunner.spring.SpringLabRunnerSupport;

import com.interviewlab.common.sql.SqlStatementRecorder;
import com.interviewlab.jpa.bad.EagerFetchAlwaysLoadsService;
import com.interviewlab.jpa.bad.LazyInitializationDemoService;
import com.interviewlab.jpa.bad.NPlusOneOrderService;
import com.interviewlab.jpa.entity.Order;
import com.interviewlab.jpa.entity.Product;
import com.interviewlab.jpa.good.DtoProjectionOrderService;
import com.interviewlab.jpa.good.EntityGraphOrderService;
import com.interviewlab.jpa.good.FetchJoinOrderService;
import com.interviewlab.jpa.good.LazyAccessWithinTransactionService;
import com.interviewlab.jpa.repository.OrderRepository;
import com.interviewlab.jpa.repository.ProductRepository;
import com.interviewlab.labrunner.LabRunnerPrint;
import java.math.BigDecimal;
import org.hibernate.LazyInitializationException;

/** IntelliJ'de sağ tık -> Run/Debug. Postman/HTTP GEREKMEZ. `docker compose up -d` GEREKİR. */
public final class NPlusOneAndFetchSpringLabRunner {

    public static void main(String[] args) {
        SpringLabRunnerSupport.run(ctx -> {
            LabRunnerPrint.banner("N+1 / LAZY-EAGER FETCH — gerçek SQL sayısı");

            OrderRepository orderRepository = ctx.getBean(OrderRepository.class);
            ProductRepository productRepository = ctx.getBean(ProductRepository.class);
            NPlusOneOrderService nPlusOneOrderService = ctx.getBean(NPlusOneOrderService.class);
            FetchJoinOrderService fetchJoinOrderService = ctx.getBean(FetchJoinOrderService.class);
            EntityGraphOrderService entityGraphOrderService = ctx.getBean(EntityGraphOrderService.class);
            DtoProjectionOrderService dtoProjectionOrderService = ctx.getBean(DtoProjectionOrderService.class);
            LazyInitializationDemoService lazyBadService = ctx.getBean(LazyInitializationDemoService.class);
            LazyAccessWithinTransactionService lazyGoodService = ctx.getBean(LazyAccessWithinTransactionService.class);
            EagerFetchAlwaysLoadsService eagerService = ctx.getBean(EagerFetchAlwaysLoadsService.class);

            orderRepository.deleteAll();
            productRepository.deleteAll();
            Product product = productRepository.save(new Product("Widget", new BigDecimal("9.99")));
            for (int i = 0; i < 10; i++) {
                Order order = new Order("Customer-" + i);
                order.addItem(product, 1 + i % 3);
                orderRepository.save(order);
            }

            LabRunnerPrint.section("N+1");
            SqlStatementRecorder.clear();
            nPlusOneOrderService.countAllItemsAcrossOrders(); // <- BREAKPOINT 1: döngü içinde order.getOrderItems() - HER order için AYRI SELECT
            LabRunnerPrint.fact("BAD queryCount (1 parent + 10 lazy child = 11)", SqlStatementRecorder.statementsForCurrentThread().size());

            SqlStatementRecorder.clear();
            fetchJoinOrderService.countAllItemsAcrossOrders(); // <- BREAKPOINT 2
            LabRunnerPrint.fact("GOOD fetchJoin queryCount (2 - product hâlâ EAGER)", SqlStatementRecorder.statementsForCurrentThread().size());
            SqlStatementRecorder.clear();
            entityGraphOrderService.countAllItemsAcrossOrders();
            LabRunnerPrint.fact("GOOD entityGraph queryCount (1)", SqlStatementRecorder.statementsForCurrentThread().size());
            SqlStatementRecorder.clear();
            dtoProjectionOrderService.countAllItemsAcrossOrders();
            LabRunnerPrint.fact("GOOD dtoProjection queryCount (1)", SqlStatementRecorder.statementsForCurrentThread().size());

            LabRunnerPrint.section("LAZY FETCH");
            Long orderId = orderRepository.findAll().get(0).getId();
            Order detachedOrder = lazyBadService.loadOrderWithoutTouchingItems(orderId); // <- BREAKPOINT 3: metot döner, session KAPANIR
            boolean threwException;
            try {
                detachedOrder.getOrderItems().size(); // <- BREAKPOINT 4: session artık YOK - GERÇEK LazyInitializationException
                threwException = false;
            } catch (LazyInitializationException e) {
                threwException = true;
                LabRunnerPrint.fact("BAD exception (GERÇEK, simüle değil)", e.getMessage());
            }
            LabRunnerPrint.fact("BAD threwLazyInitializationException", threwException);

            int itemsInTransaction = lazyGoodService.loadOrderAndCountItemsWithinTransaction(orderId); // <- BREAKPOINT 5: erişim METODUN İÇİNDE, transaction hâlâ açık
            LabRunnerPrint.fact("GOOD orderItemsSize (exception YOK)", itemsInTransaction);

            LabRunnerPrint.section("EAGER FETCH");
            SqlStatementRecorder.clear();
            eagerService.sumQuantitiesOnly(); // <- BREAKPOINT 6: product'a HİÇ dokunulmuyor ama yine de sorgulanıyor
            long productQueries = SqlStatementRecorder.countMatching(Thread.currentThread().getName(), "PRODUCT");
            LabRunnerPrint.fact("queriesTouchingProductTable (>0 olmalı)", productQueries);

            LabRunnerPrint.section("WHY");
            LabRunnerPrint.line("Lazy koleksiyona döngüde erişmek 1+N sorgu üretir. Lazy koleksiyon SADECE onu yükleyen");
            LabRunnerPrint.line("transaction/session AÇIKKEN erişilebilir. EAGER ilişki, HİÇ okunmasa bile HER");
            LabRunnerPrint.line("yüklemede sorgulanır.");
        });
    }
}
