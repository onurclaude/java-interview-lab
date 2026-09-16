package com.interviewlab.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * `Authorization: Bearer <token>` header'ını okur, {@link JwtService} ile doğrular, geçerliyse
 * {@link SecurityContextHolder}'a bir {@code Authentication} yerleştirir. Token yoksa/geçersizse
 * SESSİZCE devam eder - bu isteğin yetkilendirilmiş olup olmadığına, request'in gerçekte hangi
 * endpoint'e gittiğine bağlı olarak SecurityConfig'deki yetkilendirme kuralları karar verir
 * (bazı path'ler zaten public, bu filter'ın hiç çalışması gerekmez).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Jws<Claims> parsed = jwtService.parse(token);
                Claims claims = parsed.getPayload();
                String username = claims.getSubject();
                @SuppressWarnings("unchecked")
                List<String> roles = claims.get("roles", List.class);
                List<GrantedAuthority> authorities = roles.stream()
                        .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException e) {
                // Geçersiz/süresi dolmuş token - SecurityContext boş kalır, aşağıdaki
                // yetkilendirme kuralları bunu "authenticated değil" olarak ele alır (401).
            }
        }
        filterChain.doFilter(request, response);
    }
}
