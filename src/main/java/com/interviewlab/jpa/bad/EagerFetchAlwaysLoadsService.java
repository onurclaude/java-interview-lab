package com.interviewlab.jpa.bad;

import com.interviewlab.jpa.entity.OrderItem;
import com.interviewlab.jpa.repository.OrderItemRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NE YANLIŞ?
 * BU sınıfın kendi kodunda bir yanlışlık yok - buradaki asıl mesele,
 * {@link OrderItem#getProduct()}'ın {@code FetchType.EAGER} olmasının, {@code product}'ın
 * hiç okunmadığı bu örnek dahil, herhangi bir kodun bir {@code OrderItem} yüklediği HER
 * seferinde sessizce ödettiği bedeldir.
 *
 * <p>"HER ŞEYİ EAGER YAP" NEDEN N+1 İÇİN BİR ÇÖZÜM DEĞİLDİR?
 * EAGER "akıllı" anlamına gelmez - "bu entity'nin uygulamadaki her yüklemesinde, her yerde,
 * her zaman, koşulsuz olarak" anlamına gelir. N+1 problemini (istemediğiniz, lazy olarak
 * tetiklenen sorgular) farklı, daha az görünür bir problemle takas eder: ihtiyaç duyulmayan,
 * ama koşulsuz yapılan iş. Yalnızca {@code quantity}'ye ihtiyaç duyan bir metot bile her tek
 * çağrıda bir product join/select'inin bedelini öder.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Sadece order item'lar arasındaki miktarları toplaması gereken bir batch job, hiçbir zaman
 * kullanmadığı product verisini yükleme (ya da join etme) bedelini her çalıştırmada, sonsuza
 * kadar öder - bu, bir N+1'den fark edilmesi çok daha zor bir maliyettir, çünkü dikkat
 * çekecek bir "fazladan sorgu sayısı" yoktur; gereksiz iş, tek bir sorgunun execution plan'ına
 * gömülmüştür.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code NPlusOneTest.shouldAlwaysJoinProductWhenLoadingOrderItemsDueToEagerFetch()},
 * {@link #sumQuantitiesOnly}'yi (hiçbir zaman {@code product}'a dokunmayan) çağırır ve
 * yakalanan SQL'in yine de product tablosuna referans verdiğini gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * {@code @ManyToOne}/{@code @OneToOne} ilişkilerini varsayılan olarak LAZY yap ve yalnızca
 * gerçekten ihtiyaç duyulduğunda sorgu bazında eager yükle (fetch join / {@code @EntityGraph})
 * - bu, N+1'i düzeltmek için kullanılan araçların ters yönde uygulanmasıdır: bunu asla mümkün
 * olmayacak şekilde devre dışı bırakmak yerine, tek bir sorgu için eager yüklemeyi devreye
 * almak (opt-in).
 */
@Service
public class EagerFetchAlwaysLoadsService {

    private final OrderItemRepository orderItemRepository;

    public EagerFetchAlwaysLoadsService(OrderItemRepository orderItemRepository) {
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional(readOnly = true)
    public int sumQuantitiesOnly() {
        List<OrderItem> items = orderItemRepository.findAll(); // product yine de join edilir/yüklenir
        return items.stream().mapToInt(OrderItem::getQuantity).sum();
    }
}
