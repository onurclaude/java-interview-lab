package com.interviewlab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * "Java/Spring Interview Laboratuvarı" için giriş noktası.
 *
 * <p>Bu bir CRUD ürünü değildir. {@code com.interviewlab} altındaki her alt paket, bir
 * mülakat konusunun (transaction'lar, kilitleme, eşzamanlılık, Spring iç yapısı, tasarım
 * kalıpları) kendi başına yeterli bir gösterimidir; bilerek yanlış yazılmış bir {@code bad}
 * implementasyonun yanında, testlerle kanıtlanmış doğru bir {@code good} implementasyonu
 * bulunur - bu repository'nin nasıl kullanılacağı için modül README'sine ve {@code docs/}
 * dizinine bakın.
 */
@SpringBootApplication
public class InterviewLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterviewLabApplication.class, args);
    }
}
