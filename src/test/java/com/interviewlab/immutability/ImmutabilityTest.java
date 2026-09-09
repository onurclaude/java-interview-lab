package com.interviewlab.immutability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.interviewlab.immutability.bad.MutableEventLog;
import com.interviewlab.immutability.good.DefensiveCopyEventLog;
import com.interviewlab.immutability.good.ImmutableMoney;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Yazı ve mülakat cevapları için docs/immutability.md dosyasına bakın. */
class ImmutabilityTest {

    @Test
    void shouldExposeInternalStateWithoutDefensiveCopy() {
        MutableEventLog log = new MutableEventLog();
        log.record("created");

        log.getEvents().add("INJECTED_FROM_OUTSIDE"); // çağıran, "kendi" listesi sandığı şeyi değiştiriyor

        assertThat(log.getEvents())
                .as("çağıranın yaptığı değişiklik doğrudan log'un iç durumuna sızdı")
                .contains("INJECTED_FROM_OUTSIDE");
    }

    @Test
    void shouldStillLeakChangesThroughAnUnmodifiableWrapperOfTheSameList() {
        List<String> backing = new ArrayList<>(List.of("a", "b"));
        List<String> unmodifiableView = Collections.unmodifiableList(backing);

        assertThatThrownBy(() -> unmodifiableView.add("c"))
                .as("wrapper'ın kendisi doğrudan değişikliği doğru şekilde reddediyor")
                .isInstanceOf(UnsupportedOperationException.class);

        backing.add("c"); // ama wrapper, alttaki listeye dokunmuyor

        assertThat(unmodifiableView)
                .as("Collections.unmodifiableList bir kopya değil, bir VIEW'dır - alttaki listedeki değişiklikler yine de yansır")
                .contains("c");
    }

    @Test
    void shouldNotLeakInternalStateWithDefensiveCopy() {
        DefensiveCopyEventLog log = new DefensiveCopyEventLog();
        log.record("created");

        List<String> events = log.getEvents();
        assertThatThrownBy(() -> events.add("INJECTED_FROM_OUTSIDE"))
                .as("List.copyOf() değiştirilemez bir kopya döndürür, bu yüzden değiştirme denemeleri hemen başarısız olur")
                .isInstanceOf(UnsupportedOperationException.class);

        log.record("second-event");
        assertThat(events)
                .as("daha önce alınan anlık görüntü, sonradan kaydedilen olayları da yansıtmamalı")
                .containsExactly("created");
    }

    @Test
    void shouldReturnNewInstanceInsteadOfMutatingOnAdd() {
        ImmutableMoney original = new ImmutableMoney(new BigDecimal("10.00"), "USD");
        ImmutableMoney result = original.add(new BigDecimal("5.00"));

        assertThat(result).isNotSameAs(original);
        assertThat(original.amount())
                .as("orijinal örnek, add() işleminden tamamen etkilenmeden kalmalı")
                .isEqualByComparingTo("10.00");
        assertThat(result.amount()).isEqualByComparingTo("15.00");
    }
}
