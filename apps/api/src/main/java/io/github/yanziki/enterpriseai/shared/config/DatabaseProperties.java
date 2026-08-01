package io.github.yanziki.enterpriseai.shared.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.database")
public record DatabaseProperties(
        @NotBlank(message = "DB_URL must be configured")
                @Pattern(
                        regexp = "^jdbc:postgresql://.+",
                        message = "DB_URL must be a PostgreSQL JDBC URL")
                String url,
        @NotBlank(message = "DB_USERNAME must be configured") String username,
        @NotBlank(message = "DB_PASSWORD must be configured") String password) {}
