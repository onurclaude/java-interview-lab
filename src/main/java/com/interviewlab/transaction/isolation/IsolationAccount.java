package com.interviewlab.transaction.isolation;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Sadece Hibernate'in {@code ddl-auto=update} aracılığıyla arka plandaki tabloyu oluşturması
 * için var. {@link IsolationAnomalyLab} içindeki her demo metodu bu tabloyla JPA yerine düz
 * {@code JdbcTemplate} SQL'i üzerinden konuşuyor - bu bilinçli bir tercih: JPA'nın birinci
 * seviye önbelleği (first-level cache), bir transaction içinde ikinci bir {@code find()}
 * çağrısında veritabanını tekrar sorgulamadan AYNI yönetilen (managed) örneği döndürürdü;
 * bu da bu laboratuvarın göstermeye çalıştığı izolasyon seviyesi anomalilerini
 * (non-repeatable read, phantom read) sessizce gizlerdi.
 */
@Entity
@Table(name = "lab_isolation_account")
public class IsolationAccount {

    @Id
    private Long id;

    private int balance;

    protected IsolationAccount() {
    }
}
