package com.interviewlab.web.lab.fetch;

import com.interviewlab.common.sql.SqlStatementRecorder;
import com.interviewlab.jpa.bad.EagerFetchAlwaysLoadsService;
import com.interviewlab.jpa.bad.LazyInitializationDemoService;
import com.interviewlab.jpa.entity.Order;
import com.interviewlab.jpa.entity.Product;
import com.interviewlab.jpa.good.LazyAccessWithinTransactionService;
import com.interviewlab.jpa.repository.OrderItemRepository;
import com.interviewlab.jpa.repository.OrderRepository;
import com.interviewlab.jpa.repository.ProductRepository;
import com.interviewlab.web.lab.LabLog;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.LazyInitializationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PRIMARY interactive surface — bkz. docs/DEBUGGER_LABS.md "LAZY/EAGER FETCH" bölümü.
 * {@code NPlusOneTest}, AYNI davranışın otomatik regresyon KANITIdır - birincil öğrenme
 * arayüzü BU controller'dır, test değil. Gerçek bir {@link LazyInitializationException},
 * transaction/session KAPANDIKTAN SONRA lazy erişimle tetikleniyor (sahte/simüle edilmiş
 * değil); EAGER sorgu sayısı {@link SqlStatementRecorder} ile GERÇEKTEN ölçülüyor.
 */
@RestController
@RequestMapping("/api/labs/fetch")
public class LazyEagerLabController {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final LazyInitializationDemoService lazyInitializationDemoService;
    private final LazyAccessWithinTransactionService lazyAccessWithinTransactionService;
    private final EagerFetchAlwaysLoadsService eagerFetchAlwaysLoadsService;

    public LazyEagerLabController(OrderRepository orderRepository, ProductRepository productRepository,
                                   OrderItemRepository orderItemRepository,
                                   LazyInitializationDemoService lazyInitializationDemoService,
                                   LazyAccessWithinTransactionService lazyAccessWithinTransactionService,
                                   EagerFetchAlwaysLoadsService eagerFetchAlwaysLoadsService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
        this.lazyInitializationDemoService = lazyInitializationDemoService;
        this.lazyAccessWithinTransactionService = lazyAccessWithinTransactionService;
        this.eagerFetchAlwaysLoadsService = eagerFetchAlwaysLoadsService;
    }

    @PostMapping("/reset")
    @Transactional
    public Map<String, Object> reset() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        Product product = productRepository.save(new Product("Widget", new BigDecimal("9.99")));
        Order order = new Order("Customer-lazy-eager");
        order.addItem(product, 3);
        Order saved = orderRepository.save(order);
        SqlStatementRecorder.clear();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "LAZY_EAGER_FETCH");
        body.put("action", "RESET");
        body.put("orderId", saved.getId());
        body.put("breakpointHint", "LazyInitializationDemoService.loadOrderWithoutTouchingItems() metoduna "
                + "breakpoint koy - metod dönerken transaction/session'ın kapandığını, order.getOrderItems()'a "
                + "SONRA erişmenin neden patlayacağını izle.");
        body.put("nextStep", "GET /api/labs/fetch/lazy/bad?orderId=" + saved.getId());
        return body;
    }

    @GetMapping("/lazy/bad")
    public Map<String, Object> lazyBad(Long orderId) {
        LabLog.banner("LAZY FETCH", "BAD (transaction dışında lazy erişim)");
        Order order = lazyInitializationDemoService.loadOrderWithoutTouchingItems(orderId); // <- BREAKPOINT: metod döndüğünde session KAPANDI
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "LAZY_EAGER_FETCH");
        body.put("mode", "BAD_LAZY_ACCESS_OUTSIDE_TRANSACTION");
        try {
            int size = order.getOrderItems().size(); // <- BREAKPOINT: burada session artık yok - GERÇEK exception fırlar
            body.put("orderItemsSize", size);
            body.put("threwLazyInitializationException", false);
        } catch (LazyInitializationException e) {
            LabLog.lesson("order.getOrderItems() transaction/session KAPANDIKTAN SONRA çağrıldı - Hibernate "
                    + "GERÇEK bir LazyInitializationException fırlattı (simüle edilmedi).");
            body.put("threwLazyInitializationException", true);
            body.put("exceptionMessage", e.getMessage());
        }
        body.put("problem", "Entity, transaction dışına 'sızdı' ve lazy koleksiyonuna erişim artık mümkün değil.");
        body.put("nextStep", "GET /api/labs/fetch/lazy/good?orderId=" + orderId);
        return body;
    }

    @GetMapping("/lazy/good")
    public Map<String, Object> lazyGood(Long orderId) {
        LabLog.banner("LAZY FETCH", "GOOD (transaction hâlâ açıkken erişim)");
        int size = lazyAccessWithinTransactionService.loadOrderAndCountItemsWithinTransaction(orderId); // <- BREAKPOINT: erişim METODUN İÇİNDE, transaction hâlâ açık
        LabLog.lesson("order.getOrderItems() ARTIK transaction hâlâ AÇIKKEN, aynı @Transactional metodun içinde "
                + "çağrıldı - session mevcut, lazy proxy başarıyla başlatıldı.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "LAZY_EAGER_FETCH");
        body.put("mode", "GOOD_LAZY_ACCESS_INSIDE_TRANSACTION");
        body.put("orderItemsSize", size);
        body.put("threwLazyInitializationException", false);
        body.put("lesson", "Lazy koleksiyona, onu yükleyen transaction/session HÂLÂ AÇIKKEN erişildi - hiç exception yok.");
        return body;
    }

    @GetMapping("/eager/bad")
    public Map<String, Object> eagerBad() {
        LabLog.banner("EAGER FETCH", "BAD (OrderItem.product FetchType.EAGER - hiç kullanılmasa bile)");
        SqlStatementRecorder.clear();
        int totalQuantity = eagerFetchAlwaysLoadsService.sumQuantitiesOnly(); // <- BREAKPOINT: product'a HİÇ dokunulmuyor
        long queryCountTouchingProduct = SqlStatementRecorder.countMatching(Thread.currentThread().getName(), "PRODUCT");
        int totalQueries = SqlStatementRecorder.statementsForCurrentThread().size();
        LabLog.line("sumQuantitiesOnly() -> toplam={}, product tablosuna değen sorgu sayısı={}, toplam sorgu={}",
                totalQuantity, queryCountTouchingProduct, totalQueries);
        LabLog.lesson("Bu metot product'a HİÇ dokunmuyor (sadece quantity topluyor), ama OrderItem.product "
                + "FetchType.EAGER olduğu için Hibernate GERÇEKTEN product tablosunu join/select ediyor - "
                + "SQL log'unda GÖZLEMLENEBİLİR, gereksiz bir maliyet.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "LAZY_EAGER_FETCH");
        body.put("mode", "BAD_EAGER_ALWAYS_LOADS");
        body.put("totalQuantity", totalQuantity);
        body.put("totalQueries", totalQueries);
        body.put("queriesTouchingProductTable", queryCountTouchingProduct);
        body.put("problem", "product hiç okunmadığı halde EAGER olduğu için her yüklemede sorguya dahil edildi.");
        return body;
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "LAZY_EAGER_FETCH");
        body.put("orderCount", orderRepository.count());
        body.put("orderItemCount", orderItemRepository.count());
        body.put("productCount", productRepository.count());
        return body;
    }
}
