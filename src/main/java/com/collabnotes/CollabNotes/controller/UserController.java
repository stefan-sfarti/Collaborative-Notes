package com.collabnotes.CollabNotes.controller;

import com.collabnotes.CollabNotes.dto.UserEmailRequest;
import com.collabnotes.CollabNotes.dto.UserResponse;
import com.collabnotes.CollabNotes.service.UserService;
import com.collabnotes.CollabNotes.util.FirebaseAuthUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private FirebaseAuthUtil firebaseAuthUtil;

    /**
     * Endpoint to look up a user by email and return their ID
     * @param request The HTTP request with the auth token
     * @param emailRequest Request body containing the email to look up
     * @return The user ID if found
     */
    @PostMapping("/lookup")
    public ResponseEntity<?> lookupUserByEmail(
            HttpServletRequest request,
            @RequestBody UserEmailRequest emailRequest) {

        // Verify the caller is authenticated
        String userId = firebaseAuthUtil.verifyToken(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        try {
            String lookupUserId = userService.findUserIdByEmail(emailRequest.getEmail());
            if (lookupUserId == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
            }

            return ResponseEntity.ok(new UserResponse(lookupUserId, emailRequest.getEmail()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error looking up user: " + e.getMessage());
        }
    }

    /**
     * Endpoint to look up a user by ID and return their email
     * @param request The HTTP request with the auth token
     * @param userId The ID of the user to look up
     * @return The user email if found
     */
    @GetMapping("/lookup/{userId}")
    public ResponseEntity<?> lookupUserById(
            HttpServletRequest request,
            @PathVariable String userId) {

        // Verify the caller is authenticated
        String callerUserId = firebaseAuthUtil.verifyToken(request);
        if (callerUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        try {
            UserResponse userInfo = userService.getUserInfo(userId);
            if (userInfo == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
            }

            return ResponseEntity.ok(userInfo);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error looking up user: " + e.getMessage());
        }
    }

    /**
     * Get current user information
     * @param request The HTTP request with the auth token
     * @return Current user data
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        String userId = firebaseAuthUtil.verifyToken(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        try {
            UserResponse userInfo = userService.getUserInfo(userId);
            if (userInfo == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User information not found");
            }

            return ResponseEntity.ok(userInfo);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching user information: " + e.getMessage());
        }
    }
}