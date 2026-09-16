package com.interviewlab.web.lab.nplusone;

import com.interviewlab.common.sql.SqlStatementRecorder;
import com.interviewlab.jpa.bad.NPlusOneOrderService;
import com.interviewlab.jpa.entity.Order;
import com.interviewlab.jpa.entity.Product;
import com.interviewlab.jpa.good.DtoProjectionOrderService;
import com.interviewlab.jpa.good.EntityGraphOrderService;
import com.interviewlab.jpa.good.FetchJoinOrderService;
import com.interviewlab.jpa.repository.OrderRepository;
import com.interviewlab.jpa.repository.ProductRepository;
import com.interviewlab.web.lab.LabLog;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/INTERACTIVE_LABS.md — LAB 14. N+1 sorgu problemini gerçek Postgres'e karşı, gerçek
 * SQL sayısıyla gösterir: bir parent sorgusu + N adet lazy child sorgusuna karşı, tek bir
 * fetch join / entity graph / DTO projection sorgusu - bkz. docs/n-plus-one.md.
 */
@RestController
@RequestMapping("/api/labs/n-plus-one")
public class NPlusOneLabController {

    private static final int ORDER_COUNT = 10;

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final NPlusOneOrderService nPlusOneOrderService;
    private final FetchJoinOrderService fetchJoinOrderService;
    private final EntityGraphOrderService entityGraphOrderService;
    private final DtoProjectionOrderService dtoProjectionOrderService;

    public NPlusOneLabController(OrderRepository orderRepository, ProductRepository productRepository,
                                  NPlusOneOrderService nPlusOneOrderService, FetchJoinOrderService fetchJoinOrderService,
                                  EntityGraphOrderService entityGraphOrderService,
                                  DtoProjectionOrderService dtoProjectionOrderService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.nPlusOneOrderService = nPlusOneOrderService;
        this.fetchJoinOrderService = fetchJoinOrderService;
        this.entityGraphOrderService = entityGraphOrderService;
        this.dtoProjectionOrderService = dtoProjectionOrderService;
    }

    @PostMapping("/reset")
    @Transactional
    public Map<String, Object> reset() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        Product product = productRepository.save(new Product("Widget", new BigDecimal("9.99")));
        for (int i = 0; i < ORDER_COUNT; i++) {
            Order order = new Order("Customer-" + i);
            order.addItem(product, 1 + i % 3);
            orderRepository.save(order);
        }
        SqlStatementRecorder.clear();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "N_PLUS_ONE");
        body.put("action", "RESET");
        body.put("ordersCreated", ORDER_COUNT);
        body.put("nextStep", "GET /api/labs/n-plus-one/bad");
        return body;
    }

    @GetMapping("/bad")
    public Map<String, Object> bad() {
        LabLog.banner("N+1", "BAD (lazy @OneToMany, döngü içinde erişim)");
        SqlStatementRecorder.clear();
        int totalItems = nPlusOneOrderService.countAllItemsAcrossOrders();
        int queryCount = SqlStatementRecorder.statementsForCurrentThread().size();
        LabLog.line("{} sipariş için toplam {} kalem sayıldı - {} SQL ifadesi çalıştırıldı (1 parent + {} lazy child)",
                ORDER_COUNT, totalItems, queryCount, ORDER_COUNT);
        LabLog.lesson("orderItems LAZY olduğu için, döngüdeki her order.getOrderItems() ayrı bir SELECT tetikledi - "
                + "1 parent sorgusu + N (sipariş sayısı kadar) child sorgusu = N+1.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "N_PLUS_ONE");
        body.put("mode", "BAD");
        body.put("orders", ORDER_COUNT);
        body.put("totalItemsCounted", totalItems);
        body.put("queryCount", queryCount);
        body.put("problem", "N+1: 1 parent sorgusu + " + ORDER_COUNT + " lazy child sorgusu = " + queryCount + " toplam SQL ifadesi.");
        body.put("nextStep", "GET /api/labs/n-plus-one/good");
        return body;
    }

    @GetMapping("/good")
    public Map<String, Object> good() {
        LabLog.banner("N+1", "GOOD (fetch join / entity graph / DTO projection)");
        SqlStatementRecorder.clear();
        int fetchJoinItems = fetchJoinOrderService.countAllItemsAcrossOrders();
        int fetchJoinQueries = SqlStatementRecorder.statementsForCurrentThread().size();

        SqlStatementRecorder.clear();
        int entityGraphItems = entityGraphOrderService.countAllItemsAcrossOrders();
        int entityGraphQueries = SqlStatementRecorder.statementsForCurrentThread().size();

        SqlStatementRecorder.clear();
        int dtoItems = dtoProjectionOrderService.countAllItemsAcrossOrders();
        int dtoQueries = SqlStatementRecorder.statementsForCurrentThread().size();

        LabLog.line("fetch join: {} sorgu, entity graph: {} sorgu, DTO projection: {} sorgu", fetchJoinQueries, entityGraphQueries, dtoQueries);
        // Beklenebilecek şeyin aksine ("üçü de N+1'i 1 sorguya indirir"), gerçek SQL log'u
        // fetch join'in 2 sorgu ürettiğini gösteriyor: `left join fetch o.orderItems`,
        // orderItems N+1'ini düzeltiyor, AMA OrderItem.product hâlâ statik olarak
        // FetchType.EAGER - JOIN FETCH bunu DOKUNMADAN bırakıyor, bu yüzden Hibernate onu
        // ayrı (batch edilmiş) bir sorguyla eager yüklüyor. @EntityGraph tam 1 sorguya
        // iniyor çünkü JPA'nın "fetch graph" semantiği farklı: entity graph'ta AÇIKÇA
        // listelenmeyen ilişkiler (product gibi), statik eşlemeleri EAGER olsa bile bu
        // sorgu için LAZY'ye düşürülür - @EntityGraph, plain bir JOIN FETCH'in yapmadığı bu
        // ek optimizasyonu yapıyor. Bu proje bunu varsaymak yerine gerçek SQL log'undan
        // doğruladı.
        LabLog.lesson("'JOIN FETCH her zaman tam olarak 1 sorguya iner' varsayımı burada YANLIŞ çıktı: fetch join "
                + "orderItems N+1'ini çözdü ama OrderItem.product hâlâ statik EAGER olduğu için ayrı bir sorgu "
                + "daha tetikledi (2 toplam). @EntityGraph'ın 1 sorguya inmesinin nedeni JPA'nın 'fetch graph' "
                + "kuralı: graph'ta listelenmeyen ilişkiler (product), statik EAGER olsalar bile bu sorgu için "
                + "LAZY'ye düşürülür. DTO projection entity hiç materialize etmediği için zaten EAGER'dan etkilenmez.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "N_PLUS_ONE");
        body.put("mode", "GOOD");
        body.put("fetchJoin", Map.of("totalItemsCounted", fetchJoinItems, "queryCount", fetchJoinQueries));
        body.put("entityGraph", Map.of("totalItemsCounted", entityGraphItems, "queryCount", entityGraphQueries));
        body.put("dtoProjection", Map.of("totalItemsCounted", dtoItems, "queryCount", dtoQueries));
        body.put("lesson", "Üçü de orderItems N+1'ini çözdü (bad: " + (ORDER_COUNT + 1) + " sorgu), ama fetch join "
                + "hâlâ OrderItem.product için 2. bir sorguya ihtiyaç duyuyor - sadece @EntityGraph ve DTO "
                + "projection gerçekten tam 1 sorguya iniyor, ikisi farklı nedenlerle (fetch-graph semantiği vs. "
                + "entity materialize etmemek).");
        return body;
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "N_PLUS_ONE");
        body.put("orderCount", orderRepository.count());
        body.put("productCount", productRepository.count());
        body.put("dbeaverQuery", "SELECT * FROM lab_jpa_order; SELECT * FROM lab_jpa_order_item; SELECT * FROM lab_jpa_product;");
        return body;
    }
}
