package com.collabnotes.collabnotes.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.collabnotes.collabnotes.dto.AuthResponse;
import com.collabnotes.collabnotes.dto.UserResponse;
import com.collabnotes.collabnotes.entity.User;
import com.collabnotes.collabnotes.repository.UserRepository;
import com.collabnotes.collabnotes.util.JwtUtil;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, jwtUtil);
    }

    @Test
    void registerUser_whenEmailAlreadyRegistered_throwsException() {
        when(userRepository.findByEmail("taken@example.com"))
                .thenReturn(Optional.of(new User("u1", "taken@example.com", "Taken")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.registerUser("taken@example.com", "password", "Name"));

        assertEquals("Email already registered", ex.getMessage());
    }

    @Test
    void registerUser_whenValid_savesUserAndReturnsAuthResponse() {
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(jwtUtil.generateToken(any(String.class), eq("new@example.com"))).thenReturn("token-123");

        AuthResponse response = userService.registerUser("new@example.com", "password123", "New User");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User saved = userCaptor.getValue();
        assertNotNull(saved.getId());
        assertEquals("new@example.com", saved.getEmail());
        assertEquals("New User", saved.getDisplayName());
        assertEquals("encoded-password", saved.getPassword());
        assertNotNull(saved.getCreatedAt());

        assertEquals("token-123", response.getToken());
        assertEquals(saved.getId(), response.getUserId());
        assertEquals("new@example.com", response.getEmail());
        assertEquals("New User", response.getDisplayName());
    }

    @Test
    void registerUser_whenDisplayNameMissing_usesEmailAsDisplayName() {
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(jwtUtil.generateToken(any(String.class), eq("new@example.com"))).thenReturn("token-123");

        AuthResponse response = userService.registerUser("new@example.com", "password123", null);

        assertEquals("new@example.com", response.getDisplayName());
    }

    @Test
    void loginUser_whenUserMissing_returnsNull() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertNull(userService.loginUser("missing@example.com", "password"));
    }

    @Test
    void loginUser_whenOauthUserPasswordMismatch_returnsNull() {
        User user = new User("u-1", "oauth@example.com", "OAuth User");
        user.setPassword("ignored");

        when(userRepository.findByEmail("oauth@example.com")).thenReturn(Optional.of(user));

        assertNull(userService.loginUser("oauth@example.com", "password"));
    }

    @Test
    void loginUser_whenPasswordMismatch_returnsNull() {
        User user = new User("u-1", "user@example.com", "User");
        user.setPassword("encoded");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertNull(userService.loginUser("user@example.com", "wrong"));
    }

    @Test
    void loginUser_whenValid_returnsAuthResponse() {
        User user = new User("u-1", "user@example.com", "User");
        user.setPassword("encoded");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "encoded")).thenReturn(true);
        when(jwtUtil.generateToken("u-1", "user@example.com")).thenReturn("jwt-token");

        AuthResponse response = userService.loginUser("user@example.com", "correct");

        assertNotNull(response);
        assertEquals("jwt-token", response.getToken());
        assertEquals("u-1", response.getUserId());
        assertEquals("user@example.com", response.getEmail());
        assertEquals("User", response.getDisplayName());
    }

    @Test
    void findUserIdByEmail_whenUserExists_returnsId() {
        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(new User("user-1", "user@example.com", "User")));

        assertEquals("user-1", userService.findUserIdByEmail("user@example.com"));
    }

    @Test
    void getUserInfo_whenUserExists_returnsMappedResponse() {
        User user = new User("u-1", "user@example.com", "User Name");
        user.setPhotoUrl("https://example.com/avatar.png");

        when(userRepository.findById("u-1")).thenReturn(Optional.of(user));

        UserResponse response = userService.getUserInfo("u-1");

        assertNotNull(response);
        assertEquals("u-1", response.getUserId());
        assertEquals("user@example.com", response.getEmail());
        assertEquals("User Name", response.getDisplayName());
        assertEquals("https://example.com/avatar.png", response.getPhotoUrl());
    }
}
