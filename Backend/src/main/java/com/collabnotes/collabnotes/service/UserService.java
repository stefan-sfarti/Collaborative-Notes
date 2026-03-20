package com.collabnotes.collabnotes.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.collabnotes.collabnotes.dto.AuthResponse;
import com.collabnotes.collabnotes.dto.UserResponse;
import com.collabnotes.collabnotes.entity.User;
import com.collabnotes.collabnotes.repository.UserRepository;
import com.collabnotes.collabnotes.util.JwtUtil;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Cacheable(value = "userEmailCache", key = "#p0")
    public String findUserIdByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(User::getId)
                .orElse(null);
    }

    @Cacheable(value = "userCache", key = "#p0")
    public UserResponse getUserInfo(@NonNull String userId) {
        return userRepository.findById(userId)
                .map(user -> new UserResponse(
                        user.getId(),
                        user.getEmail(),
                        user.getDisplayName(),
                        user.getPhotoUrl()))
                .orElse(null);
    }

    public User findById(@NonNull String userId) {
        return userRepository.findById(userId).orElse(null);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    @CacheEvict(value = "userCache", key = "#p0")
    public void refreshUserCache(String userId) {
        logger.debug("Cache refreshed for user: {}", userId);
    }

    public AuthResponse registerUser(String email, String password, String displayName) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }

        String userId = UUID.randomUUID().toString();
        User user = new User(userId, email, displayName != null ? displayName : email);
        user.setPassword(passwordEncoder.encode(password));
        user.setCreatedAt(java.time.LocalDateTime.now());

        userRepository.save(user);
        logger.info("Registered new user: {}", userId);

        String token = jwtUtil.generateToken(userId, email, user.getDisplayName());
        return new AuthResponse(token, userId, email, user.getDisplayName());
    }

    public AuthResponse loginUser(String email, String password) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return null;
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            return null;
        }

        String token = jwtUtil.generateToken(user.getId(), email, user.getDisplayName());
        return new AuthResponse(token, user.getId(), email, user.getDisplayName());
    }
}
