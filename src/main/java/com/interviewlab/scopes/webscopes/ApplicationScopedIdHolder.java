package com.interviewlab.scopes.webscopes;

import java.util.UUID;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

/**
 * {@code ServletContext} başına bir instance, bean factory'nin kendi singleton önbelleğinde
 * değil, bir {@code ServletContext} attribute'u olarak saklanır. Tipik bir tek-{@code
 * ApplicationContext}'li Spring Boot uygulamasında bu, sıradan bir singleton ile gözlemsel
 * olarak aynıdır - ayrım yalnızca birden fazla Spring {@code ApplicationContext}'in tek bir
 * {@code ServletContext}'i paylaştığı durumlarda önem kazanır (klasik olarak, bir Spring MVC
 * child context artı bir root context): sıradan bir singleton her context'e kendi instance'ını
 * verirdi, oysa application-scoped bir bean, altta yatan tek {@code ServletContext} üzerinden
 * hepsi arasında paylaşılır. Spring Boot'un tipik tek-context kurulumu bu ayrıma nadiren
 * ihtiyaç duyar, bu yüzden "application" scope, beş scope arasında en az kullanılanıdır.
 */
@Component
@Scope(WebApplicationContext.SCOPE_APPLICATION)
public class ApplicationScopedIdHolder {

    private final String id = UUID.randomUUID().toString();

    public String getId() {
        return id;
    }
}
