package com.polling.platform.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configures the datasource URL from Railway's PG* environment variables before
 * any bean is created. This processor MUST NEVER throw — a crash here aborts
 * SpringApplication.prepareEnvironment before logging is available.
 *
 * Catches Throwable (not just Exception) to survive Error subclasses such as
 * NoClassDefFoundError or OutOfMemoryError on constrained Railway containers.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            applyDatasourceConfig(environment);
        } catch (Throwable t) {
            // Intentionally broad — nothing thrown from here must reach prepareEnvironment.
            try {
                System.err.println("[DatabaseUrlPostProcessor] FATAL: unexpected error during env post-processing: "
                        + t.getClass().getName() + ": " + t.getMessage());
                t.printStackTrace(System.err);
            } catch (Throwable ignored) {
                // Even stderr output can fail (e.g. stream closed) — swallow everything.
            }
        }
    }

    private void applyDatasourceConfig(ConfigurableEnvironment environment) {
        String host     = safeEnv("PGHOST");
        String port     = safeEnv("PGPORT");
        String database = safeEnv("PGDATABASE");
        String user     = safeEnv("PGUSER");
        String password = safeEnv("PGPASSWORD");

        if (host == null || database == null || user == null || password == null) {
            // PG* vars absent — local dev falls through to application.yml defaults.
            System.out.println("[DatabaseUrlPostProcessor] PG* env vars not set, using application.yml datasource defaults.");
            return;
        }

        String resolvedPort = (port != null && !port.isEmpty()) ? port : "5432";
        String jdbcUrl = "jdbc:postgresql://" + host + ":" + resolvedPort + "/" + database + "?sslmode=require";

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("spring.datasource.url", jdbcUrl);
        props.put("spring.datasource.username", user);
        props.put("spring.datasource.password", password);

        environment.getPropertySources().addFirst(new MapPropertySource("railwayDatasource", props));

        System.out.println("[DatabaseUrlPostProcessor] Datasource configured: "
                + "jdbc:postgresql://" + host + ":" + resolvedPort + "/" + database + "?sslmode=require");
    }

    /**
     * Reads an env var, trims whitespace, and returns null for absent or blank values.
     * Catches SecurityException in case a security manager blocks env access.
     */
    private static String safeEnv(String name) {
        try {
            String value = System.getenv(name);
            if (value == null) return null;
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        } catch (Throwable t) {
            System.err.println("[DatabaseUrlPostProcessor] Could not read env var " + name + ": " + t.getMessage());
            return null;
        }
    }
}
