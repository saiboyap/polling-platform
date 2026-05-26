package com.polling.platform.aspect;

import com.polling.platform.annotation.AuditLogged;
import com.polling.platform.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogAspect {

    private final AuditLogService auditLogService;

    /**
     * Intercepts methods annotated with @AuditLogged.
     * The audit entry is written only on success — exceptions propagate unmodified.
     */
    @Around("@annotation(auditLogged)")
    public Object audit(ProceedingJoinPoint pjp, AuditLogged auditLogged) throws Throwable {
        Object result = pjp.proceed();

        try {
            auditLogService.record(
                    auditLogged.event(),
                    extractActor(),
                    auditLogged.entityType(),
                    extractEntityId(result, pjp.getArgs()),
                    extractIp()
            );
        } catch (Exception e) {
            log.warn("Audit aspect failed for event={}: {}", auditLogged.event(), e.getMessage());
        }

        return result;
    }

    private String extractActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "anonymous";
        }
        return auth.getName();
    }

    private String extractIp() {
        try {
            HttpServletRequest req =
                    ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            String xff = req.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(xff)) return xff.split(",")[0].trim();
            String xri = req.getHeader("X-Real-IP");
            if (StringUtils.hasText(xri)) return xri;
            return req.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** Tries getId() then getUsername() on the return value; falls back to the first UUID arg. */
    private String extractEntityId(Object result, Object[] args) {
        if (result != null) {
            for (String getter : List.of("getId", "getUsername")) {
                try {
                    Object id = result.getClass().getMethod(getter).invoke(result);
                    if (id != null) return id.toString();
                } catch (Exception ignored) {
                }
            }
        }
        for (Object arg : args) {
            if (arg instanceof UUID) return arg.toString();
        }
        return "";
    }
}
