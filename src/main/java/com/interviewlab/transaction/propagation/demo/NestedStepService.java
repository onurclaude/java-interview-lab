package com.interviewlab.transaction.propagation.demo;

import com.interviewlab.transaction.entity.Account;
import com.interviewlab.transaction.entity.AccountRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * NESTED, TEORİDE: çağıranın mevcut transaction'ı içinde ayarlanan bir JDBC savepoint
 * içinde çalışır (bkz. {@link OuterOrderWithNestedStepService}). Bu metodun transaction'ı
 * rollback olursa, sadece o savepoint'ten sonraki iş geri alınır - dış transaction
 * rollback-only olarak işaretlenMEZ ve kendi ayrı değişikliklerini commit etmeye devam
 * edebilir. REQUIRES_NEW'dan temel farkı budur: NESTED aynı fiziksel veritabanı
 * transaction'ını ve bağlantısını paylaşır, sadece içine bir geri alma noktası ekler.
 *
 * <p><b>PRATİKTE: bu metodun gövdesi ASLA çalışmaz.</b> Bu projenin
 * {@code PropagationShowcaseTest}'i gerçek Postgres'e karşı doğruladığında, aşağıdaki
 * {@code account.debit(...)} satırına hiçbir zaman ulaşılmadığını ortaya çıkardı - proxy,
 * transaction başlamadan ÖNCE, {@code AbstractPlatformTransactionManager.handleExistingTransaction()}
 * içinde bir savepoint oluşturmaya çalışırken başarısız olur. Kök neden Spring'in kendi
 * {@code HibernateJpaDialect}'inde: {@code beginTransaction()}'ın döndürdüğü transaction
 * data nesnesi ({@code HibernateJpaDialect.SessionTransactionData}) {@code SavepointManager}
 * arayüzünü UYGULAMAZ - bu yüzden {@code EntityManagerHolder}'ın savepoint manager'ı hiçbir
 * zaman ayarlanmaz ve her NESTED denemesi
 * {@code NestedTransactionNotSupportedException: "JpaDialect does not support savepoints"}
 * ile başarısız olur - {@code nestedTransactionAllowed=true} doğru ayarlanmış olsa bile (bkz.
 * {@link NestedTransactionManagerConfig}). Bu, Spring'in kendi 6.1.x bytecode'unda
 * doğrulanmıştır (javap ile decompile edilerek) - bir varsayım değil.
 *
 * <p><b>Sonuç:</b> {@code Propagation.NESTED}, düz JPA/Hibernate ile pratikte KULLANILAMAZ.
 * Gerçek savepoint desteği için ya saf JDBC tabanlı bir {@code DataSourceTransactionManager}
 * (Spring'in {@code ConnectionHolder}'ı gerçekten {@code SavepointManager} uygular) ya da
 * savepoint'leri açıkça destekleyen özel bir {@code JpaDialect} gerekir - hiçbiri bu
 * projenin (ya da çoğu tipik Spring Data JPA uygulamasının) kapsamında değildir.
 */
@Service
public class NestedStepService {

    private final AccountRepository accountRepository;

    public NestedStepService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(propagation = Propagation.NESTED)
    public void attemptOverdraft(Long accountId) {
        Account account = accountRepository.findById(accountId).orElseThrow();
        account.debit(new BigDecimal("100000.00"));
        throw new IllegalStateException("iş kuralı: bu debit'e izin verilmiyor - sadece bu savepoint'i geri al");
    }
}
