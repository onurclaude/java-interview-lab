package com.interviewlab.transaction.rollback.good;

import com.interviewlab.transaction.entity.Account;
import com.interviewlab.transaction.entity.AccountRepository;
import com.interviewlab.transaction.rollback.SimulatedUncheckedFailureException;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Temel doğru davranış: unchecked bir exception, {@code rollbackFor}'a gerek kalmadan
 * otomatik olarak rollback olur. {@code transaction.rollback.bad}/
 * {@code transaction.rollback.good} içindeki checked-exception durumuyla karşılaştırın.
 */
@Service
public class UncheckedExceptionRollbackService {

    private final AccountRepository accountRepository;

    public UncheckedExceptionRollbackService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public void debitThenFailWithUncheckedException(Long accountId, BigDecimal amount) {
        Account account = accountRepository.findById(accountId).orElseThrow();
        account.debit(amount);
        throw new SimulatedUncheckedFailureException("Downstream validation failed");
    }
}
