package io.github.yanziki.enterpriseai.shared.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.identity")
public record IdentityProperties(
        @NotBlank String issuerUri, @NotBlank String jwkSetUri, @NotBlank String audience) {}
