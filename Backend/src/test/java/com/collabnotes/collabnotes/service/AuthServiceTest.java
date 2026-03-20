package com.collabnotes.collabnotes.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.collabnotes.collabnotes.entity.User;
import com.collabnotes.collabnotes.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private Authentication authentication;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder);
    }

    @Test
    void getCurrentUserId_whenAuthenticationIsNull_returnsNull() {
        assertNull(authService.getCurrentUserId(null));
    }

    @Test
    void getCurrentUserId_whenAuthenticationExists_returnsJwtSubject() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .claim("email", "user@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(authentication.getPrincipal()).thenReturn(jwt);

        assertEquals("user-123", authService.getCurrentUserId(authentication));
    }

    @Test
    void getCurrentUserId_whenPrincipalIsNotJwt_returnsAuthenticationName() {
        when(authentication.getPrincipal()).thenReturn("user-123");
        when(authentication.getName()).thenReturn("user-123");

        assertEquals("user-123", authService.getCurrentUserId(authentication));
    }

    @Test
    void getOrCreateUserFromJwt_whenUserExists_returnsExistingUser() {
        User existing = new User("user-1", "existing@example.com", "Existing");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-1")
                .claim("email", "existing@example.com")
                .claim("name", "Existing")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));

        User result = authService.getOrCreateUserFromJwt(jwt);

        assertEquals(existing, result);
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(any(String.class));
    }

    @Test
    void getOrCreateUserFromJwt_whenUserMissing_createsAndSavesExternalUser() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("external-id")
                .claim("email", "ext@example.com")
                .claim("name", "Ext User")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(userRepository.findById("external-id")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("external-id-external-auth")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = authService.getOrCreateUserFromJwt(jwt);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User saved = userCaptor.getValue();
        assertEquals("external-id", saved.getId());
        assertEquals("ext@example.com", saved.getEmail());
        assertEquals("Ext User", saved.getDisplayName());
        assertEquals("encoded-password", saved.getPassword());
        assertNotNull(saved.getCreatedAt());

        assertEquals("external-id", result.getId());
    }

    @Test
    void getOrCreateUserFromJwt_whenNameMissing_usesPreferredUsernameAsDisplayName() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("external-id")
                .claim("email", "ext@example.com")
                .claim("preferred_username", "ext-user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(userRepository.findById("external-id")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("external-id-external-auth")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = authService.getOrCreateUserFromJwt(jwt);

        assertEquals("ext-user", result.getDisplayName());
    }

    @Test
    void getOrCreateUserFromJwt_whenNameAndPreferredUsernameMissing_usesEmailAsDisplayName() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("external-id")
                .claim("email", "ext@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(userRepository.findById("external-id")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("external-id-external-auth")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = authService.getOrCreateUserFromJwt(jwt);

        assertEquals("ext@example.com", result.getDisplayName());
    }

    @Test
    void findById_whenMissing_returnsNull() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertNull(authService.findById("missing"));
    }

    @Test
    void findByEmail_whenPresent_returnsUser() {
        User user = new User("u-1", "user@example.com", "User");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        User result = authService.findByEmail("user@example.com");

        assertEquals("u-1", result.getId());
    }
}
