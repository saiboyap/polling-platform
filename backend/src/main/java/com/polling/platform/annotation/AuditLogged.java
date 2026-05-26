package com.polling.platform.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method for asynchronous audit logging.
 * The aspect captures actor, IP, and entity ID automatically.
 * Audit is only written on successful method completion.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLogged {

    /** Audit event name, e.g. "POLL_CREATED", "VOTE_CAST". */
    String event();

    /** Domain entity type affected, e.g. "POLL", "USER". */
    String entityType() default "";
}
