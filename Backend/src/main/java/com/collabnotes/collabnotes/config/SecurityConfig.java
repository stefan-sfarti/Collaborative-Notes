package com.collabnotes.collabnotes.config;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String issuerUri;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtDecoder localJwtDecoder(
            @Value("${app.auth.jwt.secret-key:defaultSecretKeyForDevelopmentOnlyMustBeAtLeast256BitsLong}") String secretKey) {
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        return NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public AuthenticationManagerResolver<HttpServletRequest> tokenAuthenticationManagerResolver(
            JwtDecoder localJwtDecoder) {
        JwtAuthenticationProvider localJwtProvider = new JwtAuthenticationProvider(localJwtDecoder);

        return new AuthenticationManagerResolver<>() {
            private volatile AuthenticationManager keycloakAuthenticationManager;

            @Override
            public AuthenticationManager resolve(HttpServletRequest request) {
                return authentication -> {
                    Object credentials = authentication.getCredentials();
                    if (credentials == null) {
                        throw new BadCredentialsException("Unsupported authentication token type");
                    }

                    String token = credentials.toString();

                    if (isLocalToken(token)) {
                        return localJwtProvider.authenticate(authentication);
                    }

                    return getKeycloakAuthenticationManager().authenticate(authentication);
                };
            }

            private AuthenticationManager getKeycloakAuthenticationManager() {
                if (keycloakAuthenticationManager == null) {
                    synchronized (this) {
                        if (keycloakAuthenticationManager == null) {
                            if (issuerUri == null || issuerUri.isBlank()) {
                                throw new OAuth2AuthenticationException("Keycloak issuer is not configured");
                            }

                            JwtDecoder keycloakDecoder = JwtDecoders.fromIssuerLocation(issuerUri);
                            JwtAuthenticationProvider keycloakProvider = new JwtAuthenticationProvider(keycloakDecoder);
                            keycloakAuthenticationManager = keycloakProvider::authenticate;
                        }
                    }
                }

                return keycloakAuthenticationManager;
            }
        };
    }

    private boolean isLocalToken(String token) {
        try {
            String[] tokenParts = token.split("\\.");
            if (tokenParts.length < 2) {
                return false;
            }

            String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(tokenParts[1]),
                    StandardCharsets.UTF_8);
            Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);
            Object issuer = payload.get("iss");

            // Local JWTs generated by this app intentionally have no issuer claim.
            return issuer == null || issuer.toString().isBlank();
        } catch (Exception ex) {
            // Default to OAuth2/Keycloak path for malformed or unknown tokens.
            return false;
        }
    }

    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();

        return request -> {
            String path = request.getServletPath();
            if ("/api/users/register".equals(path)
                    || "/api/users/login".equals(path)
                    || "/users/register".equals(path)
                    || "/users/login".equals(path)) {
                return null;
            }

            return delegate.resolve(request);
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationManagerResolver<HttpServletRequest> tokenAuthenticationManagerResolver,
            BearerTokenResolver bearerTokenResolver) throws Exception {

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/ws-notes/**").permitAll()
                        .requestMatchers("/api/users/register").permitAll()
                        .requestMatchers("/api/users/login").permitAll()
                        .requestMatchers("/users/register").permitAll()
                        .requestMatchers("/users/login").permitAll()
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll().anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationManagerResolver(tokenAuthenticationManagerResolver)
                        .bearerTokenResolver(bearerTokenResolver))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                        .accessDeniedHandler(new BearerTokenAccessDeniedHandler()));

        return http.build();
    }
}
