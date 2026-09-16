package com.interviewlab.labrunner.spring;

import com.interviewlab.labrunner.LabRunnerPrint;
import com.interviewlab.transaction.entity.Account;
import com.interviewlab.transaction.entity.AccountRepository;
import com.interviewlab.transaction.entity.AuditLogRepository;
import com.interviewlab.transaction.propagation.OrderProcessingException;
import com.interviewlab.transaction.propagation.bad.SelfInvocationPaymentService;
import com.interviewlab.transaction.propagation.good.OrderService;
import java.math.BigDecimal;

/** IntelliJ'de sağ tık -> Run/Debug. Postman/HTTP GEREKMEZ. `docker compose up -d` GEREKİR. */
public final class PropagationSpringLabRunner {

    private static final BigDecimal STARTING_BALANCE = new BigDecimal("500.00");
    private static final BigDecimal PAYMENT_AMOUNT = new BigDecimal("50.00");

    public static void main(String[] args) {
        SpringLabRunnerSupport.run(ctx -> {
            LabRunnerPrint.banner("PROPAGATION — REQUIRES_NEW self-invocation");

            AccountRepository accountRepository = ctx.getBean(AccountRepository.class);
            AuditLogRepository auditLogRepository = ctx.getBean(AuditLogRepository.class);
            SelfInvocationPaymentService bad = ctx.getBean(SelfInvocationPaymentService.class);
            OrderService good = ctx.getBean(OrderService.class);

            Account badAccount = accountRepository.save(new Account("Demo-self-invocation", STARTING_BALANCE));
            String badAuditMessage = "self-invocation-" + badAccount.getId();
            try {
                bad.processPayment(badAccount.getId(), PAYMENT_AMOUNT, badAuditMessage); // <- BREAKPOINT 1: içeride this.audit(...)
            } catch (OrderProcessingException e) {
                LabRunnerPrint.fact("BAD paymentOutcome", "OrderProcessingException: " + e.getMessage());
            }
            LabRunnerPrint.fact("BAD auditSurvivedOuterRollback", !auditLogRepository.findByMessage(badAuditMessage).isEmpty());

            Account goodAccount = accountRepository.save(new Account("Demo-real-proxy", STARTING_BALANCE));
            String goodAuditMessage = "real-proxy-" + goodAccount.getId();
            try {
                good.processPayment(goodAccount.getId(), PAYMENT_AMOUNT, goodAuditMessage); // <- BREAKPOINT 2: auditService.audit() proxy üzerinden
            } catch (OrderProcessingException e) {
                LabRunnerPrint.fact("GOOD paymentOutcome", "OrderProcessingException: " + e.getMessage());
            }
            LabRunnerPrint.fact("GOOD auditSurvivedOuterRollback", !auditLogRepository.findByMessage(goodAuditMessage).isEmpty());

            LabRunnerPrint.section("WHY");
            LabRunnerPrint.line("REQUIRES_NEW, self-invocation ile çağrıldığında proxy atlanır ve sessizce göz ardı");
            LabRunnerPrint.line("edilir; ayrı bir bean üzerinden çağrıldığında gerçekten bağımsız commit eder.");
        });
    }
}
