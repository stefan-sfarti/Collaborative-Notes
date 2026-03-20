package com.collabnotes.collabnotes.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private WebRequest webRequest;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        webRequest = new ServletWebRequest(new MockHttpServletRequest());
    }

    @Test
    void handleUnauthorizedException_returns401WithMessage() {
        UnauthorizedException ex = new UnauthorizedException("Not authenticated");

        ResponseEntity<ProblemDetail> response = handler.handleUnauthorizedException(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Not authenticated", response.getBody().getDetail());
    }

    @Test
    void handleResourceNotFoundException_returns404WithMessage() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Note not found");

        ResponseEntity<ProblemDetail> response = handler.handleResourceNotFoundException(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Note not found", response.getBody().getDetail());
    }

    @Test
    void handleIllegalArgumentException_returns400WithMessage() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid input");

        ResponseEntity<ProblemDetail> response = handler.handleIllegalArgumentException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid input", response.getBody().getDetail());
    }

    @Test
    void handleConflictException_returns409WithMessage() {
        ConflictException ex = new ConflictException("Version conflict");

        ResponseEntity<ProblemDetail> response = handler.handleConflictException(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Version conflict", response.getBody().getDetail());
    }

    @Test
    void handleOptimisticLockingException_returns409WithGenericMessage() {
        ObjectOptimisticLockingFailureException ex =
                new ObjectOptimisticLockingFailureException("Note", "note-1");

        ResponseEntity<ProblemDetail> response = handler.handleOptimisticLockingException(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Note was modified by another user. Please refresh and try again.",
                response.getBody().getDetail());
    }

    @Test
    void handleNoHandlerFoundException_returns404() {
        NoHandlerFoundException ex = new NoHandlerFoundException("GET", "/nonexistent", null);

        ResponseEntity<ProblemDetail> response = handler.handleNoHandlerFoundException(ex, webRequest);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void handleNoResourceFoundException_returns404() {
        NoResourceFoundException ex = new NoResourceFoundException(
                org.springframework.http.HttpMethod.GET, "nonexistent", "No static resource nonexistent.");

        ResponseEntity<ProblemDetail> response = handler.handleNoResourceFoundException(ex, webRequest);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void handleAllExceptions_returns500WithGenericMessage() {
        Exception ex = new RuntimeException("Something unexpected happened");

        ResponseEntity<ProblemDetail> response = handler.handleAllExceptions(ex, webRequest);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Unexpected error has occurred", response.getBody().getDetail());
    }

    @Test
    void handleAllExceptions_doesNotExposeInternalErrorDetails() {
        Exception ex = new NullPointerException("sensitive.field was null at line 42");

        ResponseEntity<ProblemDetail> response = handler.handleAllExceptions(ex, webRequest);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Unexpected error has occurred", response.getBody().getDetail());
    }
}
