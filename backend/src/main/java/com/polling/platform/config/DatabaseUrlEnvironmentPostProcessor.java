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
        Map<String, Object> props = buildDatasourceProps();
        if (props == null) {
            return;
        }
        environment.getPropertySources()
                .addFirst(new MapPropertySource("railwayDatasource", props));
    }

    private Map<String, Object> buildDatasourceProps() {
        String rawUrl = System.getenv("DATABASE_URL");

        if (rawUrl != null && !rawUrl.isBlank()) {
            String jdbcUrl = toJdbcUrl(rawUrl);
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("spring.datasource.url", jdbcUrl);
            // Credentials are embedded in the URL; suppress separate username/password.
            props.put("spring.datasource.username", "");
            props.put("spring.datasource.password", "");
            return props;
        }

        // Fallback: Railway also exposes individual PG* variables.
        String host     = System.getenv("PGHOST");
        String port     = System.getenv("PGPORT");
        String database = System.getenv("PGDATABASE");
        String user     = System.getenv("PGUSER");
        String password = System.getenv("PGPASSWORD");

        if (host != null && database != null && user != null && password != null) {
            String resolvedPort = (port != null && !port.isBlank()) ? port : "5432";
            String jdbcUrl = String.format(
                    "jdbc:postgresql://%s:%s/%s?sslmode=require", host, resolvedPort, database);
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("spring.datasource.url", jdbcUrl);
            props.put("spring.datasource.username", user);
            props.put("spring.datasource.password", password);
            return props;
        }

        return null;
    }

    private String toJdbcUrl(String url) {
        if (url.startsWith("postgresql://") || url.startsWith("postgres://")) {
            url = "jdbc:" + url;
        }

        if (!url.contains("sslmode=")) {
            url = url.contains("?") ? url + "&sslmode=require" : url + "?sslmode=require";
        }

        return url;
    }
}
