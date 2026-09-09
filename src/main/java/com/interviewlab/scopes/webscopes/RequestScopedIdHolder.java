package com.interviewlab.scopes.webscopes;

import java.util.UUID;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

/**
 * HTTP request başına bir instance: AYNI request sırasında bu bean'e bir referansla inject
 * edilen her collaborator aynı {@code id}'yi görür; bir sonraki request yeni bir instance
 * alır (ve dolayısıyla yeni bir id). Bunu singleton bean'lere (bir {@code @RestController}
 * gibi) inject edebilmek için {@code proxyMode = TARGET_CLASS} gereklidir - bu olmadan,
 * singleton, henüz hiçbir HTTP request'in olmadığı container başlangıç anında gerçek bir
 * instance çözmeye çalışır ve başlatma başarısız olur.
 */
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestScopedIdHolder {

    private final String id = UUID.randomUUID().toString();

    public String getId() {
        return id;
    }
}
