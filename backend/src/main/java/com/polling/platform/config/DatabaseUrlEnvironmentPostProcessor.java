package com.polling.platform.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String rawUrl = System.getenv("DATABASE_URL");
        if (rawUrl == null || rawUrl.isBlank()) {
            return;
        }

        String jdbcUrl = toJdbcUrl(rawUrl);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("spring.datasource.url", jdbcUrl);
        // Credentials are embedded in the URL; clear any accidentally inherited values.
        props.put("spring.datasource.username", "");
        props.put("spring.datasource.password", "");

        environment.getPropertySources()
                .addFirst(new MapPropertySource("railwayDatabaseUrl", props));
    }

    private String toJdbcUrl(String url) {
        // Add jdbc: prefix for postgresql:// or postgres:// schemes
        if (url.startsWith("postgresql://") || url.startsWith("postgres://")) {
            url = "jdbc:" + url;
        }

        // Append sslmode=require if not already present
        if (!url.contains("sslmode=")) {
            url = url.contains("?") ? url + "&sslmode=require" : url + "?sslmode=require";
        }

        return url;
    }
}
