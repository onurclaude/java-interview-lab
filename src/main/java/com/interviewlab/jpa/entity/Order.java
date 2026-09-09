package com.interviewlab.jpa.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code @OneToMany}, varsayılan olarak {@code FetchType.LAZY}'dir (varsayılan olarak EAGER
 * olan {@code @ManyToOne}/{@code @OneToOne}'un aksine - bkz. {@link OrderItem#getProduct()}).
 * Bu varsayılan, klasik N+1 problemini mümkün kılan tam olarak budur: {@code orderItems},
 * {@code Order} ile birlikte yüklenmez, bu yüzden ona daha sonra dokunmak ayrı bir sorguyu
 * tetikler - bir döngü içinde yapılıyorsa, sipariş başına bir kez. Bkz. docs/n-plus-one.md.
 */
@Entity
@Table(name = "lab_jpa_order")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "jpa_order_seq")
    @SequenceGenerator(name = "jpa_order_seq", sequenceName = "lab_jpa_order_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false)
    private String customerName;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    protected Order() {
    }

    public Order(String customerName) {
        this.customerName = customerName;
    }

    public void addItem(Product product, int quantity) {
        OrderItem item = new OrderItem(this, product, quantity);
        orderItems.add(item);
    }

    public Long getId() {
        return id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public List<OrderItem> getOrderItems() {
        return orderItems;
    }
}
