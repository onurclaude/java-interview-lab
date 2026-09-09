package com.interviewlab.exception;

/**
 * Bu projenin business exception hiyerarşisinin kökü.
 *
 * <p><b>Checked mi unchecked mi?</b> Bilerek unchecked ({@link RuntimeException}'ı extend
 * eder). Checked bir exception, stack'teki her çağıranı ya onu yakalamaya ya da
 * {@code throws} bildirmeye zorlar; bu da pratikte geliştiricilere derleyicinin şikayetini
 * kesmek için sadece {@code catch (Exception e) {}} yazmayı öğretir - bkz.
 * {@link com.interviewlab.exception.bad.SwallowingExceptionService}. Ayrıca Spring'in
 * varsayılan {@code @Transactional} rollback kuralıyla da kötü etkileşir; bu kural,
 * {@code rollbackFor} aksini söylemediği sürece checked exception'larda rollback YAPMAZ
 * (bkz. docs/transactions.md ve {@code transaction.rollback}). Modern Spring tarzı
 * codebase'ler, tam olarak bu iki sorundan kaçınmak için genellikle unchecked business
 * exception'ları tercih eder.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
