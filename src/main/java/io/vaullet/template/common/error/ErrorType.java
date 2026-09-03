package io.vaullet.template.common.error;

import java.net.URI;
import org.springframework.http.HttpStatus;

/**
 * The catalogue of machine-readable error types this service can return.
 *
 * <p>RFC 9457 ({@code application/problem+json}) says the {@code type} URI is the identifier a
 * client branches on — not the HTTP status, and certainly not a prose message. Enumerating them in
 * one place means the set is reviewable, documentable, and impossible to typo at a call site.
 */
public enum ErrorType {
    VALIDATION_FAILED("validation-failed", HttpStatus.BAD_REQUEST, "Request validation failed"),
    RESOURCE_NOT_FOUND("resource-not-found", HttpStatus.NOT_FOUND, "Resource not found"),
    RESOURCE_CONFLICT("resource-conflict", HttpStatus.CONFLICT, "Resource conflict"),
    CONCURRENT_MODIFICATION("concurrent-modification", HttpStatus.CONFLICT, "Concurrent modification"),
    ACCESS_DENIED("access-denied", HttpStatus.FORBIDDEN, "Access denied"),
    UPSTREAM_UNAVAILABLE("upstream-unavailable", HttpStatus.SERVICE_UNAVAILABLE, "Upstream service unavailable"),
    INTERNAL_ERROR("internal-error", HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    /** Resolvable base; point it at real documentation once you publish any. */
    private static final String BASE = "https://errors.vaullet.io/";

    private final URI type;
    private final HttpStatus status;
    private final String title;

    ErrorType(String slug, HttpStatus status, String title) {
        this.type = URI.create(BASE + slug);
        this.status = status;
        this.title = title;
    }

    public URI type() {
        return type;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
