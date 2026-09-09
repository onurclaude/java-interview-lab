package com.interviewlab.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.interviewlab.AbstractPostgresIntegrationTest;
import com.interviewlab.transaction.entity.Account;
import com.interviewlab.transaction.entity.AccountRepository;
import com.interviewlab.transaction.rollback.SimulatedCheckedFailureException;
import com.interviewlab.transaction.rollback.SimulatedUncheckedFailureException;
import com.interviewlab.transaction.rollback.bad.CheckedExceptionNoRollbackService;
import com.interviewlab.transaction.rollback.good.RollbackForCheckedExceptionService;
import com.interviewlab.transaction.rollback.good.UncheckedExceptionRollbackService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Mülakat yazısı için docs/transactions.md dosyasına bakın. */
class RollbackBehaviorTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CheckedExceptionNoRollbackService checkedExceptionNoRollbackService;

    @Autowired
    private RollbackForCheckedExceptionService rollbackForCheckedExceptionService;

    @Autowired
    private UncheckedExceptionRollbackService uncheckedExceptionRollbackService;

    @Test
    void shouldCommitDespiteCheckedExceptionByDefault() throws Exception {
        Account account = accountRepository.save(new Account("Rollback-Bad", new BigDecimal("100.00")));

        assertThatThrownBy(() ->
                        checkedExceptionNoRollbackService.debitThenFailWithCheckedException(account.getId(), new BigDecimal("30.00")))
                .isInstanceOf(SimulatedCheckedFailureException.class);

        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getBalance())
                .as("Spring'in varsayılan kuralı checked exception'larda commit yapar - debit işlemi hayatta kalır, bug budur")
                .isEqualByComparingTo("70.00");
    }

    @Test
    void shouldRollbackWhenRollbackForConfiguredForCheckedException() {
        Account account = accountRepository.save(new Account("Rollback-Good", new BigDecimal("100.00")));

        assertThatThrownBy(() ->
                        rollbackForCheckedExceptionService.debitThenFailWithCheckedException(account.getId(), new BigDecimal("30.00")))
                .isInstanceOf(SimulatedCheckedFailureException.class);

        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getBalance())
                .as("rollbackFor=Exception.class, debit işlemini geri almalı")
                .isEqualByComparingTo("100.00");
    }

    @Test
    void shouldRollbackOnUncheckedExceptionByDefault() {
        Account account = accountRepository.save(new Account("Rollback-Unchecked", new BigDecimal("100.00")));

        assertThatThrownBy(() ->
                        uncheckedExceptionRollbackService.debitThenFailWithUncheckedException(account.getId(), new BigDecimal("30.00")))
                .isInstanceOf(SimulatedUncheckedFailureException.class);

        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getBalance())
                .as("unchecked exception'lar, rollbackFor gerekmeden otomatik olarak geri alınır")
                .isEqualByComparingTo("100.00");
    }
}
