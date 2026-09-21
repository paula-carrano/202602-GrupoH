package com.cbo.players.exception;

import com.cbo.players.dto.response.ViolationDto;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String PROP_ERROR_CODE = "errorCode";
    private static final String PROP_TIMESTAMP = "timestamp";
    private static final String PROP_CORRELATION_ID = "correlationId";
    private static final String PROP_VIOLATIONS = "violations";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex, HttpServletRequest request) {
        String correlationId = UUID.randomUUID().toString();
        log.warn("Excepción de negocio [{}]: {} - ErrorCode: {}", correlationId, ex.getMessage(), ex.getErrorCode());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        enrichProblemDetail(problem, ex.getErrorCode().name(), request.getRequestURI(), correlationId, null);

        return ResponseEntity.status(ex.getStatus()).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String correlationId = UUID.randomUUID().toString();
        log.warn("Error de validación [{}] en {}", correlationId, request.getRequestURI());

        List<ViolationDto> violations = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            violations.add(new ViolationDto(fieldError.getField(), fieldError.getDefaultMessage()));
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Los datos provistos en la solicitud contienen errores de validación."
        );
        enrichProblemDetail(problem, ErrorCode.VALIDATION_FAILED.name(), request.getRequestURI(), correlationId, violations);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatchException(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String correlationId = UUID.randomUUID().toString();
        log.warn("Parámetro con formato inválido [{}]: {}", correlationId, ex.getMessage());

        String paramName = ex.getName();
        String message = String.format("El parámetro '%s' tiene un formato inválido.", paramName);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
        enrichProblemDetail(problem, ErrorCode.INVALID_PARAMETER_FORMAT.name(), request.getRequestURI(), correlationId, null);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleNotReadableException(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String correlationId = UUID.randomUUID().toString();
        log.warn("Cuerpo de solicitud malformado [{}]: {}", correlationId, ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "El cuerpo de la solicitud no tiene un formato JSON válido o está incompleto."
        );
        enrichProblemDetail(problem, ErrorCode.MALFORMED_JSON.name(), request.getRequestURI(), correlationId, null);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        String correlationId = UUID.randomUUID().toString();
        log.warn("Acceso denegado [{}]: {}", correlationId, ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "No posee los permisos necesarios para realizar esta operación."
        );
        enrichProblemDetail(problem, ErrorCode.FORBIDDEN_OPERATION.name(), request.getRequestURI(), correlationId, null);

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        String correlationId = UUID.randomUUID().toString();
        log.warn("Falla de autenticación [{}]: {}", correlationId, ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Autenticación requerida para acceder a este recurso."
        );
        enrichProblemDetail(problem, ErrorCode.UNAUTHORIZED_ACCESS.name(), request.getRequestURI(), correlationId, null);

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex, HttpServletRequest request) {
        String correlationId = UUID.randomUUID().toString();
        log.error("Error interno del servidor no controlado [{}]", correlationId, ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ha ocurrido un error interno en el servidor. Por favor intente más tarde."
        );
        enrichProblemDetail(problem, ErrorCode.INTERNAL_SERVER_ERROR.name(), request.getRequestURI(), correlationId, null);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    private void enrichProblemDetail(ProblemDetail problem, String errorCode, String path, String correlationId, List<ViolationDto> violations) {
        problem.setInstance(URI.create(path));
        problem.setProperty(PROP_ERROR_CODE, errorCode);
        problem.setProperty(PROP_TIMESTAMP, Instant.now().toString());
        problem.setProperty(PROP_CORRELATION_ID, correlationId);
        if (violations != null && !violations.isEmpty()) {
            problem.setProperty(PROP_VIOLATIONS, violations);
        }
    }
}
