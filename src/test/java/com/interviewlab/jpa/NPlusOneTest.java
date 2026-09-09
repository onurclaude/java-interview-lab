package com.interviewlab.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.interviewlab.AbstractPostgresIntegrationTest;
import com.interviewlab.common.sql.SqlStatementRecorder;
import com.interviewlab.jpa.bad.EagerFetchAlwaysLoadsService;
import com.interviewlab.jpa.bad.LazyInitializationDemoService;
import com.interviewlab.jpa.bad.NPlusOneOrderService;
import com.interviewlab.jpa.entity.Order;
import com.interviewlab.jpa.entity.Product;
import com.interviewlab.jpa.good.DtoProjectionOrderService;
import com.interviewlab.jpa.good.EntityGraphOrderService;
import com.interviewlab.jpa.good.FetchJoinOrderService;
import com.interviewlab.jpa.repository.OrderRepository;
import com.interviewlab.jpa.repository.ProductRepository;
import java.math.BigDecimal;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Yazı ve mülakat cevapları için docs/n-plus-one.md dosyasına bakın. */
class NPlusOneTest extends AbstractPostgresIntegrationTest {

    private static final int ORDER_COUNT = 5;
    private static final int ITEMS_PER_ORDER = 3;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private NPlusOneOrderService nPlusOneOrderService;

    @Autowired
    private FetchJoinOrderService fetchJoinOrderService;

    @Autowired
    private EntityGraphOrderService entityGraphOrderService;

    @Autowired
    private DtoProjectionOrderService dtoProjectionOrderService;

    @Autowired
    private LazyInitializationDemoService lazyInitializationDemoService;

    @Autowired
    private EagerFetchAlwaysLoadsService eagerFetchAlwaysLoadsService;

    private Long firstOrderId;

    @BeforeEach
    void seedOrdersWithItems() {
        orderRepository.deleteAll();
        productRepository.deleteAll();

        Product product = productRepository.save(new Product("Widget", new BigDecimal("9.99")));
        for (int i = 0; i < ORDER_COUNT; i++) {
            Order order = new Order("customer-" + i);
            for (int j = 0; j < ITEMS_PER_ORDER; j++) {
                order.addItem(product, 1);
            }
            Order saved = orderRepository.save(order);
            if (i == 0) {
                firstOrderId = saved.getId();
            }
        }
        SqlStatementRecorder.clear();
    }

    @Test
    void shouldIssueOneQueryPerOrderWhenTouchingLazyCollectionInALoop() {
        int total = nPlusOneOrderService.countAllItemsAcrossOrders();

        assertThat(total).isEqualTo(ORDER_COUNT * ITEMS_PER_ORDER);
        long itemSelectCount = SqlStatementRecorder.allStatements().stream()
                .filter(sql -> sql.toLowerCase().contains("from lab_jpa_order_item"))
                .count();
        assertThat(itemSelectCount)
                .as("her sipariş için lazy orderItems koleksiyonuna ayrı bir SELECT - yani N+1")
                .isEqualTo(ORDER_COUNT);
    }

    @Test
    void shouldIssueOnlyOneQueryTotalWithFetchJoin() {
        int total = fetchJoinOrderService.countAllItemsAcrossOrders();

        assertThat(total).isEqualTo(ORDER_COUNT * ITEMS_PER_ORDER);
        long orderRelatedStatements = SqlStatementRecorder.allStatements().stream()
                .filter(sql -> sql.toLowerCase().contains("lab_jpa_order"))
                .count();
        assertThat(orderRelatedStatements)
                .as("tek bir fetch-join sorgusu, siparişleri ve öğelerini birlikte yüklemeli")
                .isEqualTo(1);
    }

    @Test
    void shouldIssueOnlyOneQueryTotalWithEntityGraph() {
        int total = entityGraphOrderService.countAllItemsAcrossOrders();

        assertThat(total).isEqualTo(ORDER_COUNT * ITEMS_PER_ORDER);
        long orderRelatedStatements = SqlStatementRecorder.allStatements().stream()
                .filter(sql -> sql.toLowerCase().contains("lab_jpa_order"))
                .count();
        assertThat(orderRelatedStatements).isEqualTo(1);
    }

    @Test
    void shouldIssueOnlyOneQueryTotalWithDtoProjection() {
        int total = dtoProjectionOrderService.countAllItemsAcrossOrders();

        assertThat(total).isEqualTo(ORDER_COUNT * ITEMS_PER_ORDER);
        assertThat(SqlStatementRecorder.allStatements())
                .as("çalıştırılan tek ifade bir DTO projeksiyon sorgusu olmalı")
                .hasSize(1);
    }

    @Test
    void shouldThrowLazyInitializationExceptionWhenAccessingLazyCollectionOutsideTransaction() {
        Order order = lazyInitializationDemoService.loadOrderWithoutTouchingItems(firstOrderId);

        assertThatThrownBy(() -> order.getOrderItems().size())
                .isInstanceOf(LazyInitializationException.class);
    }

    @Test
    void shouldAlwaysJoinProductWhenLoadingOrderItemsDueToEagerFetch() {
        int totalQuantity = eagerFetchAlwaysLoadsService.sumQuantitiesOnly();

        assertThat(totalQuantity).isEqualTo(ORDER_COUNT * ITEMS_PER_ORDER);
        assertThat(SqlStatementRecorder.allStatements())
                .as("sumQuantitiesOnly() hiçbir zaman getProduct() okumasa bile, EAGER eşleme "
                        + "bir OrderItem yükleyen her sorguya yine de ürün verisini dahil ediyor")
                .anyMatch(sql -> sql.toLowerCase().contains("lab_jpa_product"));
    }
}
