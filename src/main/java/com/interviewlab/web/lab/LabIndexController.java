package com.interviewlab.web.lab;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Postman/tarayıcı ile keşif için tek giriş noktası: hangi lab'ların çalıştırılabilir
 * olduğunu ve hangi endpoint'leri desteklediğini listeler. Tam adım-adım talimat için
 * docs/INTERACTIVE_LABS.md dosyasına bakın.
 */
@RestController
public class LabIndexController {

    @GetMapping("/api/labs")
    public Map<String, Object> index() {
        return Map.of(
                "labs", List.of(
                        lab("PERSISTENCE_CONTEXT", "/api/labs/persistence", List.of("reset", "bad", "good", "state")),
                        lab("TRANSACTION_ROLLBACK", "/api/labs/transaction", List.of("reset", "bad", "good", "state")),
                        lab("PROPAGATION", "/api/labs/propagation",
                                List.of("reset", "required", "requires-new/bad", "requires-new/good", "state")),
                        lab("ISOLATION", "/api/labs/isolation",
                                List.of("reset", "non-repeatable-read/read-committed", "non-repeatable-read/repeatable-read", "state")),
                        lab("OPTIMISTIC_LOCKING", "/api/labs/optimistic", List.of("reset", "bad", "good", "state")),
                        lab("PESSIMISTIC_LOCKING", "/api/labs/pessimistic", List.of("reset", "bad", "good", "state")),
                        lab("EXECUTOR_SERVICE", "/api/labs/executor", List.of("reset", "bad", "good", "caller-runs", "state")),
                        lab("COMPLETABLE_FUTURE", "/api/labs/completable-future", List.of("reset", "sequential", "parallel", "state")),
                        lab("SPRING_ASYNC", "/api/labs/async", List.of("reset", "bad", "good", "void-exception", "state")),
                        lab("THREAD_LOCAL", "/api/labs/threadlocal", List.of("reset", "bad", "good", "state")),
                        lab("BEAN_SCOPES", "/api/labs/scopes", List.of("reset", "singleton/bad", "singleton/good", "prototype/bad", "prototype/good", "lifecycle", "state")),
                        lab("SPRING_AOP", "/api/labs/aop", List.of("reset", "self-invocation/bad", "self-invocation/good", "self-invocation/state", "proxy-info")),
                        lab("EXCEPTIONS", "/api/labs/exceptions", List.of("reset", "swallowed", "lossy-rethrow", "good", "state")),
                        lab("N_PLUS_ONE", "/api/labs/n-plus-one", List.of("reset", "bad", "good", "state")),
                        lab("DESIGN_PATTERNS_STRATEGY", "/api/labs/patterns", List.of("strategy/bad", "strategy/good", "state")),
                        lab("RESILIENCE", "/api/labs/resilience", List.of("reset", "retry", "retry-exhausted",
                                "circuit-breaker", "rate-limiter", "bulkhead", "timeout", "fallback", "state")),
                        lab("SECURITY", "/api/labs/security", List.of("login", "protected", "admin-only", "state")),
                        lab("RACE_CONDITION", "/api/labs/concurrency/counter", List.of("reset", "bad", "good", "state")),
                        lab("LOCKING_PRIMITIVES", "/api/labs/concurrency", List.of("synchronized/bad", "synchronized/good",
                                "reentrant-lock/bad", "reentrant-lock/good", "read-write-lock/demo", "stamped-lock/demo", "state")),
                        lab("VOLATILE", "/api/labs/concurrency/volatile", List.of("misconception-check", "correct-usage"))
                ),
                "docs", "docs/INTERACTIVE_LABS.md",
                "postmanCollection", "postman/Java-Interview-Lab.postman_collection.json",
                "note", "Faz 1 + Faz 2 kapsamındaki interactive lab'ların listesidir. Kalan (saf "
                        + "concurrency primitifleri, collections, JVM, vb.) konular için PROJECT_STATUS.md "
                        + "ve TOPIC_MATRIX.md içindeki yol haritasına bakın - bazı konular kasıtlı olarak "
                        + "HTTP yerine deterministik test/CLI/debugger ile gösterilir."
        );
    }

    private static Map<String, Object> lab(String name, String basePath, List<String> actions) {
        return Map.of("name", name, "basePath", basePath, "actions", actions);
    }
}
