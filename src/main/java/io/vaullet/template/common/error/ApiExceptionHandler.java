package io.vaullet.template.common.error;

import io.micrometer.tracing.Tracer;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Single place where exceptions become HTTP responses, in RFC 9457 {@code problem+json}.
 *
 * <p>Design notes worth copying into your own services:
 *
 * <ul>
 *   <li><b>Extend {@link ResponseEntityExceptionHandler}.</b> It already handles the ~15 Spring MVC
 *       exceptions (unreadable body, unsupported media type, missing parameter, …) as
 *       {@code ProblemDetail}. Writing your own {@code @ExceptionHandler} for each is re-inventing
 *       a wheel that ships in the box.
 *   <li><b>One handler per <em>domain</em> failure, not per call site.</b> Controllers contain no
 *       try/catch and no {@code ResponseEntity.status(...)} plumbing.
 *   <li><b>Never leak internals.</b> Unexpected exceptions are logged with the stack trace and the
 *       correlation id, and the client gets a generic body plus that id. Echoing
 *       {@code ex.getMessage()} to callers is how table names and SQL end up in a bug tracker.
 *   <li><b>Always attach the trace id.</b> It is the one field that turns a screenshot of an error
 *       into a query against your logs.
 * </ul>
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Optional: tracing is not auto-configured in every test slice. */
    private final ObjectProvider<Tracer> tracer;

    ApiExceptionHandler(ObjectProvider<Tracer> tracer) {
        this.tracer = tracer;
    }

    @ExceptionHandler(ApplicationException.class)
    ProblemDetail handleApplicationException(ApplicationException ex, WebRequest request) {
        log.debug("Handled application error: {}", ex.getMessage());
        return problem(ex.errorType(), ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        // Deliberately vague: telling a caller *why* they were denied is an information leak.
        return problem(ErrorType.ACCESS_DENIED, "You do not have permission to perform this action", request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLocking(OptimisticLockingFailureException ex, WebRequest request) {
        return problem(
                ErrorType.CONCURRENT_MODIFICATION,
                "The resource was modified by another request. Re-read it and retry.",
                request);
    }

    /** {@code @Validated} on method parameters (as opposed to {@code @Valid} on a body). */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        var problem = problem(ErrorType.VALIDATION_FAILED, "One or more parameters are invalid", request);
        problem.setProperty(
                "errors",
                ex.getConstraintViolations().stream()
                        .map(violation -> new FieldError(
                                violation.getPropertyPath().toString(), violation.getMessage()))
                        .sorted(Comparator.comparing(FieldError::field))
                        .toList());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        return problem(ErrorType.INTERNAL_ERROR, "An unexpected error occurred", request);
    }

    /**
     * Enriches Spring's own body-validation problem with a structured {@code errors} array.
     *
     * <p>Clients need field-level detail to highlight the offending input; a flat sentence forces
     * them to parse prose.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        var problem = problem(ErrorType.VALIDATION_FAILED, "One or more fields are invalid", request);
        problem.setProperty(
                "errors",
                ex.getBindingResult().getFieldErrors().stream()
                        .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
                        .sorted(Comparator.comparing(FieldError::field))
                        .toList());
        return ResponseEntity.status(problem.getStatus()).headers(headers).body(problem);
    }

    private ProblemDetail problem(ErrorType errorType, String detail, WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(errorType.status(), detail);
        problem.setType(errorType.type());
        problem.setTitle(errorType.title());
        problem.setProperty("timestamp", Instant.now());
        problem.setInstance(instanceOf(request));

        var traceId = currentTraceId();
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
        return problem;
    }

    private static @Nullable URI instanceOf(WebRequest request) {
        var description = request.getDescription(false);
        return description.startsWith("uri=") ? URI.create(description.substring(4)) : null;
    }

    private @Nullable String currentTraceId() {
        var current = tracer.getIfAvailable();
        if (current == null || current.currentSpan() == null) {
            return null;
        }
        return current.currentSpan().context().traceId();
    }

    /** Field-level validation failure, serialised into the problem's {@code errors} array. */
    record FieldError(String field, @Nullable String message) {}
}
