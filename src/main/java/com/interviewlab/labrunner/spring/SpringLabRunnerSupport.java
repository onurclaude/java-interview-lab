package com.interviewlab.labrunner.spring;

import com.interviewlab.InterviewLabApplication;
import java.util.function.Consumer;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Kategori A (Spring'e bağımlı) konuları için, Postman/HTTP'ye HİÇ gerek kalmadan, IntelliJ'de
 * doğrudan {@code main()} ile Run/Debug edilebilen runner'ların ortak altyapısı.
 *
 * <p>TAM gerçek bir Spring {@code ApplicationContext} başlatılır (gerçek CGLIB proxy'ler,
 * gerçek {@code @Transactional}/{@code @Aspect}/{@code @Async} davranışı, gerçek Postgres
 * bağlantısı, gerçek {@code request}/{@code session}/{@code application} web scope'ları).
 * Bean'ler {@code ctx.getBean(...)} ile DOĞRUDAN Java çağrısıyla kullanılır - HTTP/Jackson
 * serialization katmanı tamamen atlanır, debugger'da doğrudan gerçek nesneleri incelersin.
 *
 * <p><b>NEDEN {@code WebApplicationType.NONE} DEĞİL:</b> bizzat denenip GERÇEK bir hata ile
 * keşfedildi - {@code request}/{@code session}/{@code application} scope'lu bean'ler
 * (ör. {@code ScopesController}), bu scope'ları register eden gerçek bir Servlet web
 * container'ı OLMADAN {@code IllegalStateException: No Scope registered for scope name
 * 'application'} ile context başlatmayı BAŞARISIZ kılıyordu. Bunun yerine {@code server.port=0}
 * (rastgele, boş bir port) ile GERÇEK bir Tomcat başlatılır - context TAM VE DOĞRU şekilde
 * kurulur, ama sabit `:8082` ile HİÇBİR ZAMAN çakışmaz (runner zaten hiçbir HTTP çağrısı
 * yapmaz, sadece {@code ctx.getBean(...)} kullanır).
 *
 * <p><b>ÖN KOŞUL:</b> `docker compose up -d` ile Postgres (`:5434`) ayakta olmalı.
 */
public final class SpringLabRunnerSupport {

    private SpringLabRunnerSupport() {
    }

    public static void run(Consumer<ConfigurableApplicationContext> body) {
        // System property olarak set ediliyor: application.yml'deki "server.port: 8082"
        // sabit değerinden DAHA YÜKSEK önceliğe sahip olması için (SpringApplicationBuilder
        // .properties(...) - denendi, application.yml'den DAHA DÜŞÜK öncelikli çıktı, port
        // 8082'ye bağlanmayı denedi ve zaten çalışan HTTP uygulamasıyla ÇAKIŞTI - bu system
        // property yaklaşımıyla düzeltildi).
        System.setProperty("server.port", "0");
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(InterviewLabApplication.class)
                .web(WebApplicationType.SERVLET)
                .run();
        try {
            body.accept(ctx);
        } finally {
            ctx.close();
        }
    }
}
