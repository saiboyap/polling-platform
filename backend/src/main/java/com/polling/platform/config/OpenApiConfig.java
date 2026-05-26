package com.polling.platform.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Polling Platform API",
                version = "v1.0",
                description = "Event-driven real-time polling platform. " +
                        "Authenticate via POST /api/auth/login and pass the returned token as a Bearer header.",
                contact = @Contact(name = "Platform Team")
        ),
        servers = @Server(url = "/", description = "Default server")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT obtained from POST /api/auth/login"
)
public class OpenApiConfig {
}
