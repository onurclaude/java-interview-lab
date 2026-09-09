package com.interviewlab.common.config;

import com.interviewlab.common.sql.SqlStatementRecorder;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link SqlStatementRecorder}'ı her laboratuvar için Hibernate'e bağlar, böylece testler
 * bir yoruma güvenmek yerine gönderilen tam SQL üzerinde assertion yapabilir. Bunun neden
 * laboratuvar başına değil de global olarak yapıldığı için {@link SqlStatementRecorder}
 * sınıfının javadoc'una bakın.
 */
@Configuration
public class SqlObservabilityConfig {

    @Bean
    public HibernatePropertiesCustomizer statementInspectorCustomizer() {
        return properties -> properties.put("hibernate.session_factory.statement_inspector", new SqlStatementRecorder());
    }
}
