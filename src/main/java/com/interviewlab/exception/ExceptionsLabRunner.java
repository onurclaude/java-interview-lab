package com.interviewlab.exception;

import com.interviewlab.labrunner.LabRunnerPrint;

import com.interviewlab.exception.PaymentGatewayClient;
import com.interviewlab.exception.bad.LossyRethrowService;
import com.interviewlab.exception.bad.SwallowingExceptionService;
import com.interviewlab.exception.good.WrappingExceptionService;

/** IntelliJ'de sağ tık -> Run/Debug. Postman/HTTP GEREKMEZ - Spring de GEREKMEZ (saf POJO). */
public final class ExceptionsLabRunner {

    public static void main(String[] args) {
        LabRunnerPrint.banner("EXCEPTIONS — swallow / lossy-rethrow / wrap");

        PaymentGatewayClient gateway = new PaymentGatewayClient();

        SwallowingExceptionService swallowing = new SwallowingExceptionService(gateway);
        boolean reportedSuccess = swallowing.chargeCardSilently("tok_bad", 50.0); // <- BREAKPOINT 1: catch bloğu BOŞ/log-only
        LabRunnerPrint.fact("SWALLOWED methodReportedSuccess (gerçekte başarısız oldu)", reportedSuccess);

        LossyRethrowService lossy = new LossyRethrowService(gateway);
        try {
            lossy.chargeCardLosingCause("tok_bad", 50.0); // <- BREAKPOINT 2: new RuntimeException(e.getMessage()) - cause YOK
        } catch (RuntimeException e) {
            LabRunnerPrint.fact("LOSSY_RETHROW message", e.getMessage());
            LabRunnerPrint.fact("LOSSY_RETHROW hasOriginalCause", e.getCause() != null);
        }

        WrappingExceptionService wrapping = new WrappingExceptionService(gateway);
        try {
            wrapping.chargeCard("tok_bad", 50.0); // <- BREAKPOINT 3: new RuntimeException(msg, e) - cause KORUNUR
        } catch (RuntimeException e) {
            LabRunnerPrint.fact("GOOD message", e.getMessage());
            LabRunnerPrint.fact("GOOD causeMessage", e.getCause() != null ? e.getCause().getMessage() : null);
            LabRunnerPrint.fact("GOOD causePreserved", e.getCause() != null);
        }

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("Swallowed: exception hiçbir iz bırakmadan yutuldu, çağıran başarı sanıyor. Lossy-rethrow:");
        LabRunnerPrint.line("bir şey fırlatıldı ama orijinal (tanı bilgisi taşıyan) exception cause olarak eklenmedi.");
        LabRunnerPrint.line("Wrapped: yeni exception, orijinali cause olarak TAŞIYOR - stack trace/kök neden korunuyor.");
    }
}
