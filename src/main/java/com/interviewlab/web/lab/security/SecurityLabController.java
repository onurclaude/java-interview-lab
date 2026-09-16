package com.interviewlab.web.lab.security;

import com.interviewlab.security.JwtService;
import com.interviewlab.web.lab.LabLog;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/INTERACTIVE_LABS.md — LAB 17, docs/http-and-security.md. JWT'nin ŞİFRELİ olmadığını
 * (payload'ı herkes decode edip okuyabilir), ve 401 (kimliksiz) ile 403'ün (kimlik biliniyor
 * ama yetki yok) GERÇEKTEN farklı HTTP status kodları olarak nasıl ayrıldığını gösterir.
 *
 * <p><b>Bu lab'ı Postman'de denemek için:</b> önce {@code POST /login}'i çağır, dönen
 * {@code token}'ı kopyala, sonra {@code GET /protected} ve {@code GET /admin-only}
 * isteklerine {@code Authorization: Bearer <token>} header'ı ekleyerek çağır.
 */
@RestController
@RequestMapping("/api/labs/security")
public class SecurityLabController {

    private final JwtService jwtService;

    public SecurityLabController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestParam String username, @RequestParam(defaultValue = "USER") String role) {
        LabLog.banner("SECURITY / JWT", "LOGIN");
        String token = jwtService.issueToken(username, List.of(role));

        String[] parts = token.split("\\.");
        String decodedHeader = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String decodedPayload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);

        LabLog.line("Token üretildi, username={}, role={}", username, role);
        LabLog.lesson("Token'ın header+payload kısımları SADECE Base64URL ile ENCODE edilmiş - ŞİFRELİ DEĞİL. "
                + "Aşağıdaki decodedPayload alanı, SECRET KEY OLMADAN, sadece base64 decode ile elde edildi - "
                + "bu yüzden JWT payload'ına asla şifre/API key gibi gizli veri konulmamalı.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "SECURITY");
        body.put("mode", "LOGIN");
        body.put("token", token);
        body.put("decodedHeaderWithoutSecret", decodedHeader);
        body.put("decodedPayloadWithoutSecret", decodedPayload);
        body.put("lesson", "JWT şifreli DEĞİLDİR, sadece imzalıdır - payload'ı herkes secret olmadan okuyabilir.");
        body.put("nextStep", "Authorization: Bearer " + token + " header'ı ile GET /api/labs/security/protected çağır");
        return body;
    }

    @GetMapping("/protected")
    public Map<String, Object> protectedEndpoint(Authentication authentication) {
        LabLog.banner("SECURITY / JWT", "PROTECTED (authenticated - herhangi bir rol yeterli)");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "SECURITY");
        body.put("mode", "PROTECTED");
        body.put("authenticatedAs", authentication.getName());
        body.put("authorities", authentication.getAuthorities().stream().map(Object::toString).toList());
        body.put("lesson", "Bu endpoint sadece GEÇERLİ bir token istiyor, belirli bir rol değil. Token olmadan çağırırsan 401 alırsın.");
        return body;
    }

    @GetMapping("/admin-only")
    public Map<String, Object> adminOnly(Authentication authentication) {
        LabLog.banner("SECURITY / JWT", "ADMIN-ONLY (ROLE_ADMIN gerekli)");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "SECURITY");
        body.put("mode", "ADMIN_ONLY");
        body.put("authenticatedAs", authentication.getName());
        body.put("lesson", "Bu endpoint'e ulaştıysan ROLE_ADMIN'in var demektir - USER rolüyle çağırırsan (geçerli "
                + "token olsa bile) 403 alırsın: kimliğin biliniyor ama yetkin yok.");
        return body;
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "SECURITY");
        body.put("note", "POST /login?username=ada&role=USER (401 senaryosu için) veya "
                + "?username=ada&role=ADMIN (admin-only'yi geçmek için) ile başla.");
        return body;
    }
}
