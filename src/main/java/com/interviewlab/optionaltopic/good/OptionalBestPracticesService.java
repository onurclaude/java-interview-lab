package com.interviewlab.optionaltopic.good;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** {@code optionaltopic.bad.OptionalGetMisuseService}'in doğru karşılığı. */
public class OptionalBestPracticesService {

    private final Map<String, String> emailsByUserId;

    public OptionalBestPracticesService(Map<String, String> emailsByUserId) {
        this.emailsByUserId = emailsByUserId;
    }

    public String getUserEmailOrThrow(String userId) {
        return Optional.ofNullable(emailsByUserId.get(userId))
                .orElseThrow(() -> new NoSuchElementException("no email on file for user " + userId));
    }

    public String getUserEmailUpperCaseOrDefault(String userId) {
        return Optional.ofNullable(emailsByUserId.get(userId))
                .map(String::toUpperCase)
                .orElse("UNKNOWN@EXAMPLE.COM");
    }

    public void withUserEmailIfPresent(String userId, Consumer<String> action) {
        Optional.ofNullable(emailsByUserId.get(userId)).ifPresent(action);
    }

    /**
     * {@code orElse} ile {@code orElseGet} arasındaki seçim NEDEN önemli?
     * {@code orElse(x)}, zaten değerlendirilmiş bir DEĞER alır - Java, Optional'ın dolu olup
     * olmadığına bakılmaksızın, {@code orElse} çalışmadan önce bile argüman ifadesini istekli
     * (eager) bir şekilde değerlendirir. {@code orElseGet(supplier)} ise bir {@link Supplier}
     * alır ve onu yalnızca Optional gerçekten boşsa çağırır - tembel (lazy) değerlendirme.
     * Fallback'i üretmek pahalıysa (bir DB çağrısı, uzak bir istek) veya bir yan etkisi varsa,
     * {@code orElse} kullanmak, değer zaten mevcut olsa bile bu maliyeti HER çağrıda ödemek
     * (veya o yan etkiyi tetiklemek) anlamına gelir.
     */
    public String getUserEmailOrElseEager(String userId, Supplier<String> expensiveFallback) {
        return Optional.ofNullable(emailsByUserId.get(userId)).orElse(expensiveFallback.get());
    }

    public String getUserEmailOrElseGetLazy(String userId, Supplier<String> expensiveFallback) {
        return Optional.ofNullable(emailsByUserId.get(userId)).orElseGet(expensiveFallback);
    }
}
