package com.interviewlab.labrunner.spring;

import com.interviewlab.labrunner.LabRunnerPrint;
import com.interviewlab.transaction.entity.Account;
import com.interviewlab.transaction.entity.AccountRepository;
import com.interviewlab.transaction.rollback.SimulatedCheckedFailureException;
import com.interviewlab.transaction.rollback.bad.CheckedExceptionNoRollbackService;
import com.interviewlab.transaction.rollback.good.RollbackForCheckedExceptionService;
import java.math.BigDecimal;

/** IntelliJ'de sağ tık -> Run/Debug. Postman/HTTP GEREKMEZ. `docker compose up -d` GEREKİR. */
public final class TransactionSpringLabRunner {

    private static final BigDecimal STARTING_BALANCE = new BigDecimal("100.00");
    private static final BigDecimal DEBIT_AMOUNT = new BigDecimal("30.00");

    public static void main(String[] args) {
        SpringLabRunnerSupport.run(ctx -> {
            LabRunnerPrint.banner("TRANSACTION ROLLBACK — checked exception varsayılan kuralı");

            AccountRepository accountRepository = ctx.getBean(AccountRepository.class);
            CheckedExceptionNoRollbackService bad = ctx.getBean(CheckedExceptionNoRollbackService.class);
            RollbackForCheckedExceptionService good = ctx.getBean(RollbackForCheckedExceptionService.class);

            Account badAccount = accountRepository.save(new Account("Demo", STARTING_BALANCE));
            try {
                bad.debitThenFailWithCheckedException(badAccount.getId(), DEBIT_AMOUNT); // <- BREAKPOINT 1
            } catch (SimulatedCheckedFailureException e) {
                LabRunnerPrint.fact("BAD exception", e.getMessage());
            }
            Account badReloaded = accountRepository.findById(badAccount.getId()).orElseThrow();
            LabRunnerPrint.fact("BAD finalBalance (70.00 = COMMITTED)", badReloaded.getBalance());

            Account goodAccount = accountRepository.save(new Account("Demo", STARTING_BALANCE));
            try {
                good.debitThenFailWithCheckedException(goodAccount.getId(), DEBIT_AMOUNT); // <- BREAKPOINT 2
            } catch (SimulatedCheckedFailureException e) {
                LabRunnerPrint.fact("GOOD exception", e.getMessage());
            }
            Account goodReloaded = accountRepository.findById(goodAccount.getId()).orElseThrow();
            LabRunnerPrint.fact("GOOD finalBalance (100.00 = ROLLED_BACK)", goodReloaded.getBalance());

            LabRunnerPrint.section("WHY");
            LabRunnerPrint.line("Spring'in varsayılan rollback kuralı SADECE unchecked RuntimeException/Error içindir.");
            LabRunnerPrint.line("rollbackFor=Exception.class açıkça belirtilmedikçe checked exception COMMIT'i durdurmaz.");
        });
    }
}
