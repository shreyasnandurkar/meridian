package com.meridian.common;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Domain error rendered as RFC 7807 problem+json by {@link GlobalExceptionHandler}. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String title;
    private final Map<String, Object> properties;

    public ApiException(HttpStatus status, String title, String detail) {
        this(status, title, detail, Map.of());
    }

    public ApiException(HttpStatus status, String title, String detail, Map<String, Object> properties) {
        super(detail);
        this.status = status;
        this.title = title;
        this.properties = properties;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    /** Extra problem members, e.g. {@code errors}. */
    public Map<String, Object> getProperties() {
        return properties;
    }

    public static ApiException notFound(String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, "Not found", detail);
    }

    public static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, "Conflict", detail);
    }

    public static ApiException unauthorized(String detail) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized", detail);
    }

    public static ApiException forbidden(String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, "Forbidden", detail);
    }

    public static ApiException badRequest(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Invalid request", detail);
    }

    /** 400 carrying a list of validation messages under {@code violations}. */
    public static ApiException invalid(String detail, List<String> violations) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Invalid request", detail, Map.of("violations", violations));
    }

    public static ApiException badGateway(String detail) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "Upstream unavailable", detail);
    }
}
