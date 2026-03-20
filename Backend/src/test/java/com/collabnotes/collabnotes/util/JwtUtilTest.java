package com.collabnotes.collabnotes.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Objects;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

class JwtUtilTest {

    private static final String SECRET = "defaultSecretKeyForDevelopmentOnlyMustBeAtLeast256BitsLong";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expirationInMs", 86400000L);
    }

    @Nested
    class GenerateToken {

        @Test
        void createsTokenCompatibleWithHs256Decoder() {
            String token = jwtUtil.generateToken("user-123", "user@example.com");

            SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            Jwt decoded = NimbusJwtDecoder.withSecretKey(key)
                    .macAlgorithm(MacAlgorithm.HS256)
                    .build()
                    .decode(token);

            assertNotNull(decoded);
            assertEquals("user-123", decoded.getSubject());
            assertEquals("user@example.com", decoded.getClaimAsString("email"));
        }

        @Test
        void tokenHasIssuedAtAndExpiration() {
            String token = jwtUtil.generateToken("user-1", "u@e.com");

            SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            Jwt decoded = NimbusJwtDecoder.withSecretKey(key)
                    .macAlgorithm(MacAlgorithm.HS256)
                    .build()
                    .decode(token);

            assertNotNull(decoded.getIssuedAt());
            assertNotNull(decoded.getExpiresAt());

            Date issuedAt = Date.from(decoded.getIssuedAt());
            Date expiresAt = Date.from(decoded.getExpiresAt());
            assertTrue(expiresAt.after(issuedAt));
        }

        @Test
        void tokenExpiresIn24Hours() {
            String token = jwtUtil.generateToken("user-1", "u@e.com");

            SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            Jwt decoded = NimbusJwtDecoder.withSecretKey(key)
                    .macAlgorithm(MacAlgorithm.HS256)
                    .build()
                    .decode(token);

            assertNotNull(decoded.getIssuedAt());
            assertNotNull(decoded.getExpiresAt());

            long diffMs = Objects.requireNonNull(decoded.getExpiresAt()).toEpochMilli()
                    - Objects.requireNonNull(decoded.getIssuedAt()).toEpochMilli();
            assertEquals(86400000L, diffMs);
        }

        @Test
        void tokenIncludesNameClaimWhenProvided() {
            String token = jwtUtil.generateToken("user-1", "u@e.com", "Display Name");

            SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            Jwt decoded = NimbusJwtDecoder.withSecretKey(key)
                    .macAlgorithm(MacAlgorithm.HS256)
                    .build()
                    .decode(token);

            assertEquals("Display Name", decoded.getClaimAsString("name"));
        }

        @Test
        void tokenSkipsNameClaimWhenBlank() {
            String token = jwtUtil.generateToken("user-1", "u@e.com", "  ");

            SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            Jwt decoded = NimbusJwtDecoder.withSecretKey(key)
                    .macAlgorithm(MacAlgorithm.HS256)
                    .build()
                    .decode(token);

            assertNull(decoded.getClaimAsString("name"));
        }
    }

    @Nested
    class ExtractUserId {

        @Test
        void extractsUserIdFromValidToken() {
            String token = jwtUtil.generateToken("user-123", "user@example.com");

            assertEquals("user-123", jwtUtil.extractUserId(token));
        }

        @Test
        void extractsUserIdFromBearerPrefixedToken() {
            String token = jwtUtil.generateToken("user-123", "user@example.com");

            assertEquals("user-123", jwtUtil.extractUserId("Bearer " + token));
        }

        @Test
        void returnsNullForNullToken() {
            assertNull(jwtUtil.extractUserId(null));
        }

        @Test
        void returnsNullForEmptyToken() {
            assertNull(jwtUtil.extractUserId(""));
        }

        @Test
        void returnsNullForMalformedToken() {
            assertNull(jwtUtil.extractUserId("not.a.valid.jwt"));
        }

        @Test
        void returnsNullForTokenSignedWithDifferentKey() {
            SecretKey differentKey = Keys.hmacShaKeyFor(
                    "aDifferentSecretKeyThatIsAlsoAtLeast256BitsLongForTesting!!".getBytes(StandardCharsets.UTF_8));
            String token = Jwts.builder()
                    .setSubject("user-1")
                    .signWith(differentKey, SignatureAlgorithm.HS256)
                    .compact();

            assertNull(jwtUtil.extractUserId(token));
        }

        @Test
        void returnsNullForExpiredToken() {
            SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            String expiredToken = Jwts.builder()
                    .setSubject("user-1")
                    .setIssuedAt(new Date(System.currentTimeMillis() - 200000))
                    .setExpiration(new Date(System.currentTimeMillis() - 100000))
                    .signWith(key, SignatureAlgorithm.HS256)
                    .compact();

            assertNull(jwtUtil.extractUserId(expiredToken));
        }

        @Test
        void returnsNullForBearerPrefixOnly() {
            assertNull(jwtUtil.extractUserId("Bearer "));
        }
    }

    @Nested
    class ExtractEmail {

        @Test
        void extractsEmailFromValidToken() {
            String token = jwtUtil.generateToken("user-1", "email@test.com");

            assertEquals("email@test.com", jwtUtil.extractEmail(token));
        }

        @Test
        void extractsEmailFromBearerPrefixedToken() {
            String token = jwtUtil.generateToken("user-1", "email@test.com");

            assertEquals("email@test.com", jwtUtil.extractEmail("Bearer " + token));
        }

        @Test
        void returnsNullForNullToken() {
            assertNull(jwtUtil.extractEmail(null));
        }

        @Test
        void returnsNullForEmptyToken() {
            assertNull(jwtUtil.extractEmail(""));
        }

        @Test
        void returnsNullForMalformedToken() {
            assertNull(jwtUtil.extractEmail("garbage"));
        }

        @Test
        void returnsNullForExpiredToken() {
            SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            String expiredToken = Jwts.builder()
                    .setSubject("user-1")
                    .claim("email", "old@test.com")
                    .setExpiration(new Date(System.currentTimeMillis() - 100000))
                    .signWith(key, SignatureAlgorithm.HS256)
                    .compact();

            assertNull(jwtUtil.extractEmail(expiredToken));
        }
    }

    @Nested
    class ValidateToken {

        @Test
        void returnsTrueForValidToken() {
            String token = jwtUtil.generateToken("user-1", "u@e.com");

            assertTrue(jwtUtil.validateToken(token));
        }

        @Test
        void returnsTrueForBearerPrefixedValidToken() {
            String token = jwtUtil.generateToken("user-1", "u@e.com");

            assertTrue(jwtUtil.validateToken("Bearer " + token));
        }

        @Test
        void returnsFalseForNullToken() {
            assertFalse(jwtUtil.validateToken(null));
        }

        @Test
        void returnsFalseForEmptyToken() {
            assertFalse(jwtUtil.validateToken(""));
        }

        @Test
        void returnsFalseForMalformedToken() {
            assertFalse(jwtUtil.validateToken("not.valid.token"));
        }

        @Test
        void returnsFalseForExpiredToken() {
            SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            String expiredToken = Jwts.builder()
                    .setSubject("user-1")
                    .setExpiration(new Date(System.currentTimeMillis() - 100000))
                    .signWith(key, SignatureAlgorithm.HS256)
                    .compact();

            assertFalse(jwtUtil.validateToken(expiredToken));
        }

        @Test
        void returnsFalseForTokenSignedWithDifferentKey() {
            SecretKey differentKey = Keys.hmacShaKeyFor(
                    "aDifferentSecretKeyThatIsAlsoAtLeast256BitsLongForTesting!!".getBytes(StandardCharsets.UTF_8));
            String token = Jwts.builder()
                    .setSubject("user-1")
                    .setExpiration(new Date(System.currentTimeMillis() + 86400000))
                    .signWith(differentKey, SignatureAlgorithm.HS256)
                    .compact();

            assertFalse(jwtUtil.validateToken(token));
        }
    }
}
