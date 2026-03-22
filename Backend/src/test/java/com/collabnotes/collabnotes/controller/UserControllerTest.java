package com.collabnotes.collabnotes.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.collabnotes.collabnotes.dto.AuthResponse;
import com.collabnotes.collabnotes.dto.UserResponse;
import com.collabnotes.collabnotes.exception.GlobalExceptionHandler;
import com.collabnotes.collabnotes.service.UserService;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    private MockMvc mockMvc;
    private JwtAuthenticationToken authToken;

    @BeforeEach
    void setUp() {
        UserController controller = new UserController(userService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .subject("test-user")
                .claim("email", "test@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        authToken = new JwtAuthenticationToken(jwt);
    }

    private MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder builder) {
        return builder.principal(authToken);
    }

    @Nested
    class Register {

        @Test
        void whenValid_returns200WithAuthResponse() throws Exception {
            AuthResponse response = new AuthResponse("jwt-token", "user-1", "new@example.com", "New User");
            when(userService.registerUser("new@example.com", "password123", "New User"))
                    .thenReturn(response);

            mockMvc.perform(post("/api/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                            "{\"email\":\"new@example.com\",\"password\":\"password123\",\"displayName\":\"New User\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("jwt-token"))
                    .andExpect(jsonPath("$.userId").value("user-1"))
                    .andExpect(jsonPath("$.email").value("new@example.com"))
                    .andExpect(jsonPath("$.displayName").value("New User"));
        }

        @Test
        void whenEmailAlreadyRegistered_returns400() throws Exception {
            when(userService.registerUser("taken@example.com", "password123", "Name"))
                    .thenThrow(new IllegalArgumentException("Email already registered"));

            mockMvc.perform(post("/api/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"taken@example.com\",\"password\":\"password123\",\"displayName\":\"Name\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void whenUnexpectedError_returns500() throws Exception {
            when(userService.registerUser(any(), any(), any()))
                    .thenThrow(new RuntimeException("DB connection failed"));

            mockMvc.perform(post("/api/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"a@b.com\",\"password\":\"password123\",\"displayName\":\"X\"}"))
                    .andExpect(status().isInternalServerError());
        }
    }

    @Nested
    class Login {

        @Test
        void whenValid_returns200WithAuthResponse() throws Exception {
            AuthResponse response = new AuthResponse("jwt-token", "user-1", "user@example.com", "User");
            when(userService.loginUser("user@example.com", "correct")).thenReturn(response);

            mockMvc.perform(post("/api/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"user@example.com\",\"password\":\"correct\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("jwt-token"));
        }

        @Test
        void whenInvalidCredentials_returns401() throws Exception {
            when(userService.loginUser("user@example.com", "wrong")).thenReturn(null);

            mockMvc.perform(post("/api/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"user@example.com\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void whenUnexpectedError_returns500() throws Exception {
            when(userService.loginUser(any(), any()))
                    .thenThrow(new RuntimeException("Unexpected"));

            mockMvc.perform(post("/api/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"a@b.com\",\"password\":\"pass\"}"))
                    .andExpect(status().isInternalServerError());
        }
    }

    @Nested
    class LookupUserByEmail {

        @Test
        void whenAuthenticated_andUserFound_returns200() throws Exception {
            when(userService.findUserIdByEmail("friend@example.com")).thenReturn("friend-1");

            mockMvc.perform(withAuth(post("/api/users/lookup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"friend@example.com\"}")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value("friend-1"));
        }

        @Test
        void whenAuthenticated_andUserNotFound_returns404() throws Exception {
            when(userService.findUserIdByEmail("nobody@example.com")).thenReturn(null);

            mockMvc.perform(withAuth(post("/api/users/lookup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"nobody@example.com\"}")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void whenUnauthenticated_returns401() throws Exception {
            mockMvc.perform(post("/api/users/lookup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"a@b.com\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class LookupUserById {

        @Test
        void whenAuthenticated_andUserFound_returns200() throws Exception {
            UserResponse response = new UserResponse("user-1", "u@e.com", "User", null);
            when(userService.getUserInfo("user-1")).thenReturn(response);

            mockMvc.perform(withAuth(get("/api/users/lookup/user-1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value("user-1"))
                    .andExpect(jsonPath("$.email").value("u@e.com"));
        }

        @Test
        void whenAuthenticated_andUserNotFound_returns404() throws Exception {
            when(userService.getUserInfo("missing")).thenReturn(null);

            mockMvc.perform(withAuth(get("/api/users/lookup/missing")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void whenUnauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/users/lookup/user-1"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class GetCurrentUser {

        @Test
        void whenAuthenticated_andUserFound_returns200() throws Exception {
            UserResponse response = new UserResponse("test-user", "test@example.com", "Test", null);
            when(userService.getUserInfo("test-user")).thenReturn(response);

            mockMvc.perform(withAuth(get("/api/users/me")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value("test-user"))
                    .andExpect(jsonPath("$.email").value("test@example.com"));
        }

        @Test
        void whenAuthenticated_andUserNotInDb_returns404() throws Exception {
            when(userService.getUserInfo("test-user")).thenReturn(null);

            mockMvc.perform(withAuth(get("/api/users/me")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void whenUnauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/users/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class UpdateProfile {

        @Test
        void whenValid_returns200() throws Exception {
            UserResponse response = new UserResponse("test-user", "new@example.com", "New Name", null);
            when(userService.updateProfile("test-user", "new@example.com", "New Name")).thenReturn(response);

            mockMvc.perform(
                    withAuth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/users/me"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"new@example.com\",\"displayName\":\"New Name\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value("test-user"))
                    .andExpect(jsonPath("$.email").value("new@example.com"))
                    .andExpect(jsonPath("$.displayName").value("New Name"));
        }

        @Test
        void whenInvalidEmail_returns400() throws Exception {
            mockMvc.perform(
                    withAuth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/users/me"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"not-an-email\",\"displayName\":\"New Name\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void whenUserNotFound_returns404() throws Exception {
            when(userService.updateProfile("test-user", "new@example.com", "New Name")).thenReturn(null);

            mockMvc.perform(
                    withAuth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/users/me"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"new@example.com\",\"displayName\":\"New Name\"}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void whenEmailTaken_returns400() throws Exception {
            when(userService.updateProfile("test-user", "taken@example.com", "New Name"))
                    .thenThrow(new IllegalArgumentException("Email already taken"));

            mockMvc.perform(
                    withAuth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/users/me"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"taken@example.com\",\"displayName\":\"New Name\"}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
