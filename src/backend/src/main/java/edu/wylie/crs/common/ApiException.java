package edu.wylie.crs.common;

import org.springframework.http.HttpStatus;

import java.util.List;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final List<ErrorItem> errors;

    public ApiException(HttpStatus status, String message) {
        this(status, message, List.of());
    }

    public ApiException(HttpStatus status, String message, List<ErrorItem> errors) {
        super(message);
        this.status = status;
        this.errors = errors == null ? List.of() : errors;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public List<ErrorItem> getErrors() {
        return errors;
    }

    public static ApiException notImplemented(String feature) {
        return new ApiException(HttpStatus.NOT_IMPLEMENTED, feature + " 尚未实现");
    }
}
