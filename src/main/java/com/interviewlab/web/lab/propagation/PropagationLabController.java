package com.interviewlab.web.lab.propagation;

import com.interviewlab.transaction.entity.Account;
import com.interviewlab.transaction.entity.AccountRepository;
import com.interviewlab.transaction.entity.AuditLogRepository;
import com.interviewlab.transaction.propagation.OrderProcessingException;
import com.interviewlab.transaction.propagation.bad.SelfInvocationPaymentService;
import com.interviewlab.transaction.propagation.good.OrderService;
import com.interviewlab.web.lab.LabLog;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/INTERACTIVE_LABS.md — LAB 03. REQUIRED (varsayılan, katıl-veya-oluştur) ile
 * REQUIRES_NEW (askıya al + bağımsız transaction) arasındaki farkı, ve REQUIRES_NEW'i
 * self-invocation ile çağırmanın onu neden sessizce devre dışı bıraktığını gösterir - bkz.
 * docs/propagation.md, docs/transactions.md.
 */
@RestController
@RequestMapping("/api/labs/propagation")
public class PropagationLabController {

    private static final BigDecimal STARTING_BALANCE = new BigDecimal("500.00");
    private static final BigDecimal PAYMENT_AMOUNT = new BigDecimal("50.00");

    private final AccountRepository accountRepository;
    private final AuditLogRepository auditLogRepository;
    private final SelfInvocationPaymentService selfInvocationPaymentService;
    private final OrderService orderService;
    private final AtomicReference<Long> currentAccountId = new AtomicReference<>();

    public PropagationLabController(AccountRepository accountRepository,
                                     AuditLogRepository auditLogRepository,
                                     SelfInvocationPaymentService selfInvocationPaymentService,
                                     OrderService orderService) {
        this.accountRepository = accountRepository;
        this.auditLogRepository = auditLogRepository;
        this.selfInvocationPaymentService = selfInvocationPaymentService;
        this.orderService = orderService;
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        accountRepository.deleteAll();
        currentAccountId.set(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "PROPAGATION");
        body.put("action", "RESET");
        body.put("nextStep", "POST /api/labs/propagation/required");
        return body;
    }

    @PostMapping("/required")
    @Transactional
    public Map<String, Object> required() {
        LabLog.banner("PROPAGATION", "REQUIRED (varsayılan)");
        boolean activeBeforeRepositoryCall = TransactionSynchronizationManager.isActualTransactionActive();
        Account account = accountRepository.save(new Account("Demo", STARTING_BALANCE));
        currentAccountId.set(account.getId());
        account.credit(new BigDecimal("10.00"));
        boolean stillActiveAfterRepositoryCall = TransactionSynchronizationManager.isActualTransactionActive();
        LabLog.line("Bu controller metodu zaten transactional - accountRepository.save() ayrı bir transaction AÇMADI, buna KATILDI.");
        LabLog.lesson("REQUIRED (hiçbir propagation belirtilmediğinde varsayılan), zaten aktif bir transaction "
                + "varsa ona katılır; yeni bir tane açmaz. Tek bir fiziksel transaction/connection kullanılır.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "PROPAGATION");
        body.put("mode", "REQUIRED");
        body.put("accountId", account.getId());
        body.put("transactionActiveBeforeRepositoryCall", activeBeforeRepositoryCall);
        body.put("transactionActiveAfterRepositoryCall", stillActiveAfterRepositoryCall);
        body.put("lesson", "REQUIRED, çağıranın transaction'ı varsa ona katılır - ikinci bir fiziksel "
                + "transaction/connection AÇMAZ.");
        return body;
    }

    @PostMapping("/requires-new/bad")
    public Map<String, Object> requiresNewBad() {
        LabLog.banner("PROPAGATION — REQUIRES_NEW", "BAD (self-invocation)");
        Account account = accountRepository.save(new Account("Demo-self-invocation", STARTING_BALANCE));
        currentAccountId.set(account.getId());
        String auditMessage = "self-invocation-" + account.getId();

        String outcome = runPayment(() -> selfInvocationPaymentService.processPayment(account.getId(), PAYMENT_AMOUNT, auditMessage));

        boolean auditSurvived = !auditLogRepository.findByMessage(auditMessage).isEmpty();
        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        LabLog.line("audit() aynı sınıftan this.audit(...) ile çağrıldı - proxy'yi hiç görmedi.");
        LabLog.lesson("REQUIRES_NEW, self-invocation ile (this.audit(...)) çağrıldığında sessizce göz ardı edilir - "
                + "proxy sadece DIŞARIDAN gelen çağrıları intercept eder. audit, outer transaction ile AYNI "
                + "transaction içinde kalır ve onunla birlikte rollback olur.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "PROPAGATION");
        body.put("mode", "REQUIRES_NEW_BAD_SELF_INVOCATION");
        body.put("accountId", account.getId());
        body.put("paymentOutcome", outcome);
        body.put("auditSurvivedOuterRollback", auditSurvived);
        body.put("finalBalance", reloaded.getBalance());
        body.put("problem", "audit(), REQUIRES_NEW ile işaretli ama this.audit(...) üzerinden çağrıldı - proxy "
                + "atlandı, REQUIRES_NEW hiç uygulanmadı, audit kaydı outer rollback ile birlikte KAYBOLDU.");
        body.put("nextStep", "SELECT * FROM lab_audit_log WHERE message = '" + auditMessage
                + "'; ile hiçbir satır olmadığını doğrula, sonra POST /api/labs/propagation/requires-new/good");
        return body;
    }

    @PostMapping("/requires-new/good")
    public Map<String, Object> requiresNewGood() {
        LabLog.banner("PROPAGATION — REQUIRES_NEW", "GOOD (ayrı bean üzerinden gerçek proxy çağrısı)");
        Account account = accountRepository.save(new Account("Demo-real-proxy", STARTING_BALANCE));
        currentAccountId.set(account.getId());
        String auditMessage = "real-proxy-" + account.getId();

        String outcome = runPayment(() -> orderService.processPayment(account.getId(), PAYMENT_AMOUNT, auditMessage));

        boolean auditSurvived = !auditLogRepository.findByMessage(auditMessage).isEmpty();
        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        LabLog.line("audit(), enjekte edilmiş AYRI bir bean (AuditService) üzerinden çağrıldı - gerçek proxy'den geçti.");
        LabLog.lesson("REQUIRES_NEW, ayrı bir bean referansı üzerinden çağrıldığında gerçek proxy'den geçer: "
                + "bağımsız bir transaction açar, outer rollback olmadan ÖNCE commit eder ve outer rollback "
                + "olsa bile HAYATTA KALIR.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "PROPAGATION");
        body.put("mode", "REQUIRES_NEW_GOOD_SEPARATE_BEAN");
        body.put("accountId", account.getId());
        body.put("paymentOutcome", outcome);
        body.put("auditSurvivedOuterRollback", auditSurvived);
        body.put("finalBalance", reloaded.getBalance());
        body.put("lesson", "Ayrı bir bean üzerinden çağrılan REQUIRES_NEW, gerçekten bağımsız commit eder - "
                + "outer transaction rollback olsa bile audit kaydı DB'de kalır.");
        return body;
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "PROPAGATION");
        body.put("accounts", accountRepository.findAll());
        body.put("auditLogs", auditLogRepository.findAll());
        body.put("currentAccountId", currentAccountId.get());
        body.put("dbeaverQuery", "SELECT * FROM lab_account; SELECT * FROM lab_audit_log;");
        return body;
    }

    private static String runPayment(Runnable payment) {
        try {
            payment.run();
            return "beklenmedik şekilde exception fırlatmadan tamamlandı";
        } catch (OrderProcessingException e) {
            return "OrderProcessingException fırlatıldı (beklenen): " + e.getMessage();
        }
    }
}
