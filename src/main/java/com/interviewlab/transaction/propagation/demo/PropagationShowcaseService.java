package com.interviewlab.transaction.propagation.demo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Bu projenin başka bir yerde tam bad/good muamelesi göstermediği her propagation tipi için
 * bir metot (REQUIRED, REQUIRES_NEW ve NESTED, {@code transaction.propagation.bad}/
 * {@code good} ve {@link NestedStepService} içinde özel bad/good demolarına sahip). Her
 * metot, çalıştığı anda gerçek bir transaction'ın aktif olup olmadığını dürüstçe bildirir.
 *
 * <p>Testler bunları bu bean'in dışından tetikler - bazıları "zaten bir transaction aktifken
 * çağrıldı" durumunu simüle etmek için bir {@code TransactionTemplate} içine sarılmış,
 * bazıları "hiç transaction olmadan çağrıldı" durumunu simüle etmek için çıplak çağrılmış -
 * bkz. {@code PropagationShowcaseTest}. Çağıran, bu sınıfın kendi metodunu çağırması yerine
 * farklı bir nesne olduğundan (test, veya bir TransactionTemplate callback'i), buradaki her
 * çağrı doğru şekilde Spring proxy'sinden geçer.
 */
@Service
public class PropagationShowcaseService {

    /** SUPPORTS: varsa bir transaction'a katılır, yoksa transaction olmadan çalışır. Asla hata vermez. */
    @Transactional(propagation = Propagation.SUPPORTS)
    public boolean isTransactionActiveUnderSupports() {
        return TransactionSynchronizationManager.isActualTransactionActive();
    }

    /** MANDATORY: mevcut bir transaction gerektirir; hiç transaction olmadan çağrılırsa {@code IllegalTransactionStateException} fırlatır. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean isTransactionActiveUnderMandatory() {
        return TransactionSynchronizationManager.isActualTransactionActive();
    }

    /** NOT_SUPPORTED: mevcut herhangi bir transaction'ı askıya alır ve transactional olmadan çalışır. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean isTransactionActiveUnderNotSupported() {
        return TransactionSynchronizationManager.isActualTransactionActive();
    }

    /** NEVER: zaten bir transaction aktifken çağrılırsa {@code IllegalTransactionStateException} fırlatır. */
    @Transactional(propagation = Propagation.NEVER)
    public boolean isTransactionActiveUnderNever() {
        return TransactionSynchronizationManager.isActualTransactionActive();
    }
}
