package com.orderSystem.order_system.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

import static java.util.stream.Collectors.toMap;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .collect(toMap(
                        field ->field.getField(),
                        error -> error.getDefaultMessage(),
                        (msg1, msg2) -> msg1 + ", " + msg2
                ));

        return ResponseEntity.badRequest().body(errorMessage);
    }
}
