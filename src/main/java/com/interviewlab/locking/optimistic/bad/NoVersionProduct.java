package com.interviewlab.locking.optimistic.bad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * NE YANLIŞ?
 * Hiç {@code @Version} alanı yok - sadece bir id, bir isim, bir stok sayısı var.
 *
 * <p>NEDEN YANLIŞ?
 * Sade dirty checking, satırın yüklendiğinden bu yana değişip değişmediğinden habersiz bir
 * şekilde, bellekteki GÜNCEL alan değerleri her ne ise onu yazar. Burada hiçbir şey okunan
 * değerle karşılaştırılmaz - bu "ek yapılandırma gerektirmeyen optimistic locking" değildir,
 * bu hiç locking olmamasıdır.
 *
 * <p>Somut lost-update kanıtı için bkz. {@link NoVersionStockService}.
 */
@Entity
@Table(name = "lab_no_version_product")
public class NoVersionProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "no_version_product_seq")
    @SequenceGenerator(name = "no_version_product_seq", sequenceName = "lab_no_version_product_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int stock;

    protected NoVersionProduct() {
    }

    public NoVersionProduct(String name, int stock) {
        this.name = name;
        this.stock = stock;
    }

    public void decreaseStock(int amount) {
        this.stock -= amount;
    }

    public Long getId() {
        return id;
    }

    public int getStock() {
        return stock;
    }
}
