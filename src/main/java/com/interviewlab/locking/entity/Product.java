package com.interviewlab.locking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * {@code @Version}, optimistic locking mekanizmasının tamamıdır: Hibernate, her UPDATE/DELETE
 * işleminin WHERE cümlesine {@code AND version = ?} ekler ve SET cümlesine
 * {@code version = version + 1} dahil eder. Eşleşen satır sayısı sıfırsa (çünkü başka bir
 * transaction version'ı zaten artırmıştır), Hibernate başka birinin daha önce oraya vardığını
 * anlar ve bir istisna fırlatır - bunu anlamak için hiçbir zaman veritabanı kilidi almasına
 * gerek yoktur. Bkz. docs/optimistic-locking.md.
 */
@Entity(name = "LockingProduct") // JPA metamodel'inde com.interviewlab.jpa.entity.Product ile karışmasını önler
@Table(name = "lab_locking_product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "locking_product_seq")
    @SequenceGenerator(name = "locking_product_seq", sequenceName = "lab_locking_product_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int stock;

    @Version
    private Long version;

    protected Product() {
    }

    public Product(String name, int stock) {
        this.name = name;
        this.stock = stock;
    }

    public void decreaseStock(int amount) {
        if (amount > stock) {
            throw new IllegalStateException("insufficient stock");
        }
        this.stock -= amount;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getStock() {
        return stock;
    }

    public Long getVersion() {
        return version;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    /**
     * YALNIZCA {@link com.interviewlab.locking.optimistic.bad.ClientControlledVersionService}
     * içindeki (beklenenden farklı çıkan) senaryoyu göstermek için var - gerçek bir entity,
     * {@code @Version} alanının persistence provider dışından üzerine yazılmasına asla izin
     * vermemelidir; bu metod hâlâ kötü bir pratiği temsil eder, mimari niyeti ihlal eder.
     *
     * <p><b>Ama dikkat:</b> bu alanı elle değiştirmek, aşağıda ele alınan bu tür yönetilen
     * (managed) entity'lerde kalıcı hale gelen değeri DEĞİŞTİRMEZ - bkz.
     * {@link com.interviewlab.locking.optimistic.bad.ClientControlledVersionService}.
     */
    public void dangerouslyOverrideVersion(Long version) {
        this.version = version;
    }
}
