package com.interviewlab.security;

import com.interviewlab.labrunner.spring.SpringLabRunnerSupport;

import com.interviewlab.labrunner.LabRunnerPrint;
import com.interviewlab.security.JwtService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

/**
 * IntelliJ'de sağ tık -> Run/Debug. Postman GEREKMEZ - ama Security'nin doğası (GERÇEK
 * {@code JwtAuthenticationFilter} + {@code SecurityFilterChain} + gerçek HTTP status kodu
 * 401/403/200) SADECE gerçek bir HTTP isteğiyle test edilebilir. Bu runner, {@code SpringLabRunnerSupport}'un
 * ZATEN başlattığı GERÇEK (rastgele portta) Tomcat'e, KENDİ İÇİNDEN, `java.net.http.HttpClient`
 * ile GERÇEK HTTP istekleri gönderir - hâlâ TEK bir main() çağrısı, Postman AÇILMASI GEREKMEZ.
 * `docker compose up -d` GEREKİR.
 */
public final class SecuritySpringLabRunner {

    public static void main(String[] args) throws Exception {
        SpringLabRunnerSupport.run(ctx -> {
            try {
                run(ctx);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void run(org.springframework.context.ConfigurableApplicationContext ctx) throws Exception {
        LabRunnerPrint.banner("SECURITY — JWT, 401 vs 403 (GERÇEK HTTP, aynı JVM içinde başlatılan Tomcat'e)");

        JwtService jwtService = ctx.getBean(JwtService.class);
        int port = ((ServletWebServerApplicationContext) ctx).getWebServer().getPort();
        String base = "http://localhost:" + port;

        String userToken = jwtService.issueToken("alice", List.of("USER")); // <- BREAKPOINT 1
        String adminToken = jwtService.issueToken("bob", List.of("ADMIN"));

        HttpClient client = HttpClient.newHttpClient();

        int noTokenStatus = send(client, base + "/api/labs/security/protected", null); // <- BREAKPOINT 2 (JwtAuthenticationFilter.doFilterInternal())
        LabRunnerPrint.fact("protected, token YOK (401 beklenir)", noTokenStatus);

        int userProtectedStatus = send(client, base + "/api/labs/security/protected", userToken);
        LabRunnerPrint.fact("protected, USER token (200 beklenir)", userProtectedStatus);

        int userAdminStatus = send(client, base + "/api/labs/security/admin-only", userToken); // <- BREAKPOINT 3: hasRole("ADMIN") filter'da reddeder
        LabRunnerPrint.fact("admin-only, USER token (403 beklenir)", userAdminStatus);

        int adminAdminStatus = send(client, base + "/api/labs/security/admin-only", adminToken);
        LabRunnerPrint.fact("admin-only, ADMIN token (200 beklenir)", adminAdminStatus);

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("JwtAuthenticationFilter, HER istekte çalışır - token yoksa/geçersizse controller'a HİÇ");
        LabRunnerPrint.line("ULAŞILMAZ (401 filter seviyesinde üretilir). USER token'la admin-only'ye gidersen,");
        LabRunnerPrint.line("SecurityConfig'deki hasRole(\"ADMIN\") kontrolü YİNE controller'a ULAŞMADAN 403 üretir -");
        LabRunnerPrint.line("401 (kimliksiz) ile 403 (kimlik biliniyor ama yetkisiz) GERÇEKTEN farklı katmanlardır.");
    }

    private static int send(HttpClient client, String url, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpResponse<Void> response = client.send(builder.build(), HttpResponse.BodyHandlers.discarding());
        return response.statusCode();
    }
}
