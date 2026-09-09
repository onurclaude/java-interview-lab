package com.interviewlab.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.interviewlab.AbstractPostgresIntegrationTest;
import com.interviewlab.transaction.entity.Account;
import com.interviewlab.transaction.entity.AccountRepository;
import com.interviewlab.transaction.entity.AuditLogRepository;
import com.interviewlab.transaction.propagation.OrderProcessingException;
import com.interviewlab.transaction.propagation.bad.SelfInvocationPaymentService;
import com.interviewlab.transaction.propagation.good.OrderService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Yazı için docs/propagation.md dosyasına bakın. */
class PropagationSelfInvocationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private SelfInvocationPaymentService selfInvocationPaymentService;

    @Autowired
    private OrderService orderService;

    @Test
    void shouldNotApplyRequiresNewDuringSelfInvocation() {
        Account account = accountRepository.save(new Account("SelfInvocation", new BigDecimal("500.00")));
        String auditMessage = "self-invocation-" + account.getId();

        assertThatThrownBy(() ->
                        selfInvocationPaymentService.processPayment(account.getId(), new BigDecimal("50.00"), auditMessage))
                .isInstanceOf(OrderProcessingException.class);

        assertThat(auditLogRepository.findByMessage(auditMessage))
                .as("REQUIRES_NEW hiçbir zaman devreye girmedi: audit(), processPayment() ile aynı transaction "
                        + "içinde düz bir metot çağrısı olarak çalıştı, bu yüzden onunla birlikte geri alındı")
                .isEmpty();

        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getBalance()).isEqualByComparingTo("500.00");
    }

    @Test
    void shouldCommitRequiresNewTransactionEvenWhenOuterTransactionRollsBack() {
        Account account = accountRepository.save(new Account("SeparateBean", new BigDecimal("500.00")));
        String auditMessage = "separate-bean-" + account.getId();

        assertThatThrownBy(() ->
                        orderService.processPayment(account.getId(), new BigDecimal("50.00"), auditMessage))
                .isInstanceOf(OrderProcessingException.class);

        assertThat(auditLogRepository.findByMessage(auditMessage))
                .as("audit(), gerçek Spring proxy'si (ayrı bir bean) üzerinden çağrıldı, bu yüzden REQUIRES_NEW "
                        + "rollback'ten önce zaten commit olan bağımsız bir transaction açtı")
                .hasSize(1);

        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getBalance())
                .as("dış transaction'ın debit işlemi yine de geri alınır")
                .isEqualByComparingTo("500.00");
    }
}
