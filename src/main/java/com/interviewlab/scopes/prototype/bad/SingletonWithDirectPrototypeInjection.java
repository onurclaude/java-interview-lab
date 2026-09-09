package com.interviewlab.scopes.prototype.bad;

import com.interviewlab.scopes.prototype.PrototypeWorker;
import org.springframework.stereotype.Service;

/**
 * NE YANLIŞ?
 * Bir singleton bean üzerinde, sıradan constructor injection ile alınmış bir
 * {@link PrototypeWorker} alanı.
 *
 * <p>NEDEN YANLIŞ?
 * Dependency injection tam olarak BİR KEZ gerçekleşir, singleton bean'in kendisi
 * oluşturulduğunda: Spring container'dan bir {@code PrototypeWorker} ister, taze bir instance
 * alır (bu gerçekten "prototype" davranışıdır - yeni bir nesne OLUŞTURULMUŞTUR) ve bunu
 * constructor'a verir. Bu noktadan sonra bu singleton, o tek nesneye sonsuza dek işaret eden
 * sıradan bir Java alan referansı tutmaktan başka bir şey yapmaz. "Prototype scope",
 * container'dan bir bean İSTENDİĞİNDE ne olacağını tanımlar - elinizde zaten bir referans
 * varken ne olacağı hakkında hiçbir şey söylemez, bu yüzden burada hiçbir şey container'a
 * tekrar sormaz.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Birisi, kullanım başına taze, paylaşılmayan bir instance'a ihtiyacı olduğu için (örn. stateful
 * bir builder, görev başına bir accumulator) özellikle prototype scope seçer, ama bunu bir
 * singleton'a "normal" şekilde inject eder - ve tam olarak kaçınmaya çalıştığı
 * paylaşılan-tek-instance davranışını, sessizce elde eder.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code BeanScopesTest.shouldReusePrototypeBeanWhenInjectedDirectlyIntoSingleton()},
 * {@link #getWorkerId()} metodunu iki kez çağırır ve her ikisinde de AYNI id'yi gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * Referansı önbelleğe almak yerine her kullanımda container'dan yeni bir instance iste - bkz.
 * {@link com.interviewlab.scopes.prototype.good.SingletonWithObjectProvider} (açık/explicit) ya da
 * {@link com.interviewlab.scopes.prototype.good.SingletonWithScopedProxy} (şeffaf, scoped
 * proxy üzerinden).
 */
@Service
public class SingletonWithDirectPrototypeInjection {

    private final PrototypeWorker worker;

    public SingletonWithDirectPrototypeInjection(PrototypeWorker worker) {
        this.worker = worker; // tam olarak bir kez, construction anında inject edilir
    }

    public String getWorkerId() {
        return worker.getId(); // her zaman aynı instance, sonsuza dek
    }
}
