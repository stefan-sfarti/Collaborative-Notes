package com.collabnotes.collabnotes.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

import io.jsonwebtoken.security.Keys;

class JwtUtilTest {

    private static final String SECRET = "defaultSecretKeyForDevelopmentOnlyMustBeAtLeast256BitsLong";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKey", SECRET);
    }

    @Test
    void generateToken_createsTokenCompatibleWithHs256Decoder() {
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
}
