package com.interviewlab.scopes.prototype;

import java.util.UUID;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Container'dan her istendiğinde yeni bir instance - {@code id} bunu kanıtlar. */
@Component
@Scope("prototype")
public class PrototypeWorker {

    private final String id = UUID.randomUUID().toString();

    public String getId() {
        return id;
    }
}
