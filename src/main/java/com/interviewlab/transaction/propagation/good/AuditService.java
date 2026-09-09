package com.interviewlab.transaction.propagation.good;

import com.interviewlab.transaction.entity.AuditLog;
import com.interviewlab.transaction.entity.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Özellikle çağıranların {@link #audit(String)}'i Spring proxy'si üzerinden (enjekte
 * edilmiş bir referans aracılığıyla) çağırması için kendi bean'inde yaşar; bu da
 * {@code Propagation.REQUIRES_NEW}'in gerçekten devreye girmesini sağlayan şeydir - bunun
 * önlediği hata modu için bkz.
 * {@link com.interviewlab.transaction.propagation.bad.SelfInvocationPaymentService}.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * REQUIRES_NEW: çağıranın transaction'ını (varsa) askıya alır, yepyeni bir tane başlatır,
     * bunu bağımsız olarak commit eder, ardından çağıranın transaction'ına geri döner. Audit
     * satırı, çağıranın sonrasında ne yaparsa yapsın - rollback dahil - bu metot döner
     * dönmez kalıcı hale gelir.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void audit(String message) {
        auditLogRepository.save(new AuditLog(message));
    }
}
