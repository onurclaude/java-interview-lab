-- Bu proje daha önce Hibernate'in ddl-auto=update'ine güveniyordu. Interactive lab modunda
-- (Docker Postgres + IntelliJ'den çalıştırılan Spring Boot uygulaması), şema artık Flyway
-- tarafından yönetiliyor ve application.yml, ddl-auto=validate kullanıyor - yani uygulama
-- açılışında Hibernate şemayı DEĞİŞTİRMEZ, sadece burada tanımlanan şemanın entity
-- mapping'leriyle eşleştiğini doğrular. Testler (AbstractPostgresIntegrationTest, gerçek bir
-- Testcontainers Postgres'ine karşı) de AYNI migration'ları çalıştırır, böylece dev ve test
-- ortamları aynı şemayı paylaşır.
--
-- Aşağıdaki her tablo/sequence, ilgili @Entity sınıfının önceden Hibernate'in ürettiği DDL'in
-- birebir karşılığıdır (bkz. src/main/java/com/interviewlab/**/entity/*.java).

-- com.interviewlab.persistence.entity.Customer
CREATE SEQUENCE lab_customer_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE lab_customer (
    id    BIGINT NOT NULL PRIMARY KEY,
    name  VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL
);

-- com.interviewlab.transaction.entity.Account
CREATE SEQUENCE lab_account_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE lab_account (
    id      BIGINT NOT NULL PRIMARY KEY,
    owner   VARCHAR(255) NOT NULL,
    balance NUMERIC(19, 2) NOT NULL
);

-- com.interviewlab.transaction.entity.AuditLog
CREATE SEQUENCE lab_audit_log_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE lab_audit_log (
    id         BIGINT NOT NULL PRIMARY KEY,
    message    VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- com.interviewlab.transaction.isolation.IsolationAccount (id manuel atanır, sequence yok)
CREATE TABLE lab_isolation_account (
    id      BIGINT NOT NULL PRIMARY KEY,
    balance INTEGER
);

-- com.interviewlab.locking.entity.Product (@Entity(name = "LockingProduct"))
CREATE SEQUENCE lab_locking_product_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE lab_locking_product (
    id      BIGINT NOT NULL PRIMARY KEY,
    name    VARCHAR(255) NOT NULL,
    stock   INTEGER NOT NULL,
    version BIGINT
);

-- com.interviewlab.locking.optimistic.bad.NoVersionProduct
CREATE SEQUENCE lab_no_version_product_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE lab_no_version_product (
    id    BIGINT NOT NULL PRIMARY KEY,
    name  VARCHAR(255) NOT NULL,
    stock INTEGER NOT NULL
);

-- com.interviewlab.jpa.entity.Product
CREATE SEQUENCE lab_jpa_product_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE lab_jpa_product (
    id    BIGINT NOT NULL PRIMARY KEY,
    name  VARCHAR(255) NOT NULL,
    price NUMERIC(19, 2) NOT NULL
);

-- com.interviewlab.jpa.entity.Order
CREATE SEQUENCE lab_jpa_order_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE lab_jpa_order (
    id            BIGINT NOT NULL PRIMARY KEY,
    customer_name VARCHAR(255) NOT NULL
);

-- com.interviewlab.jpa.entity.OrderItem
CREATE SEQUENCE lab_jpa_order_item_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE lab_jpa_order_item (
    id         BIGINT NOT NULL PRIMARY KEY,
    order_id   BIGINT NOT NULL REFERENCES lab_jpa_order (id),
    product_id BIGINT NOT NULL REFERENCES lab_jpa_product (id),
    quantity   INTEGER NOT NULL
);
