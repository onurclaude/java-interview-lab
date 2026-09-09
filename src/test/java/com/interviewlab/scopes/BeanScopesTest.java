package com.interviewlab.scopes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.interviewlab.AbstractPostgresIntegrationTest;
import com.interviewlab.scopes.prototype.bad.SingletonWithDirectPrototypeInjection;
import com.interviewlab.scopes.prototype.good.SingletonWithObjectProvider;
import com.interviewlab.scopes.prototype.good.SingletonWithScopedProxy;
import com.interviewlab.scopes.singleton.bad.MutableSingletonPriceService;
import com.interviewlab.scopes.singleton.good.StatelessPriceService;
import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/** Yazı ve mülakat cevapları için docs/bean-scopes.md dosyasına bakın. */
@AutoConfigureMockMvc
class BeanScopesTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MutableSingletonPriceService mutableSingletonPriceService;

    @Autowired
    private StatelessPriceService statelessPriceService;

    @Autowired
    private SingletonWithDirectPrototypeInjection singletonWithDirectPrototypeInjection;

    @Autowired
    private SingletonWithObjectProvider singletonWithObjectProvider;

    @Autowired
    private SingletonWithScopedProxy singletonWithScopedProxy;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCorruptResultWithMutableSingletonUnderConcurrency() throws InterruptedException {
        int callCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(16);
        AtomicInteger corrupted = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(callCount);

        for (int i = 1; i <= callCount; i++) {
            BigDecimal basePrice = BigDecimal.valueOf(i * 10);
            executor.submit(() -> {
                BigDecimal result = mutableSingletonPriceService.calculateDiscountedPrice(basePrice, BigDecimal.valueOf(0.1));
                BigDecimal expected = basePrice.subtract(basePrice.multiply(BigDecimal.valueOf(0.1)));
                if (result.compareTo(expected) != 0) {
                    corrupted.incrementAndGet();
                }
                done.countDown();
            });
        }
        done.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(corrupted.get())
                .as("singleton üzerindeki paylaşılan mutable durum, eşzamanlı çağrıların birbirinin sonucunu bozmasına izin vermeli")
                .isGreaterThan(0);
    }

    @Test
    void shouldNeverCorruptResultWithStatelessSingletonUnderConcurrency() throws InterruptedException {
        int callCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(16);
        AtomicInteger corrupted = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(callCount);

        for (int i = 1; i <= callCount; i++) {
            BigDecimal basePrice = BigDecimal.valueOf(i * 10);
            executor.submit(() -> {
                BigDecimal result = statelessPriceService.calculateDiscountedPrice(basePrice, BigDecimal.valueOf(0.1));
                BigDecimal expected = basePrice.subtract(basePrice.multiply(BigDecimal.valueOf(0.1)));
                if (result.compareTo(expected) != 0) {
                    corrupted.incrementAndGet();
                }
                done.countDown();
            });
        }
        done.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(corrupted.get()).isZero();
    }

    @Test
    void shouldReusePrototypeBeanWhenInjectedDirectlyIntoSingleton() {
        String first = singletonWithDirectPrototypeInjection.getWorkerId();
        String second = singletonWithDirectPrototypeInjection.getWorkerId();
        assertThat(first).isEqualTo(second);
    }

    @Test
    void shouldCreateNewPrototypeInstanceWithObjectProvider() {
        Set<String> ids = IntStream.range(0, 5)
                .mapToObj(i -> singletonWithObjectProvider.getWorkerId())
                .collect(Collectors.toSet());
        assertThat(ids).as("her çağrı farklı bir prototype instance üretmeli").hasSize(5);
    }

    @Test
    void shouldCreateNewPrototypeInstanceWithScopedProxy() {
        Set<String> ids = IntStream.range(0, 5)
                .mapToObj(i -> singletonWithScopedProxy.getWorkerId())
                .collect(Collectors.toSet());
        assertThat(ids).as("scoped proxy, her çağrıda şeffaf bir şekilde yeni bir instance getirmeli").hasSize(5);
    }

    @Test
    void shouldReturnDifferentRequestScopedIdAcrossSeparateRequests() throws Exception {
        String idFromRequest1 = extractJsonValue(mockMvc.perform(get("/lab/scopes/request")).andReturn().getResponse().getContentAsString(), "firstRead");
        String idFromRequest2 = extractJsonValue(mockMvc.perform(get("/lab/scopes/request")).andReturn().getResponse().getContentAsString(), "firstRead");
        assertThat(idFromRequest1).as("iki ayrı HTTP isteği, iki farklı request-scoped instance almalı").isNotEqualTo(idFromRequest2);
    }

    @Test
    void shouldReturnSameIdWithinOneRequestForRequestScopedBean() throws Exception {
        String body = mockMvc.perform(get("/lab/scopes/request")).andReturn().getResponse().getContentAsString();
        String firstRead = extractJsonValue(body, "firstRead");
        String secondRead = extractJsonValue(body, "secondReadSameRequest");
        assertThat(firstRead)
                .as("aynı istek İÇİNDE request-scoped bean'in iki okunuşu, aynı instance'ı görmeli")
                .isEqualTo(secondRead);
    }

    @Test
    void shouldShareSessionScopedIdAcrossRequestsInSameSessionButNotAcrossSessions() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String firstBody = mockMvc.perform(get("/lab/scopes/session").session(session)).andReturn().getResponse().getContentAsString();
        String secondBody = mockMvc.perform(get("/lab/scopes/session").session(session)).andReturn().getResponse().getContentAsString();
        String idInNewSession = extractJsonValue(
                mockMvc.perform(get("/lab/scopes/session").session(new MockHttpSession())).andReturn().getResponse().getContentAsString(),
                "sessionScopedId");

        String id1 = extractJsonValue(firstBody, "sessionScopedId");
        String id2 = extractJsonValue(secondBody, "sessionScopedId");

        assertThat(id1).as("aynı session, istekler boyunca aynı session-scoped instance'ı görmeli").isEqualTo(id2);
        assertThat(id1).as("farklı bir session, farklı bir session-scoped instance almalı").isNotEqualTo(idInNewSession);
    }

    @Test
    void shouldShareApplicationScopedIdAcrossRequests() throws Exception {
        String id1 = extractJsonValue(mockMvc.perform(get("/lab/scopes/application")).andReturn().getResponse().getContentAsString(), "applicationScopedId");
        String id2 = extractJsonValue(mockMvc.perform(get("/lab/scopes/application")).andReturn().getResponse().getContentAsString(), "applicationScopedId");
        assertThat(id1).isEqualTo(id2);
    }

    private static String extractJsonValue(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker) + marker.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
