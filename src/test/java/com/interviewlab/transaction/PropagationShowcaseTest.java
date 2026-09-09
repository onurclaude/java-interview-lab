package com.interviewlab.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.interviewlab.AbstractPostgresIntegrationTest;
import com.interviewlab.transaction.entity.Account;
import com.interviewlab.transaction.entity.AccountRepository;
import com.interviewlab.transaction.propagation.demo.OuterOrderWithNestedStepService;
import com.interviewlab.transaction.propagation.demo.PropagationShowcaseService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Tam karşılaştırma tablosu ve mülakat cevapları için docs/propagation.md dosyasına bakın. */
class PropagationShowcaseTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private PropagationShowcaseService propagationShowcaseService;

    @Autowired
    private OuterOrderWithNestedStepService outerOrderWithNestedStepService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void supportsShouldRunWithoutTransactionWhenCalledWithNone() {
        boolean active = propagationShowcaseService.isTransactionActiveUnderSupports();
        assertThat(active).isFalse();
    }

    @Test
    void supportsShouldJoinTransactionWhenOneIsActive() {
        boolean active = new TransactionTemplate(transactionManager)
                .execute(status -> propagationShowcaseService.isTransactionActiveUnderSupports());
        assertThat(active).isTrue();
    }

    @Test
    void mandatoryShouldThrowWhenCalledWithNoTransaction() {
        assertThatThrownBy(() -> propagationShowcaseService.isTransactionActiveUnderMandatory())
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void mandatoryShouldSucceedWhenTransactionIsActive() {
        boolean active = new TransactionTemplate(transactionManager)
                .execute(status -> propagationShowcaseService.isTransactionActiveUnderMandatory());
        assertThat(active).isTrue();
    }

    @Test
    void notSupportedShouldSuspendAnActiveTransaction() {
        boolean activeInsideNotSupported = new TransactionTemplate(transactionManager)
                .execute(status -> propagationShowcaseService.isTransactionActiveUnderNotSupported());
        assertThat(activeInsideNotSupported)
                .as("NOT_SUPPORTED, çağıranın transaction'ını askıya almalı ve transaction olmadan çalışmalı")
                .isFalse();
    }

    @Test
    void neverShouldThrowWhenCalledWithAnActiveTransaction() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                        .execute(status -> propagationShowcaseService.isTransactionActiveUnderNever()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void neverShouldSucceedWhenCalledWithNoTransaction() {
        boolean active = propagationShowcaseService.isTransactionActiveUnderNever();
        assertThat(active).isFalse();
    }

    @Test
    void shouldNeverExecuteNestedStepBodyBecauseHibernateJpaDialectHasNoSavepointSupport() {
        Account account = accountRepository.save(new Account("Nested", new BigDecimal("1000.00")));

        outerOrderWithNestedStepService.runOuterWithFailingNestedStep(account.getId());

        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getBalance())
                .as("outer'ın iki credit işlemi (+50, +10) hayatta kaldı; nested adımın -100000 debit'i hiçbir "
                        + "zaman uygulanmadı çünkü NESTED, düz JPA/Hibernate ile hiç çalışmıyor - metodun "
                        + "gövdesi hiç çalışmadan NestedTransactionNotSupportedException fırlatılıyor")
                .isEqualByComparingTo("1060.00");
    }
}
