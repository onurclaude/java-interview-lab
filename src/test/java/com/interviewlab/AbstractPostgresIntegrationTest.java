package com.interviewlab;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Tüm amacı Spring wiring yerine gerçek veritabanı davranışı (transaction'lar,
 * kilitleme, izolasyon, N+1) olan her lab için temel sınıf.
 *
 * <p><b>NEDEN H2 yerine gerçek bir Postgres container?</b> H2'nin "PostgreSQL uyumluluk
 * modu" gerçek MVCC'yi uygulamaz, gerçek satır kilitleri almaz ve optimistic
 * locking / izolasyon seviyesi davranışı Postgres ile eşleşmez. Bu projedeki
 * optimistic/pessimistic locking, izolasyon anomalileri ve deadlock'lar *simüle edilmiş
 * değil, gözlemlenen gerçek veritabanı davranışıdır* - bu yüzden testler Testcontainers
 * üzerinden gerçek motora karşı çalışır.
 *
 * <p><b>NEDEN {@code @Testcontainers}/{@code @Container}/{@code @ServiceConnection} yerine
 * {@code @DynamicPropertySource}, ve NEDEN container.start() bir static initializer
 * BLOĞUNDA DEĞİL?</b> İki ayrı gerçek problemin çözümü:
 * <ol>
 *   <li>{@code @Container} + {@code @ServiceConnection}, her test sınıfı için Spring'in test
 *       context cache'ini bir cache-miss'e zorluyordu - JUnit5'in {@code @Testcontainers}
 *       extension'ı her sınıfın {@code beforeAll}'ında container'ı yeniden değerlendiriyordu.
 *       {@code @DynamicPropertySource} ile HER ZAMAN AYNI (stabil) connection değerlerini
 *       kaydetmek, context cache'inin tüm alt sınıfları gerçekten aynı context'i paylaşan
 *       sınıflar olarak tanımasını sağlar.</li>
 *   <li>Container'ı başlatma çağrısını (`.start()`, ağ/Docker I/O yapan bloklayan bir
 *       çağrı) bir <b>static initializer bloğuna</b> koymak tehlikelidir: JLS, bir sınıfın
 *       static initializer'ının, o sınıfı ilk kullanan thread tarafından, sınıfın kendi
 *       class-initialization lock'u ALTINDA çalıştırılmasını garanti eder. JUnit5'in test
 *       discovery mekanizması bu projedeki 28 test sınıfını (hepsi bu sınıfı extend ediyor)
 *       neredeyse eşzamanlı olarak inceler; bu, birden fazla thread'in aynı anda bu sınıfın
 *       class-init lock'unu istemesine ve - Testcontainers'ın kendi iç thread'leri (Ryuk,
 *       log-follow) class-loading ile etkileşime girdiğinde - gerçek bir DEADLOCK'a yol
 *       açabiliyordu (bunu bizzat, tekrarlanabilir şekilde, tam olarak aynı noktada asılı
 *       kalan bir full-suite çalıştırmasıyla gördüm). Çözüm: `.start()` çağrısını normal bir
 *       static METODA taşımak ({@code @DynamicPropertySource}, Spring'in test framework'ü
 *       tarafından normal bir çağrı olarak invoke edilir, class-initialization sırasında
 *       değil) - bu, class-init lock'unu hiç tutmadan aynı bloklayan işi yapar.
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractPostgresIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("interviewlab")
                    .withUsername("interviewlab")
                    .withPassword("interviewlab");

    /**
     * Normal bir static metod olarak - bir static initializer bloğu OLARAK DEĞİL - çağrılır,
     * böylece bloklayan {@code .start()} çağrısı sınıfın class-initialization lock'unu hiç
     * tutmaz. {@code isRunning()} kontrolü, Spring bu metodu her test sınıfı için tekrar
     * çağırdığında (context cache hit olsa bile {@code @DynamicPropertySource} yine de
     * invoke edilir) container'ı yeniden başlatmaya çalışmamak için var - Testcontainers'ın
     * kendi {@code start()}'ı zaten idempotent'tir, ama kontrol niyeti açık kılıyor.
     */
    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
