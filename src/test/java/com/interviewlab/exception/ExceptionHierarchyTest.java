package com.interviewlab.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.interviewlab.AbstractPostgresIntegrationTest;
import com.interviewlab.exception.bad.LossyRethrowService;
import com.interviewlab.exception.bad.SwallowingExceptionService;
import com.interviewlab.exception.good.WrappingExceptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Yazı ve mülakat cevapları için docs/exceptions.md dosyasına bakın. */
@AutoConfigureMockMvc
class ExceptionHierarchyTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private SwallowingExceptionService swallowingExceptionService;

    @Autowired
    private LossyRethrowService lossyRethrowService;

    @Autowired
    private WrappingExceptionService wrappingExceptionService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldSilentlySwallowFailureAndReturnNormally() {
        boolean reportedSuccess = swallowingExceptionService.chargeCardSilently("tok_123", 50.0);
        assertThat(reportedSuccess)
                .as("gateway aslında başarısız oldu, ama caller'ın bunu öğrenmesinin bir yolu yok")
                .isTrue();
    }

    @Test
    void shouldLoseOriginalCauseWithLossyRethrow() {
        assertThatThrownBy(() -> lossyRethrowService.chargeCardLosingCause("tok_123", 50.0))
                .isInstanceOf(RuntimeException.class)
                .satisfies(ex -> assertThat(ex.getCause())
                        .as("orijinal PaymentGatewayCheckedException atılmış olmalıdır")
                        .isNull());
    }

    @Test
    void shouldPreserveCauseChainAndProduceCorrectHierarchyWithWrapping() {
        assertThatThrownBy(() -> wrappingExceptionService.chargeCard("tok_123", 50.0))
                .isInstanceOf(InsufficientBalanceException.class)
                .isInstanceOf(PaymentException.class)
                .isInstanceOf(BusinessException.class)
                .isInstanceOf(RuntimeException.class)
                .satisfies(ex -> assertThat(ex.getCause())
                        .as("orijinal gateway exception'ı hâlâ getCause() aracılığıyla erişilebilir olmalıdır")
                        .isInstanceOf(PaymentGatewayCheckedException.class));
    }

    @Test
    void shouldMapInsufficientBalanceExceptionToHttp402ViaControllerAdvice() throws Exception {
        mockMvc.perform(post("/lab/exceptions/charge").param("cardToken", "tok_123").param("amount", "50.0"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_BALANCE"));
    }
}
