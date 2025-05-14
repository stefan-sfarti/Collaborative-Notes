package com.collabnotes.CollabNotes.util;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FirebaseAuthUtil {

    @Autowired
    private FirebaseAuth firebaseAuth;

    /**
     * Verifies the Firebase ID token from the request
     * @param request HttpServletRequest containing the token in the Authorization header
     * @return User ID if token is valid, null otherwise
     */
    public String verifyToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String idToken = authHeader.substring(7);
            try {
                FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);
                return decodedToken.getUid();
            } catch (FirebaseAuthException e) {
                System.err.println("Error verifying token: " + e.getMessage());
                return null;
            }
        }
        return null;
    }

    /**
     * Gets the full decoded token with all claims
     * @param request HttpServletRequest containing the token in the Authorization header
     * @return FirebaseToken if valid, null otherwise
     */
    public FirebaseToken getDecodedToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String idToken = authHeader.substring(7);
            try {
                return firebaseAuth.verifyIdToken(idToken);
            } catch (FirebaseAuthException e) {
                System.err.println("Error decoding token: " + e.getMessage());
                return null;
            }
        }
        return null;
    }
}