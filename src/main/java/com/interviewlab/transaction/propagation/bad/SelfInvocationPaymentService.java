package com.interviewlab.transaction.propagation.bad;

import com.interviewlab.transaction.entity.Account;
import com.interviewlab.transaction.entity.AccountRepository;
import com.interviewlab.transaction.entity.AuditLog;
import com.interviewlab.transaction.entity.AuditLogRepository;
import com.interviewlab.transaction.propagation.OrderProcessingException;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * NE YANLIŞ?
 * {@code processPayment()}, {@code this.audit(...)}'i doğrudan çağırıyor - {@code this}
 * üzerinde düz bir Java metot çağrısı - ve {@link #audit} üzerindeki
 * {@code @Transactional(propagation = REQUIRES_NEW)}'in, dış transaction rollback olsa bile
 * ayakta kalan bağımsız bir transaction açmasını bekliyor.
 *
 * <p>NEDEN YANLIŞ?
 * Spring'in bildirimsel (declarative) {@code @Transactional}'ı bu bean'i saran bir proxy
 * üzerinden çalışır. Proxy, <em>bean'in dışından</em> gelen çağrıları yakalar (başka bean'lerin
 * tuttuğu enjekte edilmiş referans üzerinden). Aynı instance içinden {@code this.audit(...)}
 * şeklinde yapılan bir çağrı hiçbir zaman bu proxy'den geçmez - doğrudan bir JVM metot
 * çağrısıdır - bu yüzden annotation'ın hiçbir advice'ı (bu durumda yeni bir transaction
 * başlatmak) çalışmaz. {@code audit()}'in gövdesi yine de çalışır, ama zaten aktif olan
 * (varsa) transaction içinde sıradan kod olarak - burada, {@code processPayment()} ile aynı
 * transaction içinde.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Kurcalamaya karşı korumalı / her zaman commit edilmiş olması gereken bir audit trail
 * (örneğin "bir iade denemesinin yapıldığını, daha sonra başarısız olsa bile her zaman
 * kaydederiz") dış iş transaction'ı rollback olduğunda sessizce kaybolur - bu da
 * geliştiricinin tam olarak önlemek için {@code REQUIRES_NEW} eklediği durumdur.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code PropagationSelfInvocationTest.shouldRollBackAuditLogDueToSelfInvocation()}'a
 * bakın: {@code processPayment}'ı çağırın, exception fırlatmasına izin verin, ardından
 * {@code audit()} {@code REQUIRES_NEW} taşımasına rağmen o mesaj için audit log'un boş
 * olduğunu kontrol edin.
 *
 * <p>NASIL DÜZELTİLİR?
 * {@code audit()}'i farklı bir Spring bean'ine taşıyın (bkz.
 * {@link com.interviewlab.transaction.propagation.good.AuditService}) ve onu enjekte
 * edilmiş bir referans üzerinden çağırın, böylece çağrı proxy'den geçer.
 */
@Service
public class SelfInvocationPaymentService {

    private static final Logger log = LoggerFactory.getLogger(SelfInvocationPaymentService.class);

    private final AccountRepository accountRepository;
    private final AuditLogRepository auditLogRepository;

    public SelfInvocationPaymentService(AccountRepository accountRepository, AuditLogRepository auditLogRepository) {
        this.accountRepository = accountRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void processPayment(Long accountId, BigDecimal amount, String auditMessage) {
        Account account = accountRepository.findById(accountId).orElseThrow();
        account.debit(amount);

        this.audit(auditMessage); // SELF-INVOCATION (KENDİ KENDİNİ ÇAĞIRMA): transactional proxy'yi tamamen atlar
        log.warn("Called audit() via 'this.' - REQUIRES_NEW on it will be silently ignored");

        throw new OrderProcessingException("payment gateway timeout");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void audit(String message) {
        auditLogRepository.save(new AuditLog(message));
    }
}
