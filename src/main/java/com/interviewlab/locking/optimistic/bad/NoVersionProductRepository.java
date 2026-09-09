package com.interviewlab.locking.optimistic.bad;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NoVersionProductRepository extends JpaRepository<NoVersionProduct, Long> {
}
