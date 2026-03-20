package com.collabnotes.collabnotes.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.collabnotes.collabnotes.entity.User;
import com.collabnotes.collabnotes.repository.UserRepository;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public String getCurrentUserId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            return jwt.getSubject();
        }

        return authentication.getName();
    }

    public User getOrCreateUserFromJwt(Jwt jwt) {
        String userId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String displayName = resolveDisplayName(jwt, email, userId);

        return userRepository.findById(userId).orElseGet(() -> {
            User newUser = new User(userId, email, displayName);

            newUser.setPassword(passwordEncoder.encode(userId + "-external-auth"));
            newUser.setCreatedAt(LocalDateTime.now());
            logger.info("Creating new user from JWT: {}", userId);
            return userRepository.save(newUser);
        });
    }

    public User findById(String userId) {
        return userRepository.findById(userId).orElse(null);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    private String resolveDisplayName(Jwt jwt, String email, String userId) {
        String name = jwt.getClaimAsString("name");
        if (name != null && !name.isBlank()) {
            return name;
        }

        String preferredUsername = jwt.getClaimAsString("preferred_username");
        if (preferredUsername != null && !preferredUsername.isBlank()) {
            return preferredUsername;
        }

        if (email != null && !email.isBlank()) {
            return email;
        }

        return userId;
    }
}
