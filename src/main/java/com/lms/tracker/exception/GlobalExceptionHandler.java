package com.lms.tracker.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.lms.tracker.util.LogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converts every exception into the same JSON {@link ErrorResponse}.
 * Client errors are logged at WARN without stack traces; server errors are logged at ERROR
 * with the stack trace, but the client only ever sees a generic message.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String VALIDATION_FAILED = "Validation failed";

    // ------------------------------------------------------------------ 404

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.info("Not found: {} {} -> {}", request.getMethod(), path(request), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "No endpoint or resource exists at this path", request);
    }

    // ------------------------------------------------------------------ 401 / 403 / 429

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenOperationException ex, HttpServletRequest request) {
        // AccessPolicy already wrote a detailed security log line
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        String user = request.getUserPrincipal() != null ? LogSanitizer.clean(request.getUserPrincipal().getName()) : "anonymous";
        log.warn("SECURITY Role check denied: user={} {} {}", LogSanitizer.maskEmail(user), request.getMethod(), path(request));
        return build(HttpStatus.FORBIDDEN, "You do not have permission to perform this action", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        String message = ex instanceof BadCredentialsException
                ? "Invalid email or password"
                : "Authentication failed";
        return build(HttpStatus.UNAUTHORIZED, message, request);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException ex, HttpServletRequest request) {
        return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request);
    }

    // ------------------------------------------------------------------ 400 - business rules

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        log.info("Bad request: {} {} -> {}", request.getMethod(), path(request), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    // ------------------------------------------------------------------ 400 - validation

    /** @Valid @RequestBody failures. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> errors.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));
        logValidation(request, errors);
        return buildWithFields(errors, request);
    }

    /** Constraint annotations on path variables / params (Spring 6.1+ built-in method validation). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ParameterValidationResult result : ex.getAllValidationResults()) {
            String param = result.getMethodParameter().getParameterName();
            if (result instanceof ParameterErrors parameterErrors) {
                for (FieldError fe : parameterErrors.getFieldErrors()) {
                    errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
                }
            } else {
                for (MessageSourceResolvable error : result.getResolvableErrors()) {
                    errors.putIfAbsent(param != null ? param : "parameter", error.getDefaultMessage());
                }
            }
        }
        logValidation(request, errors);
        return buildWithFields(errors, request);
    }

    /** Fallback for class-level @Validated style validation. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String property = violation.getPropertyPath().toString();
            String field = property.contains(".") ? property.substring(property.lastIndexOf('.') + 1) : property;
            errors.putIfAbsent(field, violation.getMessage());
        }
        logValidation(request, errors);
        return buildWithFields(errors, request);
    }

    /** e.g. GET /api/progress/course/abc */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String expected = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "value";
        String message = "Parameter '" + ex.getName() + "' has an invalid value (expected " + describe(expected) + ")";
        Map<String, String> errors = Map.of(ex.getName(), "must be " + describe(expected));
        logValidation(request, errors);
        return ResponseEntity.badRequest()
                .body(ErrorResponse.withFieldErrors(400, "Bad Request", message, path(request), errors));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingPathVariableException.class})
    public ResponseEntity<ErrorResponse> handleMissingParameter(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "A required request parameter is missing", request);
    }

    /** Malformed JSON, wrong types, unknown fields (mass assignment), invalid enum values. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        Throwable cause = ex.getCause();
        String message;
        Map<String, String> errors = null;

        if (cause instanceof UnrecognizedPropertyException upe) {
            String field = LogSanitizer.truncate(upe.getPropertyName(), 50);
            message = "Unknown field '" + field + "' is not allowed in this request";
            errors = Map.of(field, "is not an allowed field");
        } else if (cause instanceof InvalidFormatException ife && ife.getTargetType() != null && ife.getTargetType().isEnum()) {
            String field = fieldPath(ife);
            String allowed = Arrays.stream(ife.getTargetType().getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            message = "Invalid value for field '" + field + "'";
            errors = Map.of(field, "must be one of: " + allowed);
        } else if (cause instanceof MismatchedInputException mie && !mie.getPath().isEmpty()) {
            String field = fieldPath(mie);
            message = "Invalid value for field '" + field + "'";
            errors = Map.of(field, "has an invalid type or format");
        } else if (cause instanceof JsonProcessingException) {
            message = "Malformed JSON request body";
        } else {
            message = "Request body is missing or unreadable";
        }

        log.warn("Unreadable request body: {} {} -> {}", request.getMethod(), path(request), message);
        if (errors != null) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.withFieldErrors(400, "Bad Request", message, path(request), errors));
        }
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    // ------------------------------------------------------------------ 405 / 415 / 409

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method " + ex.getMethod() + " is not supported for this path", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type must be application/json", request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        log.info("Conflict: {} {} -> {}", request.getMethod(), path(request), ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Concurrent modification: {} {}", request.getMethod(), path(request));
        return build(HttpStatus.CONFLICT, "The record was modified by another request. Reload and try again", request);
    }

    // ------------------------------------------------------------------ database

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        // Never expose SQL, table or constraint names to the client
        log.warn("Data integrity violation: {} {} -> {}", request.getMethod(), path(request),
                ex.getMostSpecificCause().getClass().getSimpleName());
        return build(HttpStatus.CONFLICT, "The request conflicts with existing data", request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccess(DataAccessException ex, HttpServletRequest request) {
        log.error("Database error on {} {}", request.getMethod(), path(request), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "A database error occurred. Please try again later", request);
    }

    // ------------------------------------------------------------------ everything else

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), path(request), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    // ------------------------------------------------------------------ helpers

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), status.getReasonPhrase(), message, path(request)));
    }

    private ResponseEntity<ErrorResponse> buildWithFields(Map<String, String> errors, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.withFieldErrors(400, "Bad Request", VALIDATION_FAILED, path(request), errors));
    }

    private void logValidation(HttpServletRequest request, Map<String, String> errors) {
        log.warn("Validation failed: {} {} fields={}", request.getMethod(), path(request), errors.keySet());
    }

    private static String fieldPath(JsonMappingException ex) {
        String joined = ex.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                .collect(Collectors.joining("."));
        return joined.isEmpty() ? "body" : LogSanitizer.truncate(joined, 80);
    }

    private static String describe(String javaType) {
        return switch (javaType) {
            case "Long", "Integer", "long", "int" -> "a whole number";
            default -> "a valid " + javaType;
        };
    }

    private static String path(HttpServletRequest request) {
        return LogSanitizer.clean(request.getRequestURI());
    }
}
