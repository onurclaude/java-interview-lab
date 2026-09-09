package com.interviewlab.locking.optimistic.bad;

import com.interviewlab.locking.entity.Product;
import com.interviewlab.locking.entity.LockingProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NE YANLIŞ (VE ŞAŞIRTICI ŞEKİLDE, GERÇEKTE NE OLUYOR)?
 * {@link #updateStockTrustingClientSuppliedVersion}, ÇAĞIRANDAN (bu, "HTTP request body'sinden"
 * gelmesinin yerini tutuyor) bir version numarası alır ve güncellemeden önce bunu doğrudan
 * yönetilen (managed) entity'ye yazar - {@code @Version} yalnızca persistence provider
 * tarafından yönetilmesi gereken bir alandır, bir client'ın asla etkileyebilmemesi gereken
 * tek alandır, bu yüzden bu hâlâ gerçek bir kod kokusu ve mimari ihlaldir.
 *
 * <p><b>Ama {@code OptimisticLockingTest.shouldIgnoreClientSuppliedVersionAndAlwaysPersistHibernatesOwnIncrement()} testinin
 * kanıtladığı gibi, bu SATIRA gerçekte HİÇBİR ETKİSİ YOKTUR.</b> Hibernate, versiyonlu bir
 * UPDATE üretirken entity'nin bellekteki {@code version} alanına güvenmez - yüklenen
 * (snapshot) değeri kendi içinde takip eder ve SET cümlesine her zaman kendi ürettiği
 * {@code version = version + 1} ifadesini yazar (bkz. {@link com.interviewlab.locking.entity.Product}
 * javadoc'u). Yani {@code dangerouslyOverrideVersion(999L)} çağrısı entity'nin Java alanını
 * 999 yapar, ama flush anında veritabanına giden gerçek SQL yine de yüklenen version'dan
 * (0) bir artırılmış (1) değeri yazar - client'ın gönderdiği 999 sessizce göz ardı edilir.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Standart, dirty-checking ile yönetilen bir entity için - yukarıdaki gibi - gerçek bir
 * güvenlik açığı YOKTUR; Hibernate'in kendi version-üretim stratejisi burada sizi zaten
 * korur. Asıl risk farklı bir yerdedir: bir entity'yi {@code entityManager.merge()} ile
 * DETACHED/geçici (client'tan gelen, id+version dolu) bir instance olarak birleştirirseniz,
 * client'ın sağladığı version değeri merge'ün optimistic-check'inde KULLANILIR (mevcut satırın
 * version'ıyla karşılaştırılır) - bu senaryo bu projenin kapsamı dışındadır ama gerçek risk
 * oradadır, burada değil.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code OptimisticLockingTest.shouldIgnoreClientSuppliedVersionAndAlwaysPersistHibernatesOwnIncrement()}, keyfi bir version
 * numarasıyla çağırır ve kalıcı hale gelen satırın version'ının client'ın gönderdiği 999
 * DEĞİL, Hibernate'in ürettiği 1 (yüklenen 0 + 1) olduğunu kanıtlar.
 *
 * <p>NASIL DÜZELTİLİR?
 * Yine de {@code version}'ı asla client girdisinden bağlamayın (bind) - bu koddaki niyet
 * hâlâ yanlıştır, sadece BU spesifik ORM mapping'inde etkisiz kalır. Entity'yi yükleyin,
 * yalnızca client'ın gerçekten değiştirmesine izin verilen alanları değiştirin ve gerisini
 * dirty checking'e bırakın (bkz. {@link com.interviewlab.locking.optimistic.good.OptimisticStockService}).
 */
@Service
public class ClientControlledVersionService {

    private final LockingProductRepository productRepository;

    public ClientControlledVersionService(LockingProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public void updateStockTrustingClientSuppliedVersion(Long productId, int newStock, long clientSuppliedVersion) {
        Product product = productRepository.findById(productId).orElseThrow();
        product.dangerouslyOverrideVersion(clientSuppliedVersion); // provider'a ait bir alan için client girdisine güveniyor
        product.setStock(newStock);
    }
}
