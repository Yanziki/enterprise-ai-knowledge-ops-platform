package io.github.yanziki.enterpriseai.shared.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfiguration {

    private static final Set<String> PLATFORM_ROLES =
            Set.of("PLATFORM_ADMIN", "TENANT_ADMIN", "MEMBER", "AUDITOR");

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        return http.sessionManagement(
                        sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(
                                                new BearerTokenAuthenticationEntryPoint())
                                        .accessDeniedHandler(new BearerTokenAccessDeniedHandler()))
                .oauth2ResourceServer(
                        resourceServer ->
                                resourceServer
                                        .jwt(
                                                jwt ->
                                                        jwt.jwtAuthenticationConverter(
                                                                jwtAuthenticationConverter))
                                        .authenticationEntryPoint(
                                                new BearerTokenAuthenticationEntryPoint())
                                        .accessDeniedHandler(new BearerTokenAccessDeniedHandler()))
                .authorizeHttpRequests(
                        requests ->
                                requests.requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/system/status",
                                                "/actuator/health",
                                                "/actuator/health/liveness",
                                                "/actuator/health/readiness")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/api/v1/me")
                                        .authenticated()
                                        .requestMatchers(
                                                HttpMethod.GET, "/api/v1/admin/system-summary")
                                        .hasRole("PLATFORM_ADMIN")
                                        .requestMatchers(
                                                HttpMethod.GET, "/api/v1/organizations/*/summary")
                                        .authenticated()
                                        .requestMatchers(
                                                "/api/v1/organizations/*/workspaces/*/documents",
                                                "/api/v1/organizations/*/workspaces/*/documents/**",
                                                "/api/v1/organizations/*/workspaces/*/retrieval",
                                                "/api/v1/organizations/*/workspaces/*/retrieval/**",
                                                "/api/v1/organizations/*/workspaces/*/answers")
                                        .authenticated()
                                        .anyRequest()
                                        .denyAll())
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(IdentityProperties identityProperties) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withJwkSetUri(identityProperties.jwkSetUri()).build();
        OAuth2TokenValidator<Jwt> issuerValidator =
                JwtValidators.createDefaultWithIssuer(identityProperties.issuerUri());
        OAuth2TokenValidator<Jwt> audienceValidator =
                new JwtAudienceValidator(identityProperties.audience());
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }

    private static final class KeycloakRealmRoleConverter
            implements Converter<Jwt, Collection<GrantedAuthority>> {

        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
                return Set.of();
            }

            return roles.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(PLATFORM_ROLES::contains)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }
}
