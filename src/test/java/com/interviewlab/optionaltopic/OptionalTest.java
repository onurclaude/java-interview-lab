package com.interviewlab.optionaltopic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.interviewlab.optionaltopic.bad.OptionalGetMisuseService;
import com.interviewlab.optionaltopic.good.OptionalBestPracticesService;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Optional yazısı için docs mülakat materyaline bakın. */
class OptionalTest {

    private final Map<String, String> emails = Map.of("u1", "ada@example.com");

    @Test
    void shouldThrowNoSuchElementExceptionWhenGettingAbsentOptional() {
        OptionalGetMisuseService service = new OptionalGetMisuseService(emails);
        assertThatThrownBy(() -> service.getUserEmail("unknown-user"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void shouldThrowMeaningfulExceptionWithOrElseThrow() {
        OptionalBestPracticesService service = new OptionalBestPracticesService(emails);
        assertThatThrownBy(() -> service.getUserEmailOrThrow("unknown-user"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("unknown-user");
    }

    @Test
    void shouldMapAndProvideDefaultWithoutThrowing() {
        OptionalBestPracticesService service = new OptionalBestPracticesService(emails);
        assertThat(service.getUserEmailUpperCaseOrDefault("u1")).isEqualTo("ADA@EXAMPLE.COM");
        assertThat(service.getUserEmailUpperCaseOrDefault("unknown")).isEqualTo("UNKNOWN@EXAMPLE.COM");
    }

    @Test
    void shouldAlwaysEvaluateFallbackSupplierWithOrElseEvenWhenValuePresent() {
        OptionalBestPracticesService service = new OptionalBestPracticesService(emails);
        AtomicInteger fallbackCalls = new AtomicInteger();

        service.getUserEmailOrElseEager("u1", () -> {
            fallbackCalls.incrementAndGet();
            return "fallback@example.com";
        });

        assertThat(fallbackCalls.get())
                .as("değer zaten mevcut olsa bile, orElse'in argümanı eagerly (istekli) olarak değerlendirilir")
                .isEqualTo(1);
    }

    @Test
    void shouldNotEvaluateFallbackSupplierWithOrElseGetWhenValuePresent() {
        OptionalBestPracticesService service = new OptionalBestPracticesService(emails);
        AtomicInteger fallbackCalls = new AtomicInteger();

        service.getUserEmailOrElseGetLazy("u1", () -> {
            fallbackCalls.incrementAndGet();
            return "fallback@example.com";
        });

        assertThat(fallbackCalls.get())
                .as("orElseGet, supplier'ı yalnızca değer gerçekten yoksa çağırır")
                .isZero();
    }
}
