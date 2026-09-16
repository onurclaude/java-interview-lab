package com.interviewlab.javacore;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewlab.javacore.switchdemo.PaymentStatus;
import com.interviewlab.javacore.switchdemo.SwitchStyles;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Modern Java 21 switch yazısı için docs/NOTES_CORRECTIONS.md dosyasına bakın. */
class SwitchStylesTest {

    @Test
    void shouldProduceSameResultAcrossAllSwitchStyles() {
        for (PaymentStatus status : PaymentStatus.values()) {
            String traditional = SwitchStyles.traditional(status);
            String arrow = SwitchStyles.withArrowSyntax(status);
            assertThat(traditional)
                    .as("aynı mantık, dört farklı söz dizimiyle - sonuç HER ZAMAN aynı olmalı: " + status)
                    .isEqualTo(arrow);
        }
    }

    @Test
    void shouldCoverEveryEnumValueWithoutADefaultBranch() {
        // exhaustiveWithoutDefault'ın DERLENMESİNİN KENDİSİ zaten kanıt: default olmadan,
        // sadece TÜM PaymentStatus değerleri kapsanıyorsa derlenir. Burada sadece
        // fonksiyonel doğruluğu kanıtlıyoruz.
        assertThat(Stream.of(PaymentStatus.values()).map(SwitchStyles::exhaustiveWithoutDefault).distinct().count())
                .as("4 farklı enum değeri, 4 farklı sonuç üretmeli - hepsi ayrı ayrı ele alındı")
                .isEqualTo(4);
    }

    @Test
    void shouldTreatCompletedFailedAndRefundedAsTerminal() {
        assertThat(SwitchStyles.isTerminalState(PaymentStatus.PENDING)).isFalse();
        assertThat(SwitchStyles.isTerminalState(PaymentStatus.COMPLETED)).isTrue();
        assertThat(SwitchStyles.isTerminalState(PaymentStatus.FAILED)).isTrue();
        assertThat(SwitchStyles.isTerminalState(PaymentStatus.REFUNDED)).isTrue();
    }
}
