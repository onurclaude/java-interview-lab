package com.interviewlab.locking.entity;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface LockingProductRepository extends JpaRepository<Product, Long> {

    /**
     * {@code PESSIMISTIC_WRITE}, PostgreSQL'de gerçek bir {@code SELECT ... FOR UPDATE}'e
     * karşılık gelir - bkz. docs/pessimistic-locking.md. Aynı şeyi Spring Data'ya özgü
     * şekilde talep etmeyi göstermek için burada
     * ({@code entityManager.find(..., LockModeType.PESSIMISTIC_WRITE)} yerine) bu şekilde
     * tanımlanmıştır.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from LockingProduct p where p.id = :id")
    Optional<Product> findByIdForUpdate(Long id);
}
