package com.interviewlab.web.lab.exceptions;

import com.interviewlab.exception.bad.LossyRethrowService;
import com.interviewlab.exception.bad.SwallowingExceptionService;
import com.interviewlab.exception.good.WrappingExceptionService;
import com.interviewlab.web.lab.LabLog;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/INTERACTIVE_LABS.md — LAB 13. Bir checked exception'ı yutmanın (hiçbir iz bırakmadan)
 * ve cause'unu kaybederek yeniden fırlatmanın, orijinal tanı bilgisini nasıl sessizce yok
 * ettiğini; doğru wrap etmenin cause zincirini nasıl koruduğunu gösterir.
 */
@RestController
@RequestMapping("/api/labs/exceptions")
public class ExceptionsLabController {

    private final SwallowingExceptionService swallowingExceptionService;
    private final LossyRethrowService lossyRethrowService;
    private final WrappingExceptionService wrappingExceptionService;

    public ExceptionsLabController(SwallowingExceptionService swallowingExceptionService,
                                    LossyRethrowService lossyRethrowService,
                                    WrappingExceptionService wrappingExceptionService) {
        this.swallowingExceptionService = swallowingExceptionService;
        this.lossyRethrowService = lossyRethrowService;
        this.wrappingExceptionService = wrappingExceptionService;
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "EXCEPTIONS");
        body.put("action", "RESET");
        body.put("nextStep", "POST /api/labs/exceptions/swallowed");
        return body;
    }

    @PostMapping("/swallowed")
    public Map<String, Object> swallowed() {
        LabLog.banner("EXCEPTIONS", "BAD — swallowed (yutulmuş)");
        boolean reportedSuccess = swallowingExceptionService.chargeCardSilently("tok_bad", 50.0);
        LabLog.lesson("Gateway gerçekten reddetti (checked exception fırlattı), ama catch bloğu hiçbir şey "
                + "yapmadı - çağıran, ücretlendirmenin başarısız olduğunu bilmenin hiçbir yoluna sahip değil.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "EXCEPTIONS");
        body.put("mode", "SWALLOWED");
        body.put("gatewayActuallyDeclined", true);
        body.put("methodReportedSuccess", reportedSuccess);
        body.put("problem", "Checked exception hiçbir iz bırakmadan yutuldu - çağıran başarı sanıyor.");
        body.put("nextStep", "POST /api/labs/exceptions/lossy-rethrow");
        return body;
    }

    @PostMapping("/lossy-rethrow")
    public Map<String, Object> lossyRethrow() {
        LabLog.banner("EXCEPTIONS", "BAD — lossy rethrow (cause kaybedilerek yeniden fırlatma)");
        String message;
        boolean hasCause;
        try {
            lossyRethrowService.chargeCardLosingCause("tok_bad", 50.0);
            message = "beklenmedik şekilde exception fırlatmadı";
            hasCause = false;
        } catch (RuntimeException e) {
            message = e.getMessage();
            hasCause = e.getCause() != null;
        }
        LabLog.lesson("Bir şey fırlatıldı (swallowing'den daha iyi), ama orijinal PaymentGatewayCheckedException "
                + "cause olarak eklenmeden atıldı - decline kodu gibi asıl tanı bilgisi kayboldu.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "EXCEPTIONS");
        body.put("mode", "LOSSY_RETHROW");
        body.put("exceptionMessage", message);
        body.put("hasOriginalCause", hasCause);
        body.put("problem", "Yeni exception fırlatıldı ama orijinal (decline kodu taşıyan) cause olarak eklenmedi.");
        body.put("nextStep", "POST /api/labs/exceptions/good");
        return body;
    }

    @PostMapping("/good")
    public Map<String, Object> good() {
        LabLog.banner("EXCEPTIONS", "GOOD (cause korunarak wrap edilmiş)");
        String message;
        String causeMessage;
        try {
            wrappingExceptionService.chargeCard("tok_bad", 50.0);
            message = "beklenmedik şekilde exception fırlatmadı";
            causeMessage = null;
        } catch (RuntimeException e) {
            message = e.getMessage();
            causeMessage = e.getCause() != null ? e.getCause().getMessage() : null;
        }
        LabLog.lesson("Anlamlı bir unchecked business exception fırlatıldı VE orijinal gateway exception'ı cause "
                + "olarak taşıyor - getCause() hâlâ decline kodunu içeriyor, tam stack trace zinciri korunuyor.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "EXCEPTIONS");
        body.put("mode", "GOOD");
        body.put("exceptionMessage", message);
        body.put("causeMessage", causeMessage);
        body.put("causePreserved", causeMessage != null);
        body.put("lesson", "cause olarak asıl exception korunuyor - " + causeMessage);
        return body;
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "EXCEPTIONS");
        body.put("note", "GlobalExceptionHandler + PaymentDemoController üzerinden HTTP hata gövdelerini denemek için: POST /lab/exceptions/charge?cardToken=tok_123&amount=50.0");
        return body;
    }
}
