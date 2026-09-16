package com.interviewlab.web.lab.async;

import com.interviewlab.async.spring.AsyncConfig;
import com.interviewlab.async.spring.bad.SelfInvocationNotificationService;
import com.interviewlab.async.spring.good.AsyncSender;
import com.interviewlab.async.spring.good.NotificationService;
import com.interviewlab.web.lab.LabLog;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/INTERACTIVE_LABS.md — LAB 09. {@code @Async}'in self-invocation ile neden sessizce
 * devre dışı kaldığını (aynı proxy sınırı, {@code @Transactional}/AOP ile paylaşılan kök
 * neden) gösterir - bkz. docs/async.md.
 */
@RestController
@RequestMapping("/api/labs/async")
public class AsyncLabController {

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(3);

    private final SelfInvocationNotificationService selfInvocationNotificationService;
    private final NotificationService notificationService;
    private final AsyncSender asyncSender;

    public AsyncLabController(SelfInvocationNotificationService selfInvocationNotificationService,
                               NotificationService notificationService,
                               AsyncSender asyncSender) {
        this.selfInvocationNotificationService = selfInvocationNotificationService;
        this.notificationService = notificationService;
        this.asyncSender = asyncSender;
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        AsyncConfig.clearUncaughtAsyncExceptions();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "SPRING_ASYNC");
        body.put("action", "RESET");
        body.put("nextStep", "POST /api/labs/async/bad");
        return body;
    }

    @PostMapping("/bad")
    public Map<String, Object> bad() {
        LabLog.banner("SPRING @ASYNC", "BAD (self-invocation)");
        String callerThread = selfInvocationNotificationService.notifyUser("hello-bad");
        String asyncThread = pollUntilNonNull(selfInvocationNotificationService::lastAsyncThreadName);
        boolean ranOnSameThread = callerThread.equals(asyncThread);
        LabLog.line("caller thread={}, 'async' iş bu thread'de çalıştı={}", callerThread, asyncThread);
        LabLog.lesson("this.sendAsync(...) self-invocation'dır - @Async proxy'sini hiç görmez, bu yüzden metot "
                + "hiçbir yeni thread'e gönderilmeden, çağıranın KENDİ thread'inde senkron olarak çalışır.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "SPRING_ASYNC");
        body.put("mode", "BAD");
        body.put("callerThread", callerThread);
        body.put("workRanOnThread", asyncThread);
        body.put("ranOnSameThreadAsCaller", ranOnSameThread);
        body.put("problem", "self-invocation, @Async proxy'sini atladı - 'asenkron' iş aslında senkron çalıştı.");
        body.put("nextStep", "POST /api/labs/async/good ile ayrı bean üzerinden gerçek async çalışmayı gör");
        return body;
    }

    @PostMapping("/good")
    public Map<String, Object> good() {
        LabLog.banner("SPRING @ASYNC", "GOOD (ayrı bean üzerinden gerçek proxy çağrısı)");
        String callerThread = notificationService.notifyUser("hello-good");
        String asyncThread = pollUntilNonNull(asyncSender::lastAsyncThreadName);
        boolean ranOnDifferentThread = !callerThread.equals(asyncThread);
        LabLog.line("caller thread={}, iş şu thread'de çalıştı={}", callerThread, asyncThread);
        LabLog.lesson("asyncSender.sendAsync(...) ayrı bir bean üzerinden çağrıldığı için gerçek proxy'den geçti - "
                + "iş gerçekten 'labAsyncExecutor' havuzundaki farklı bir thread'de çalıştı, caller hemen döndü.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "SPRING_ASYNC");
        body.put("mode", "GOOD");
        body.put("callerThread", callerThread);
        body.put("workRanOnThread", asyncThread);
        body.put("ranOnDifferentThreadThanCaller", ranOnDifferentThread);
        body.put("lesson", "Gerçek @Async: iş farklı bir thread'de (labAsyncExecutor havuzu) çalıştı, caller hemen döndü.");
        return body;
    }

    @PostMapping("/void-exception")
    public Map<String, Object> voidException() {
        LabLog.banner("SPRING @ASYNC", "void dönen metod + exception");
        AsyncConfig.clearUncaughtAsyncExceptions();
        asyncSender.sendAsyncAndFail();
        boolean caught = pollUntilTrue(() -> !AsyncConfig.uncaughtAsyncExceptions().isEmpty());
        LabLog.lesson("void dönen bir @Async metodun çağıranın yakalayabileceği bir Future'ı yok - fırlatılan "
                + "exception, sadece özel bir AsyncUncaughtExceptionHandler kaydedilmişse görülebilir; aksi "
                + "halde sessizce kaybolur.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "SPRING_ASYNC");
        body.put("mode", "VOID_EXCEPTION");
        body.put("exceptionCapturedByHandler", caught);
        body.put("lesson", "void @Async metodun exception'ı çağırana asla ulaşmaz - sadece AsyncUncaughtExceptionHandler'a.");
        return body;
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "SPRING_ASYNC");
        body.put("uncaughtAsyncExceptionCount", AsyncConfig.uncaughtAsyncExceptions().size());
        return body;
    }

    private static String pollUntilNonNull(java.util.function.Supplier<String> supplier) {
        Instant deadline = Instant.now().plus(POLL_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            String value = supplier.get();
            if (value != null) {
                return value;
            }
            sleepBriefly();
        }
        return supplier.get();
    }

    private static boolean pollUntilTrue(java.util.function.BooleanSupplier condition) {
        Instant deadline = Instant.now().plus(POLL_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return true;
            }
            sleepBriefly();
        }
        return condition.getAsBoolean();
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
