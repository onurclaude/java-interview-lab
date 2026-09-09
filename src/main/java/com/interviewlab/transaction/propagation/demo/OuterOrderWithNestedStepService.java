package com.interviewlab.transaction.propagation.demo;

import com.interviewlab.transaction.entity.Account;
import com.interviewlab.transaction.entity.AccountRepository;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.NestedTransactionNotSupportedException;
import org.springframework.transaction.annotation.Transactional;

/**
 * İki meşru credit işlemi yapan ve aralarında bir NESTED adım deneyen dış REQUIRED
 * transaction. REQUIRES_NEW ile karşılaştırın
 * ({@link com.interviewlab.transaction.propagation.good.OrderService}): NESTED, TEORİDE, dış
 * transaction ile aynı fiziksel bağlantıyı/session'ı paylaşır (sadece bir savepoint ekler),
 * REQUIRES_NEW ise dış transaction'ı ve onun persistence context'ini tamamen askıya alır ve
 * yepyeni biri üzerinde çalışır.
 *
 * <p><b>Bu sınıfın gerçekte öğrettiği şey (başlangıçta tasarlandığından farklı):</b> bu proje
 * başlangıçta burada "nested rollback sonrası entity'yi {@code EntityManager#refresh}"
 * tuzağını göstermeyi planlamıştı. Ama gerçek Postgres'e karşı test edildiğinde, ortaya daha
 * temel bir gerçek çıktı: {@link NestedStepService#attemptOverdraft}'ın gövdesi hiçbir zaman
 * çalışmıyor - {@code nestedStepService.attemptOverdraft(accountId)} çağrısının kendisi,
 * transaction proxy'si bir savepoint oluşturmaya çalışırken
 * {@link NestedTransactionNotSupportedException} ile başarısız oluyor (bkz.
 * {@code NestedStepService} javadoc'u, kök nedenin tam açıklaması için). Yani burada
 * "entity'yi refresh et" diye bir şey yok - refresh edilecek hayalet bir değişiklik hiç
 * oluşmuyor, çünkü debit'in kendisi hiç çalışmıyor.
 */
@Service
public class OuterOrderWithNestedStepService {

    private static final Logger log = LoggerFactory.getLogger(OuterOrderWithNestedStepService.class);

    private final AccountRepository accountRepository;
    private final NestedStepService nestedStepService;

    public OuterOrderWithNestedStepService(AccountRepository accountRepository, NestedStepService nestedStepService) {
        this.accountRepository = accountRepository;
        this.nestedStepService = nestedStepService;
    }

    @Transactional
    public void runOuterWithFailingNestedStep(Long accountId) {
        Account account = accountRepository.findById(accountId).orElseThrow();
        account.credit(new BigDecimal("50.00"));

        try {
            nestedStepService.attemptOverdraft(accountId);
        } catch (NestedTransactionNotSupportedException ex) {
            log.info("NESTED, düz JPA/Hibernate ile hiçbir zaman çalışmaz - nested adımın gövdesi hiç "
                    + "çalışmadı, bu yüzden geri alınacak bir şey de yoktu: {}", ex.getMessage());
        }

        account.credit(new BigDecimal("10.00"));
        // Dış transaction burada normal şekilde commit edilir: net etki +50 ve +10'dur -
        // nested adımın planladığı -100000 debit'in gövdesi hiçbir zaman çalışmadığı için
        // zaten hiç uygulanmamıştı.
    }
}
