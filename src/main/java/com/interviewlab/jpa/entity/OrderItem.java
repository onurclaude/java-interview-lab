package com.interviewlab.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "lab_jpa_order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "jpa_order_item_seq")
    @SequenceGenerator(name = "jpa_order_item_seq", sequenceName = "lab_jpa_order_item_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /**
     * {@code @ManyToOne}, VARSAYILAN olarak {@code FetchType.EAGER}'dır - bu seçimi görünür
     * kılmak için burada açıkça yazılmıştır. "Her şeyi EAGER yap"ın N+1'den kaçmanın bedava
     * bir yolu olmadığını görmek için bkz.
     * {@link com.interviewlab.jpa.bad.EagerFetchAlwaysLoadsService}: EAGER, çağıran ona hiç
     * bakmasa da, getirilen HER OrderItem için product'ı yükler.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    protected OrderItem() {
    }

    public OrderItem(Order order, Product product, int quantity) {
        this.order = order;
        this.product = product;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }
}
