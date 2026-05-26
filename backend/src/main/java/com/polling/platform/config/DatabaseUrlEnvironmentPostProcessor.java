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
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String host     = System.getenv("PGHOST");
        String port     = System.getenv("PGPORT");
        String database = System.getenv("PGDATABASE");
        String user     = System.getenv("PGUSER");
        String password = System.getenv("PGPASSWORD");

        if (host == null || database == null || user == null || password == null) {
            return;
        }

        String resolvedPort = (port != null && !port.isBlank()) ? port : "5432";
        String jdbcUrl = String.format(
                "jdbc:postgresql://%s:%s/%s?sslmode=require", host, resolvedPort, database);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("spring.datasource.url", jdbcUrl);
        props.put("spring.datasource.username", user);
        props.put("spring.datasource.password", password);

        environment.getPropertySources()
                .addFirst(new MapPropertySource("railwayDatasource", props));
    }
}
