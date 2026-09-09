package com.interviewlab.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewlab.AbstractPostgresIntegrationTest;
import com.interviewlab.common.sql.SqlStatementRecorder;
import com.interviewlab.persistence.bad.MutatingDetachedEntityService;
import com.interviewlab.persistence.bad.SaveAndFlushInLoopService;
import com.interviewlab.persistence.entity.Customer;
import com.interviewlab.persistence.good.BatchPersistenceService;
import com.interviewlab.persistence.good.PersistenceLifecycleService;
import com.interviewlab.persistence.repository.CustomerRepository;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Optional;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Hibernate'in persistence context'inin, dirty checking'in ve flush zamanlamasının gerçekte
 * nasıl davrandığını - bir varsayımla değil, gerçek bir Postgres container'ından yakalanan
 * gerçek SQL ile - kanıtlar. Yazı ve mülakat cevabı için docs/persistence-context.md dosyasına bakın.
 */
class PersistenceLifecycleServiceTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private PersistenceLifecycleService persistenceLifecycleService;

    @Autowired
    private MutatingDetachedEntityService mutatingDetachedEntityService;

    @Autowired
    private SaveAndFlushInLoopService saveAndFlushInLoopService;

    @Autowired
    private BatchPersistenceService batchPersistenceService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void clearRecordedSql() {
        SqlStatementRecorder.clear();
    }

    @Test
    void shouldStillIssueInsertAndDeleteEvenWhenRemovedBeforeFirstFlush() {
        Long id = persistenceLifecycleService.persistMutateAndRemoveWithoutFlush("Ada", "ada@example.com");

        // Beklenebilecek şeyin aksine (Hibernate'in ActionQueue'sunun bekleyen bir INSERT'i,
        // henüz flush edilmeden aynı entity delete edilirse iptal edeceği), gerçekte
        // GÖZLEMLENEN davranış bu değil: bu Hibernate 6.5.x + GenerationType.SEQUENCE
        // kombinasyonunda hem INSERT hem DELETE commit anında normal şekilde gönderilir.
        List<String> statements = SqlStatementRecorder.statementsForCurrentThread();
        assertThat(statements)
                .as("INSERT, save() sırasında sıraya alınır ve commit'te normal şekilde gönderilir")
                .filteredOn(sql -> containsIgnoreCase(sql, "insert into lab_customer"))
                .hasSize(1);
        assertThat(statements)
                .as("DELETE de commit'te normal şekilde gönderilir - ActionQueue insert+delete çiftini iptal etmez")
                .filteredOn(sql -> containsIgnoreCase(sql, "delete from lab_customer"))
                .hasSize(1);
        assertThat(statements)
                .as("ara sıradaki isim değişikliği hiçbir zaman kalıcı hale gelmemeli - dirty checking REMOVED entity'leri atlar")
                .noneMatch(sql -> containsIgnoreCase(sql, "update lab_customer"));

        assertThat(customerRepository.findById(id)).isEmpty();
    }

    @Test
    void shouldNeverIssueUpdateWhenEntityIsRemovedAfterExplicitFlush() {
        persistenceLifecycleService.persistFlushMutateAndRemove("Grace", "grace@example.com");

        List<String> statements = SqlStatementRecorder.statementsForCurrentThread();
        long inserts = statements.stream().filter(sql -> containsIgnoreCase(sql, "insert into lab_customer")).count();
        long updates = statements.stream().filter(sql -> containsIgnoreCase(sql, "update lab_customer")).count();
        long deletes = statements.stream().filter(sql -> containsIgnoreCase(sql, "delete from lab_customer")).count();

        assertThat(inserts).as("açık flush() çağrısı tam olarak bir gerçek INSERT'e zorlamalı").isEqualTo(1);
        assertThat(updates).as("dirty checking, REMOVED durumdaki bir entity için UPDATE ÜRETMEMELİ").isZero();
        assertThat(deletes).as("commit yine de DELETE ifadesini yayınlamalı").isEqualTo(1);
    }

    @Test
    void shouldPersistRenameViaDirtyCheckingWithoutExplicitSave() {
        Customer saved = customerRepository.save(new Customer("Linus", "linus@example.com"));
        SqlStatementRecorder.clear();

        persistenceLifecycleService.renameViaDirtyCheckingOnly(saved.getId(), "Linus Torvalds");

        List<String> statements = SqlStatementRecorder.statementsForCurrentThread();
        assertThat(statements).anyMatch(sql -> containsIgnoreCase(sql, "update lab_customer"));

        Optional<Customer> reloaded = customerRepository.findById(saved.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getName()).isEqualTo("Linus Torvalds");
    }

    @Test
    void shouldSilentlyDropChangeWhenMutatingDetachedEntity() {
        Long id = mutatingDetachedEntityService.renameCustomerAssumingDirtyChecking(
                "Margaret", "margaret@example.com", "Margaret Hamilton");

        Optional<Customer> reloaded = customerRepository.findById(id);
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getName())
                .as("DETACHED bir entity üzerindeki yeniden adlandırma sessizce kaybolmalı - dirty checking uygulanmaz")
                .isEqualTo("Margaret");
    }

    @Test
    void shouldFlushOnceForBatchButNTimesForSaveAndFlushLoop() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        saveAndFlushInLoopService.saveAllWithFlushPerIteration(List.of(
                new Customer("A", "a@example.com"),
                new Customer("B", "b@example.com"),
                new Customer("C", "c@example.com")));
        // 3 açık saveAndFlush() + transaction commit anındaki 1 örtük flush (FlushMode.AUTO
        // altında commit her zaman flush() ÇAĞIRIR - persistence context'te flush edilecek bir
        // şey kalmasa bile; flushCount, SQL üreten flush'ları değil flush() ÇAĞRILARINI sayar).
        assertThat(statistics.getFlushCount())
                .as("her iterasyondaki saveAndFlush() + commit anındaki örtük flush")
                .isEqualTo(4);

        statistics.clear();
        batchPersistenceService.saveAllInOneFlush(List.of(
                new Customer("D", "d@example.com"),
                new Customer("E", "e@example.com"),
                new Customer("F", "f@example.com")));
        assertThat(statistics.getFlushCount())
                .as("flush'ı commit'in tetiklemesine izin vermek, tüm batch için tam olarak bir kez flush yapmalı")
                .isEqualTo(1);
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase().contains(needle.toLowerCase());
    }
}
