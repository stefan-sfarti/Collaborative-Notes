package com.collabnotes.collabnotes.util;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${app.auth.jwt.secret-key:defaultSecretKeyForDevelopmentOnlyMustBeAtLeast256BitsLong}")
    private String secretKey;

    @Value("${app.auth.jwt.expiration-ms:86400000}")
    private long expirationInMs;

    public String generateToken(String userId, String email) {
        return generateToken(userId, email, null);
    }

    public String generateToken(String userId, String email, String displayName) {
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        Date issuedAt = new Date();

        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                .setSubject(userId)
                .claim("email", email)
                .setIssuedAt(issuedAt)
                .setExpiration(new Date(issuedAt.getTime() + expirationInMs));

        if (displayName != null && !displayName.isBlank()) {
            builder.claim("name", displayName);
        }

        return builder
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUserId(String token) {
        try {
            return parseClaims(token).map(Claims::getSubject).orElse(null);
        } catch (Exception e) {
            logger.error("Failed to extract user ID from token: {}", e.getMessage());
            return null;
        }
    }

    public String extractEmail(String token) {
        try {
            return parseClaims(token)
                    .map(claims -> claims.get("email", String.class))
                    .orElse(null);
        } catch (Exception e) {
            logger.error("Failed to extract email from token: {}", e.getMessage());
            return null;
        }
    }

    public Map<String, Object> extractClaims(String token) {
        return parseClaims(token).map(claims -> (Map<String, Object>) claims).orElse(Map.of());
    }

    public boolean validateToken(String token) {
        try {
            return parseClaims(token).isPresent();
        } catch (Exception e) {
            logger.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    private Optional<Claims> parseClaims(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }

        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (token.isBlank()) {
            return Optional.empty();
        }

        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        return Optional.of(claims);
    }
}
