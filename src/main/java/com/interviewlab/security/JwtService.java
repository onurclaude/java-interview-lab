package com.interviewlab.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Gerçek bir JWT üretici/doğrulayıcı - bkz. docs/http-and-security.md. Anahtar, uygulama
 * her başladığında YENİDEN üretilir (production'da bir env var'dan/secret store'dan
 * okunurdu, kodda sabit olmazdı) - bu proje için önemli olan gerçek imzalama/doğrulama
 * mekanizmasının kendisi.
 */
@Component
public class JwtService {

    private static final long EXPIRATION_MILLIS = 15 * 60 * 1000;

    private final SecretKey key = Jwts.SIG.HS256.key().build();

    public String issueToken(String username, List<String> roles) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + EXPIRATION_MILLIS))
                .signWith(key)
                .compact();
    }

    /** Geçersiz imza/süresi dolmuş token için io.jsonwebtoken.JwtException fırlatır. */
    public Jws<Claims> parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
    }
}
