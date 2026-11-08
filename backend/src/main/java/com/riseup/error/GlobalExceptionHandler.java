package com.riseup.error;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Map<String, Object>> handle(DomainException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("detail", ex.getMessage());
        body.put("code", ex.getCode());
        body.putAll(ex.getExtra());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }
}
