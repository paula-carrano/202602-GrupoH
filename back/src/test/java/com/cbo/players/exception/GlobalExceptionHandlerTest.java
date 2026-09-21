package com.cbo.players.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/test");
    }

    @Test
    void handleResourceNotFoundException_returns404WithProblemDetail() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Jugador no encontrado", ErrorCode.PLAYER_NOT_FOUND);

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleApiException(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        ProblemDetail body = response.getBody();
        assertNotNull(body);
        assertEquals("Jugador no encontrado", body.getDetail());
        assertEquals("PLAYER_NOT_FOUND", body.getProperties().get("errorCode"));
        assertNotNull(body.getProperties().get("timestamp"));
        assertNotNull(body.getProperties().get("correlationId"));
    }

    @Test
    void handleUserAlreadyExistsException_returns409WithProblemDetail() {
        UserAlreadyExistsException ex = new UserAlreadyExistsException("El correo ya está registrado", ErrorCode.EMAIL_ALREADY_EXISTS);

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleApiException(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        ProblemDetail body = response.getBody();
        assertNotNull(body);
        assertEquals("El correo ya está registrado", body.getDetail());
        assertEquals("EMAIL_ALREADY_EXISTS", body.getProperties().get("errorCode"));
    }

    @Test
    void handleGenericException_returns500WithoutInternalDetails() {
        Exception ex = new RuntimeException("SQL syntax error near table users");

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleGenericException(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ProblemDetail body = response.getBody();
        assertNotNull(body);
        assertEquals("Ha ocurrido un error interno en el servidor. Por favor intente más tarde.", body.getDetail());
        assertEquals("INTERNAL_SERVER_ERROR", body.getProperties().get("errorCode"));
        assertFalse(body.getDetail().contains("SQL"));
    }
}
