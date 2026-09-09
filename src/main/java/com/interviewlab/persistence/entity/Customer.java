package com.interviewlab.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Bilinçli olarak {@code IDENTITY} değil, {@code GenerationType.SEQUENCE} kullanır.
 *
 * <p><b>Üretim stratejisi burada NEDEN önemli?</b> {@code IDENTITY} ile, Hibernate
 * {@code INSERT}'i {@link jakarta.persistence.EntityManager#persist} içinde hemen çalıştırmak
 * zorundadır, çünkü veritabanı tarafından üretilen id'yi elde etmenin tek yolu budur - ertelenemez.
 * {@code SEQUENCE} ile, Hibernate id'yi önceden (veya toplu olarak) sequence'ten çekebilir ve
 * gerçek {@code INSERT}'i flush anına kadar *erteleyebilir*; bu da
 * {@link com.interviewlab.persistence.good.PersistenceLifecycleService} içinde gösterilen
 * "hiç gerçekleşmeyen insert" optimizasyonuna olanak tanıyan şeydir. Bu tek annotation
 * seçimi, "save/update/delete her zaman 3 ifade üretir" ifadesinin doğru mu yanlış mı
 * olduğunu belirler - bkz. docs/persistence-context.md.
 */
@Entity
@Table(name = "lab_customer")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_seq")
    @SequenceGenerator(name = "customer_seq", sequenceName = "lab_customer_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    protected Customer() {
        // JPA, entity'leri reflection yoluyla oluşturabilmek için parametresiz bir constructor gerektirir.
    }

    public Customer(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public void changeName(String newName) {
        this.name = newName;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }
}
