package com.interviewlab.javacore.saga;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Saga pattern'inin minimal bir orkestratör (orchestration-based saga) implementasyonu:
 * bir dizi LOKAL adım (her biri kendi transaction'ı, kendi servisi/veritabanı olabilir),
 * herhangi biri başarısız olursa önceki BAŞARILI adımları TERSİNE ÇEVİREN (compensating
 * transaction) adımlarla geri alınır.
 *
 * <p><b>2PC (Two-Phase Commit) ile temel fark:</b> 2PC, TEK bir dağıtık transaction'ı tüm
 * katılımcılar arasında ATOMİK yapmaya çalışır (bir coordinator, "prepare" aşamasında
 * herkesin hazır olduğunu doğrular, sonra "commit" aşamasında hepsine commit emri verir) -
 * bu, katılımcıların TÜMÜNÜN prepare fazı boyunca kaynakları KİLİTLİ tutmasını gerektirir
 * (blocking) ve coordinator çökerse katılımcılar süresiz kilitli kalabilir. Saga, ATOMİKLİK
 * İDDİA ETMEZ - her adım kendi başına commit eder (yerel transaction), başarısızlık
 * durumunda EVENTUAL CONSISTENCY'ye telafi edici (compensating) adımlarla ulaşılır. Saga
 * daha ölçeklenebilir (kilit yok) ama daha karmaşıktır (her adımın bir "geri alma" mantığı
 * olmalı, ve ara durumlar - ör. "sipariş oluşturuldu ama ödeme henüz alınmadı" - dışarıdan
 * GÖRÜNÜR olabilir).
 */
public class OrderSagaOrchestrator {

    /** Bir saga adımı: normal işlem + onu geri alan telafi edici (compensating) işlem. */
    public record SagaStep(String name, BooleanSupplier action, Runnable compensation) {
    }

    public record SagaResult(boolean success, List<String> executedSteps, List<String> compensatedSteps, String failedStep) {
    }

    public SagaResult run(List<SagaStep> steps) {
        List<String> executed = new ArrayList<>();
        List<String> compensated = new ArrayList<>();

        for (SagaStep step : steps) {
            boolean stepSucceeded = step.action().getAsBoolean();
            if (!stepSucceeded) {
                // Bu adım başarısız oldu - şimdiye kadar BAŞARIYLA TAMAMLANMIŞ adımları
                // TERS SIRADA telafi et (en son commit edilen, en önce geri alınmalı).
                for (int i = executed.size() - 1; i >= 0; i--) {
                    String executedStepName = executed.get(i);
                    steps.stream()
                            .filter(s -> s.name().equals(executedStepName))
                            .findFirst()
                            .ifPresent(s -> {
                                s.compensation().run();
                                compensated.add(s.name());
                            });
                }
                return new SagaResult(false, executed, compensated, step.name());
            }
            executed.add(step.name());
        }
        return new SagaResult(true, executed, compensated, null);
    }
}
