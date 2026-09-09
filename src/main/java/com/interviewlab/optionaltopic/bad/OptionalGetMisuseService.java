package com.interviewlab.optionaltopic.bad;

import java.util.Map;
import java.util.Optional;

/**
 * NE YANLIŞ?
 * {@link #getUserEmail}, önce {@code isPresent()} kontrolü yapmadan bir {@link Optional}
 * üzerinde {@code .get()} çağırıyor.
 *
 * <p>NEDEN YANLIŞ?
 * {@code Optional.get()}, değer yoksa {@link java.util.NoSuchElementException} fırlatır -
 * bunu koşulsuz çağırmak, {@code Optional}'ın özellikle önlemeye yardımcı olmak için
 * getirildiği, null kontrolsüz {@code map.get(id).toUpperCase()} ile işlevsel olarak
 * aynıdır. Bir değeri {@code Optional} içine sarıp hemen ardından üzerinde {@code .get()}
 * çağırmak, hiçbir güvenlik kazancı olmadan bir allocation ve bir metot çağrısı ekler.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Eksik bir kullanıcı id'si, anlamlı ve kasıtlı bir hata yerine genel bir mesajla
 * ("No value present") checked olmayan bir {@code NoSuchElementException} fırlatır - düz
 * bir {@code null} kontrolünün vereceğinden daha kötü teşhis bilgisi.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code OptionalTest.shouldThrowNoSuchElementExceptionWhenGettingAbsentOptional()}, bunu
 * bilinmeyen bir id ile çağırır ve istisnayı gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * Anlamlı bir istisnayla {@code orElseThrow(...)}, ya da değeri sadece varken işlemek için
 * {@code map}/{@code flatMap}/{@code ifPresent} - bkz.
 * {@link com.interviewlab.optionaltopic.good.OptionalBestPracticesService}.
 */
public class OptionalGetMisuseService {

    private final Map<String, String> emailsByUserId;

    public OptionalGetMisuseService(Map<String, String> emailsByUserId) {
        this.emailsByUserId = emailsByUserId;
    }

    public String getUserEmail(String userId) {
        Optional<String> email = Optional.ofNullable(emailsByUserId.get(userId));
        return email.get(); // değer yoksa NoSuchElementException fırlatır
    }
}
