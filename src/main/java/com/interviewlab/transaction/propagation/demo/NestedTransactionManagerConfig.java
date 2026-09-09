package com.interviewlab.transaction.propagation.demo;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Boot bir {@link JpaTransactionManager}'ı otomatik olarak yapılandırır, ancak
 * varsayılan olarak {@code nestedTransactionAllowed=false} ile. Bu açılmadığı sürece
 * {@code Propagation.NESTED} her zaman {@code NestedTransactionNotSupportedException:
 * "Transaction manager does not allow nested transactions by default"} ile başarısız olur.
 *
 * <p><b>ÖNEMLİ (ve bu projenin kendi testleriyle keşfettiği şey):</b> bu flag'i {@code true}
 * yapmak GEREKLİDİR ama YETERLİ DEĞİLDİR. Flag açık olsa bile, düz JPA/Hibernate ile NESTED
 * hâlâ HER ZAMAN farklı bir {@code NestedTransactionNotSupportedException} ile başarısız
 * olur: {@code "JpaDialect does not support savepoints"} - bkz.
 * {@link com.interviewlab.transaction.propagation.demo.NestedStepService} javadoc'u, bunun
 * neden derin ve kaçınılmaz bir sınırlama olduğunun tam açıklaması için. Bu bean'i yine de
 * burada tutuyoruz çünkü flag kapalıyken alınan hatanın MESAJI farklıdır (yukarıdaki) - iki
 * ayrı başarısızlık modu var, ve bu proje ikisini de doğru şekilde ayırt ediyor.
 */
@Configuration
public class NestedTransactionManagerConfig {

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        JpaTransactionManager transactionManager = new JpaTransactionManager(entityManagerFactory);
        transactionManager.setNestedTransactionAllowed(true);
        return transactionManager;
    }
}
