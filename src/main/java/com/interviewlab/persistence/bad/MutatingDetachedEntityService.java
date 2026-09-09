package com.interviewlab.persistence.bad;

import com.interviewlab.persistence.entity.Customer;
import com.interviewlab.persistence.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * NE YANLIŞ?
 * {@link #renameCustomerAssumingDirtyChecking} üzerinde {@code @Transactional} yok ve metot,
 * entity'yi {@code repository.save(...)} çağrısı zaten döndükten <em>sonra</em>
 * değiştiriyor.
 *
 * <p>NEDEN YANLIŞ?
 * {@code JpaRepository.save()}'in kendisi {@code @Transactional}'dır (Spring Data, çağıranın
 * hiç transaction'ı olmadığında her repository metodunu kendi transaction'ına sarar). O
 * transaction - ve içinde yaşayan persistence context - tamamen {@code save()} çağrısının
 * içinde açılıp kapatılır. {@code save()} bu metoda döndüğünde, {@link Customer} örneği
 * DETACHED'dır: Java alan değerlerini hâlâ taşır, ama Hibernate artık onu takip etmiyordur,
 * bu yüzden karşılaştırılacak bir snapshot ve flush edilecek bir persistence context yoktur.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Bir geliştirici bir metotta {@code save(); entity.setX(...);} yazar, exception görmez ve
 * değişikliğin kalıcı hale geldiğini varsayar - çünkü kod tabanında başka bir yerdeki farklı
 * bir metotta, kapsayan bir {@code @Transactional} entity'yi yönetilen (managed) tutmayı
 * başarmıştır ve orada "işe yaramıştır". Bu, tam olarak sessizce yayına çıkan ve ancak biri
 * veritabanını sorgulayıp alanın bayat olduğunu görene kadar fark edilmeyen türden bir hata
 * sınıfıdır.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code PersistenceLifecycleServiceTest.shouldSilentlyDropChangeWhenMutatingDetachedEntity()}'a
 * bakın: bu metodu çağırın, ardından müşteriyi yeni bir transaction içinde veritabanından
 * yeniden yükleyin ve ismin hâlâ ESKİ değer olduğunu doğrulayın.
 *
 * <p>NASIL DÜZELTİLİR?
 * Tüm oku-değiştir-yaz sırasını tek bir {@code @Transactional} metodunun içine sarın (bkz.
 * {@link com.interviewlab.persistence.good.PersistenceLifecycleService#renameViaDirtyCheckingOnly}),
 * ya da gerçekten iki ayrı transaction'a ihtiyacınız varsa, değiştirdikten sonra
 * {@code save()}'i açıkça tekrar çağırın (bu, {@code merge()} benzeri bir yeniden bağlanma +
 * update işlemi yapar).
 */
@Service
public class MutatingDetachedEntityService {

    private static final Logger log = LoggerFactory.getLogger(MutatingDetachedEntityService.class);

    private final CustomerRepository customerRepository;

    public MutatingDetachedEntityService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /** Burada bilinçli olarak {@code @Transactional} yok - hata da bu zaten. */
    public Long renameCustomerAssumingDirtyChecking(String name, String email, String newName) {
        Customer customer = new Customer(name, email);
        customer = customerRepository.save(customer);
        log.warn("save() already returned - the persistence context is CLOSED. customer is now DETACHED.");

        customer.changeName(newName);
        log.warn("Mutated a DETACHED entity id={}. No dirty checking will ever see this change.", customer.getId());

        // İkinci bir save() çağrısı yok: isim değişikliği sessizce kaybolur.
        return customer.getId();
    }
}
