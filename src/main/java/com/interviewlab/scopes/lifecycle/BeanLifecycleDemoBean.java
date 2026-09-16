package com.interviewlab.scopes.lifecycle;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/**
 * Spring bean lifecycle'ının gerçek sırasını gözlemlenebilir kılar: constructor (+ constructor
 * injection) → {@link BeanLifecycleLoggingPostProcessor} (BeanPostProcessor, before/after) →
 * {@code @PostConstruct} → ... uygulama çalışırken hazır ... → {@code @PreDestroy} (context
 * kapanırken - bu projede bunu tetiklemek için uygulamayı durdurmak gerekir, bu yüzden bu son
 * adım yalnızca konsol logunda görülebilir, HTTP response'unda değil).
 */
@Component
public class BeanLifecycleDemoBean {

    private static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    public BeanLifecycleDemoBean() {
        EVENTS.add("1_CONSTRUCTOR");
    }

    @PostConstruct
    void afterPropertiesSet() {
        EVENTS.add("3_POST_CONSTRUCT");
    }

    @PreDestroy
    void onDestroy() {
        // Bu yalnızca ApplicationContext kapanırken (Ctrl+C / graceful shutdown) çalışır -
        // konsolda görülebilir, ama context zaten kapanmakta olduğu için bir HTTP response'la
        // gözlemlenemez.
        EVENTS.add("4_PRE_DESTROY");
    }

    public static List<String> events() {
        return List.copyOf(EVENTS);
    }

    static void recordBeanPostProcessorEvent(String event) {
        EVENTS.add(event);
    }
}
