package com.interviewlab.transaction.rollback.good;

import com.interviewlab.transaction.entity.Account;
import com.interviewlab.transaction.entity.AccountRepository;
import com.interviewlab.transaction.rollback.SimulatedCheckedFailureException;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link com.interviewlab.transaction.rollback.bad.CheckedExceptionNoRollbackService}'in
 * doğru karşılığı: {@code rollbackFor}, Spring'in rollback kuralını checked exception'ı da
 * kapsayacak şekilde genişletir, böylece metot başarısız olduğunda debit geri alınır.
 */
@Service
public class RollbackForCheckedExceptionService {

    private final AccountRepository accountRepository;

    public RollbackForCheckedExceptionService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    public void debitThenFailWithCheckedException(Long accountId, BigDecimal amount) throws SimulatedCheckedFailureException {
        Account account = accountRepository.findById(accountId).orElseThrow();
        account.debit(amount);
        throw new SimulatedCheckedFailureException("Downstream validation failed");
    }
}
