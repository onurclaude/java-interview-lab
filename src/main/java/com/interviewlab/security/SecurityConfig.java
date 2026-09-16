package com.interviewlab.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Bu proje bilinçli olarak yalnızca YENİ {@code /api/labs/security/**} path'lerini korur -
 * projenin GERİ KALAN TÜM lab'ları ({@code /api/labs/persistence}, {@code /api/labs/optimistic},
 * vb.) kasıtlı olarak public kalır, aksi halde bu ekleme Faz 1/2/3'te inşa edilen 15
 * interactive lab'ı BOZARDI (hepsi auth gerektirmeden çalışacak şekilde tasarlandı). Gerçek
 * bir production uygulamasında TÜM endpoint'ler varsayılan olarak KORUNUR, sadece
 * gerçekten public olması gerekenler (ör. `/login`) açıkça izin verilir - bu projenin
 * tersi bir yaklaşım izlemesinin TEK nedeni, mevcut lab'ları bozmamaktır.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/labs/security/login", "/api/labs/security/state").permitAll()
                        .requestMatchers("/api/labs/security/admin-only").hasRole("ADMIN")
                        .requestMatchers("/api/labs/security/protected").authenticated()
                        .anyRequest().permitAll() // bkz. sınıf javadoc'u - projenin geri kalanı korunmuyor
                )
                .exceptionHandling(ex -> ex
                        // Varsayılan davranış (formLogin/httpBasic olmadan) her ikisi için de
                        // aynı, belirsiz bir 403 döndürebilir - 401 ile 403'ü GERÇEKTEN ayırmak
                        // için (bkz. docs/NOTES_CORRECTIONS.md #8) ikisini AÇIKÇA ayrı ayrı ele alıyoruz.
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(401, "Unauthorized: geçerli bir Bearer token gerekli"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                response.sendError(403, "Forbidden: kimliğin biliniyor ama bu kaynağa yetkin yok")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
