package com.face.platform.v3.api;

import com.face.platform.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Clock;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.face.platform.v3")
public class V3ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(V3ApiExceptionHandler.class);
    private final Clock clock;

    public V3ApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(V3ApiException.class)
    public ResponseEntity<V3ApiResponse<Void>> v3(
        V3ApiException exception,
        HttpServletRequest request
    ) {
        return response(exception.status(), exception.code(), exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<V3ApiResponse<Void>> validation(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getDefaultMessage() == null ? "提交内容不正确" : error.getDefaultMessage())
            .orElse("提交内容不正确");
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<V3ApiResponse<Void>> denied(
        AccessDeniedException exception,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.FORBIDDEN,
            "PERMISSION_DENIED",
            exception.getMessage() == null ? "当前账号没有此操作权限" : exception.getMessage(),
            request
        );
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<V3ApiResponse<Void>> business(
        ApiException exception,
        HttpServletRequest request
    ) {
        String code = switch (exception.status()) {
            case BAD_REQUEST -> "VALIDATION_ERROR";
            case NOT_FOUND -> "NOT_FOUND";
            case CONFLICT -> "BUSINESS_CONFLICT";
            case FORBIDDEN -> "PERMISSION_DENIED";
            case SERVICE_UNAVAILABLE -> "DEPENDENCY_UNAVAILABLE";
            default -> "BUSINESS_ERROR";
        };
        return response(exception.status(), code, exception.getMessage(), request);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<V3ApiResponse<Void>> duplicate(
        DuplicateKeyException exception,
        HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, "VERSION_CONFLICT", "请求与当前数据版本冲突", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<V3ApiResponse<Void>> unexpected(
        Exception exception,
        HttpServletRequest request
    ) {
        String requestId = V3RequestSupport.requestId(request);
        LOGGER.error("V3 request failed requestId={} exceptionType={}", requestId, exception.getClass().getName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            V3ApiResponse.error("INTERNAL_ERROR", "系统暂时无法处理该请求", requestId, clock)
        );
    }

    private ResponseEntity<V3ApiResponse<Void>> response(
        HttpStatus status,
        String code,
        String message,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(
            V3ApiResponse.error(code, message, V3RequestSupport.requestId(request), clock)
        );
    }
}
