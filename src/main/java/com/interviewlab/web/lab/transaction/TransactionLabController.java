package com.interviewlab.web.lab.transaction;

import com.interviewlab.transaction.entity.Account;
import com.interviewlab.transaction.entity.AccountRepository;
import com.interviewlab.transaction.rollback.SimulatedCheckedFailureException;
import com.interviewlab.transaction.rollback.bad.CheckedExceptionNoRollbackService;
import com.interviewlab.transaction.rollback.good.RollbackForCheckedExceptionService;
import com.interviewlab.web.lab.LabLog;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/INTERACTIVE_LABS.md — LAB 02. Spring'in varsayılan rollback kuralı: checked
 * exception'lar rollback ETMEZ, unchecked exception'lar rollback EDER — bkz. docs/transactions.md.
 */
@RestController
@RequestMapping("/api/labs/transaction")
public class TransactionLabController {

    private static final BigDecimal STARTING_BALANCE = new BigDecimal("100.00");
    private static final BigDecimal DEBIT_AMOUNT = new BigDecimal("30.00");

    private final AccountRepository accountRepository;
    private final CheckedExceptionNoRollbackService checkedExceptionNoRollbackService;
    private final RollbackForCheckedExceptionService rollbackForCheckedExceptionService;
    private final AtomicReference<Long> currentAccountId = new AtomicReference<>();

    public TransactionLabController(AccountRepository accountRepository,
                                     CheckedExceptionNoRollbackService checkedExceptionNoRollbackService,
                                     RollbackForCheckedExceptionService rollbackForCheckedExceptionService) {
        this.accountRepository = accountRepository;
        this.checkedExceptionNoRollbackService = checkedExceptionNoRollbackService;
        this.rollbackForCheckedExceptionService = rollbackForCheckedExceptionService;
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        accountRepository.deleteAll();
        currentAccountId.set(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "TRANSACTION_ROLLBACK");
        body.put("action", "RESET");
        body.put("nextStep", "POST /api/labs/transaction/bad");
        return body;
    }

    @PostMapping("/bad")
    public Map<String, Object> bad() {
        LabLog.banner("TRANSACTION ROLLBACK", "BAD (checked exception, rollbackFor yok)");
        Account account = accountRepository.save(new Account("Demo", STARTING_BALANCE));
        currentAccountId.set(account.getId());
        LabLog.line("Account id={} balance={} oluşturuldu, debit + checked exception deneniyor.", account.getId(), STARTING_BALANCE);

        String outcome;
        try {
            checkedExceptionNoRollbackService.debitThenFailWithCheckedException(account.getId(), DEBIT_AMOUNT);
            outcome = "beklenmedik şekilde exception fırlatmadan tamamlandı";
        } catch (SimulatedCheckedFailureException e) {
            outcome = "SimulatedCheckedFailureException fırlatıldı: " + e.getMessage();
        }

        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        LabLog.line("Transaction COMMIT oldu (checked exception, varsayılan rollback kuralını tetiklemedi).");
        LabLog.lesson("Spring'in varsayılan rollback kuralı SADECE unchecked RuntimeException/Error içindir. "
                + "rollbackFor açıkça belirtilmedikçe, checked bir exception fırlatılsa bile transaction COMMIT eder.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "TRANSACTION_ROLLBACK");
        body.put("mode", "BAD");
        body.put("accountId", account.getId());
        body.put("startingBalance", STARTING_BALANCE);
        body.put("exceptionOutcome", outcome);
        body.put("finalBalance", reloaded.getBalance());
        body.put("transactionResult", "COMMITTED");
        body.put("problem", "Checked exception fırlatıldı ama Spring'in varsayılan rollback kuralı sadece "
                + "unchecked'i kapsıyor - debit KALICI hale geldi, hiçbir hata olmamış gibi.");
        body.put("nextStep", "SELECT * FROM lab_account; ile balance=70.00 gördüğünü doğrula, sonra POST /api/labs/transaction/good");
        return body;
    }

    @PostMapping("/good")
    public Map<String, Object> good() {
        LabLog.banner("TRANSACTION ROLLBACK", "GOOD (rollbackFor = Exception.class)");
        Account account = accountRepository.save(new Account("Demo", STARTING_BALANCE));
        currentAccountId.set(account.getId());
        LabLog.line("Account id={} balance={} oluşturuldu, debit + checked exception + rollbackFor deneniyor.", account.getId(), STARTING_BALANCE);

        String outcome;
        try {
            rollbackForCheckedExceptionService.debitThenFailWithCheckedException(account.getId(), DEBIT_AMOUNT);
            outcome = "beklenmedik şekilde exception fırlatmadan tamamlandı";
        } catch (SimulatedCheckedFailureException e) {
            outcome = "SimulatedCheckedFailureException fırlatıldı: " + e.getMessage();
        }

        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        LabLog.line("Transaction ROLLBACK oldu (rollbackFor = Exception.class, checked exception'ı da kapsıyor).");
        LabLog.lesson("@Transactional(rollbackFor = Exception.class) ile checked exception'lar da rollback'i "
                + "tetikler - balance başlangıç değerine geri döner, hiçbir şey commit olmamış gibi.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "TRANSACTION_ROLLBACK");
        body.put("mode", "GOOD");
        body.put("accountId", account.getId());
        body.put("startingBalance", STARTING_BALANCE);
        body.put("exceptionOutcome", outcome);
        body.put("finalBalance", reloaded.getBalance());
        body.put("transactionResult", "ROLLED_BACK");
        body.put("lesson", "rollbackFor = Exception.class, checked exception'ları da varsayılan rollback kuralına dahil eder.");
        return body;
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "TRANSACTION_ROLLBACK");
        body.put("accounts", accountRepository.findAll());
        body.put("currentAccountId", currentAccountId.get());
        body.put("dbeaverQuery", "SELECT * FROM lab_account;");
        return body;
    }
}
