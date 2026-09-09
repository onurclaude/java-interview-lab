package com.interviewlab.transaction.propagation.good;

import com.interviewlab.transaction.entity.Account;
import com.interviewlab.transaction.entity.AccountRepository;
import com.interviewlab.transaction.propagation.OrderProcessingException;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ödeme+audit akışının doğru versiyonu:
 *
 * <pre>
 * Order Transaction (REQUIRED)
 *    + debit account         -&gt; sipariş transaction'ının bir parçası, onunla birlikte rollback olur
 *    + auditService.audit()  -&gt; REQUIRES_NEW, gerçek bir proxy çağrısı üzerinden, bağımsız olarak commit edilir
 * </pre>
 *
 * <p>Siparişin başarısız olması (ve debit'i geri alması) audit girdisini geri almaz -
 * tasarım gereği, bir audit trail, deneme başarısız olsa bile "bunu denedik" bilgisini
 * kaydetmelidir.
 *
 * <p><b>Bunun neden tehlikeli de olabileceği:</b> REQUIRES_NEW hemen commit edilir ve
 * çağıranın daha sonraki rollback'i tarafından geri alınamaz. Eğer "audit" yerine gerçek bir
 * yan etkisi olan bir şey olsaydı - örneğin gerçekten bir karttan para çekmek veya
 * idempotent olmayan bir webhook göndermek - dış iş transaction'ının başarılı olup
 * olmayacağını bilmeden önce onu commit etmek tutarsız bir duruma yol açabilir (yan etki
 * gerçekleşti, ama sipariş rollback oldu). REQUIRES_NEW'ı, varsayılan olarak değil, gerçekten
 * dış rollback'ten sağ çıkması gereken şeyler için bilinçli olarak kullanın.
 */
@Service
public class OrderService {

    private final AccountRepository accountRepository;
    private final AuditService auditService;

    public OrderService(AccountRepository accountRepository, AuditService auditService) {
        this.accountRepository = accountRepository;
        this.auditService = auditService;
    }

    @Transactional
    public void processPayment(Long accountId, BigDecimal amount, String auditMessage) {
        Account account = accountRepository.findById(accountId).orElseThrow();
        account.debit(amount);

        auditService.audit(auditMessage); // gerçek proxy çağrısı -> REQUIRES_NEW gerçekten uygulanır

        throw new OrderProcessingException("payment gateway timeout");
    }
}
