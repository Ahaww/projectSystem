package edu.wylie.crs.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorBody> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ErrorBody(ex.getMessage(), ex.getErrors()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> handleValid(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getDefaultMessage())
                .orElse("请求参数不合法");
        return ResponseEntity.badRequest().body(new ErrorBody(msg, List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleOther(Exception ex, HttpServletRequest request) {
        return ResponseEntity.internalServerError()
                .body(new ErrorBody(ex.getMessage() == null ? "服务器内部错误" : ex.getMessage(), List.of()));
    }
}
