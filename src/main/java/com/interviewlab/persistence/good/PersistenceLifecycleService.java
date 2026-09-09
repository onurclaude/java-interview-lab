package com.interviewlab.persistence.good;

import com.interviewlab.persistence.entity.Customer;
import com.interviewlab.persistence.repository.CustomerRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA entity yaşam döngüsünü (transient -> managed -> detached -> removed), dirty checking'i
 * ve flush zamanlamasını bir diyagram yerine gerçek, gözlemlenebilir davranış kullanarak
 * gösterir.
 *
 * <p>NOT: injection stili hakkında: bu proje her yerde constructor injection kullanır,
 * burası hariç. {@code EntityManager}, alan düzeyinde {@code @PersistenceContext} üzerinden
 * elde edilir, çünkü bu annotation yalnızca Spring'in
 * {@code PersistenceAnnotationBeanPostProcessor}'ı tarafından alanlarda/setter'larda işlenir
 * - desteklenen constructor tabanlı bir eşdeğeri yoktur, bu yüzden bu, bilinçli ve haklı tek
 * istisnadır.
 */
@Service
public class PersistenceLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(PersistenceLifecycleService.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final CustomerRepository customerRepository;

    public PersistenceLifecycleService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /**
     * Answers the interview question: "Aynı transaction içinde bir entity'yi kaydedip,
     * güncelleyip, silebilir miyiz - ve bu 3 ayrı SQL mi üretir?"
     *
     * <p>Sıra: persist -> mutate (dirty checking) -> remove, aralarında HİÇBİR açık flush
     * olmadan. {@link Customer}, {@code GenerationType.SEQUENCE} kullandığından, kimlik
     * (id) {@code save()} sırasında hemen bir {@code nextval(...)} çağrısıyla alınır, ama
     * asıl {@code INSERT} satırı flush'a kadar ertelenir.
     *
     * <p><b>Beklenebilecek şeyin aksine</b> ("Hibernate'in {@code ActionQueue}'su, henüz
     * flush edilmemiş bir INSERT'i, aynı entity hemen sonra remove edilirse iptal eder ve
     * hiçbir SQL göndermez" - birçok kaynakta anlatılan bir optimizasyon), bu proje bunu
     * gerçek Postgres'e karşı test ettiğinde GÖZLEMLENEN davranış farklı çıktı: bu Hibernate
     * 6.5.x + {@code SEQUENCE} kombinasyonunda commit anında hem {@code INSERT} hem
     * {@code DELETE} normal şekilde gönderilir - iptal optimizasyonu burada devreye girmez.
     * Aradaki isim değişikliği yine de hiçbir zaman kalıcı hale gelmez, çünkü entity flush
     * anında zaten {@code REMOVED} durumundadır ve dirty checking {@code REMOVED} entity'leri
     * atlar.
     *
     * <p><b>Net sonuç: bir INSERT, bir DELETE, sıfır UPDATE</b> - "hiçbir zaman flush
     * edilmeyen bir entity asla SQL üretmez" varsayımı burada YANLIŞ çıkıyor; asıl ders,
     * bunun gibi bir davranışı bir yorumdan/varsayımdan değil, gerçek SQL log'undan
     * doğrulamanın önemidir. Doğrulaması:
     * {@code PersistenceLifecycleServiceTest.shouldStillIssueInsertAndDeleteEvenWhenRemovedBeforeFirstFlush()}.
     */
    @Transactional
    public Long persistMutateAndRemoveWithoutFlush(String name, String email) {
        Customer customer = new Customer(name, email);
        log.info("STATE=TRANSIENT id={} (no identifier yet, not in persistence context)", customer.getId());

        customerRepository.save(customer);
        log.info("STATE=MANAGED id={} (in persistence context; INSERT not yet sent - deferred)", customer.getId());

        customer.changeName(name + "-renamed");
        log.info("STATE=MANAGED (dirty) id={} newName={} - still no SQL sent", customer.getId(), customer.getName());

        customerRepository.delete(customer);
        log.info("STATE=REMOVED id={} - scheduled for delete, but nothing was ever inserted", customer.getId());

        return customer.getId();
    }

    /**
     * Aynı başlangıç sırası, ama {@code save()}'den hemen sonra açık bir
     * {@link EntityManager#flush()} ile. Bu, {@code INSERT}'in *şu anda* gerçekten
     * çalıştırılmasını zorlar; böylece satır, değiştirilip silinmeden önce gerçekten
     * MANAGED-ve-DB'de-görünür hale gelir.
     *
     * <p>Sırada olan asıl ince nokta şu: {@code delete()} çağrıldığında, Hibernate
     * entity'nin durumunu {@code REMOVED} olarak işaretler. Transaction commit edildiğinde
     * ve son flush çalıştığında, Hibernate'in dirty-checking aşaması sadece hâlâ
     * {@code MANAGED} olan entity'ler için {@code UPDATE} aksiyonları planlar - bir
     * {@code REMOVED} entity dirty checking tarafından atlanır ve sadece bir
     * {@code DELETE} aksiyonu alır. Yani açık flush'tan sonra yapılan isim değişikliği,
     * bir an için bile olsa **hiçbir zaman veritabanına yazılmaz**.
     *
     * <p>Bu metot için net SQL: bir {@code INSERT} (açık flush'tan) ve bir {@code DELETE}
     * (commit'ten) - asla bir {@code UPDATE} yok. Doğrulaması:
     * {@code PersistenceLifecycleServiceTest.shouldNeverIssueUpdateWhenEntityIsRemovedAfterExplicitFlush()}.
     */
    @Transactional
    public Long persistFlushMutateAndRemove(String name, String email) {
        Customer customer = new Customer(name, email);
        customerRepository.save(customer);

        entityManager.flush();
        log.info("Explicit flush(): INSERT has now really been sent, id={}", customer.getId());

        customer.changeName(name + "-renamed");
        log.info("Mutated MANAGED entity id={} newName={} (dirty, not yet flushed)", customer.getId(), customer.getName());

        customerRepository.delete(customer);
        log.info("STATE=REMOVED id={} - the rename above will never reach the database", customer.getId());

        return customer.getId();
    }

    /**
     * Basit dirty-checking demosu: bir MANAGED entity yükle, bir alanı değiştir, başka
     * hiçbir şey çağırma. {@code save()} yok, {@code flush()} yok. {@code UPDATE} yine de,
     * commit anında gerçekleşir, çünkü Hibernate entity'nin mevcut alan değerlerini
     * entity yüklendiğinde aldığı snapshot ile karşılaştırır ve farkı flush eder.
     */
    @Transactional
    public void renameViaDirtyCheckingOnly(Long customerId, String newName) {
        Customer managed = entityManager.find(Customer.class, customerId);
        log.info("Loaded MANAGED id={} name={}", managed.getId(), managed.getName());

        managed.changeName(newName);
        log.info("Mutated field directly - no save() call at all. UPDATE will still fire at commit.");
        // Metot döner; @Transactional'ın commit'i son flush'ı tetikler -> dirty checking
        // name != snapshot olduğunu bulur -> UPDATE lab_customer SET name=? WHERE id=?
    }
}
