package com.interviewlab.persistence.bad;

import com.interviewlab.persistence.entity.Customer;
import com.interviewlab.persistence.repository.CustomerRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NE YANLIŞ?
 * Bir döngü içinde her tek bir entity için {@code saveAndFlush()} çağırmak.
 *
 * <p>NEDEN YANLIŞ?
 * {@code flush()}, Hibernate'i persistence context'i veritabanıyla <em>şu anda</em>,
 * özel bir round trip olarak senkronize etmeye zorlar; bunun yerine her şeyi toplu hale
 * getirip tek bir commit-anı flush'ının göndermesine izin vermek yerine. Bunu her döngü
 * iterasyonunda bir kez yapmak, tek bir flush olabilecek şeyi (isteğe bağlı olarak
 * {@code hibernate.jdbc.batch_size} ile JDBC-batch'lenmiş) N ayrı flush / round trip'e
 * dönüştürür. Ayrıca her seferinde sadece kaydetmek istediğiniz tek entity'yi değil,
 * *tüm* dirty persistence context'i flush eder; bu da döngü sırasında persistence context
 * büyüdükçe daha da maliyetli hale gelir.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * 10.000 satırı güncelleyen bir batch işi, birkaç toplu (batched) round trip yerine
 * veritabanına 10.000 ağ round trip'i yapar - bu, batch pencereleri sırasında artan
 * gecikme ve DB CPU'su olarak, ve Hibernate istatistiklerindeki `flushCount`'un satır
 * sayısıyla bire bir eşleşmesi olarak doğrudan görülür.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code PersistenceLifecycleServiceTest.shouldFlushOnceForBatchButNTimesForSaveAndFlushLoop()}'a
 * bakın; bu test her iki versiyondan önce/sonra
 * {@code SessionFactory.getStatistics().getFlushCount()}'u okur.
 *
 * <p>NASIL DÜZELTİLİR?
 * Tüm entity'leri tek bir {@code @Transactional} metodu içinde değiştirin/persist edin ve
 * tek bir commit-anı flush'ının bunu halletmesine izin verin (bkz.
 * {@link com.interviewlab.persistence.good.BatchPersistenceService}). Manuel
 * {@code flush()}'a sadece SQL hatasını *şu anda* görmek için gerçek bir nedeniniz varsa
 * başvurun (örneğin bekleyen değişikliklere bağlı bir native query çalıştırmadan önce) -
 * her {@code save()}'den sonra alışkanlık olarak değil.
 */
@Service
public class SaveAndFlushInLoopService {

    private static final Logger log = LoggerFactory.getLogger(SaveAndFlushInLoopService.class);

    private final CustomerRepository customerRepository;

    public SaveAndFlushInLoopService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public void saveAllWithFlushPerIteration(List<Customer> customers) {
        for (Customer customer : customers) {
            customerRepository.saveAndFlush(customer);
            log.warn("Flushed immediately for customer email={} - one extra round trip per row", customer.getEmail()); // her satır için bir ekstra round trip
        }
    }
}
