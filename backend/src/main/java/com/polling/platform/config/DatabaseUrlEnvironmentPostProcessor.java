package com.polling.platform.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the JDBC URL from Railway's individual PG* variables at startup,
 * before the datasource bean is created. Takes priority over application.yml.
 * Falls back silently when no PG* vars are set (local dev uses YAML defaults).
 *
 * Any exception is caught and printed to stderr — the processor never throws,
 * so a bad env var cannot abort SpringApplication.prepareEnvironment.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            String host     = env("PGHOST");
            String port     = env("PGPORT");
            String database = env("PGDATABASE");
            String user     = env("PGUSER");
            String password = env("PGPASSWORD");

            if (host == null || database == null || user == null || password == null) {
                // No Railway PG vars present — local dev will use application.yml defaults.
                return;
            }

            String resolvedPort = (port != null && !port.isEmpty()) ? port : "5432";
            String jdbcUrl = "jdbc:postgresql://" + host + ":" + resolvedPort + "/" + database + "?sslmode=require";

            Map<String, Object> props = new LinkedHashMap<>();
            props.put("spring.datasource.url", jdbcUrl);
            props.put("spring.datasource.username", user);
            props.put("spring.datasource.password", password);

            environment.getPropertySources()
                    .addFirst(new MapPropertySource("railwayDatasource", props));

            System.out.println("[DatabaseUrlPostProcessor] Datasource configured from PG* env vars: " + jdbcUrl);

        } catch (Exception e) {
            // Never let this processor crash prepareEnvironment — log and continue.
            System.err.println("[DatabaseUrlPostProcessor] Failed to configure datasource from env vars: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
