package com.interviewlab.scopes.webscopes;

import java.util.UUID;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

/** {@code HttpSession} başına bir instance: aynı tarayıcı/istemci birçok istek boyunca aynı id'yi görür. */
@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class SessionScopedIdHolder {

    private final String id = UUID.randomUUID().toString();

    public String getId() {
        return id;
    }
}
