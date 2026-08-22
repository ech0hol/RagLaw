package com.raglaw.server.web;

import com.raglaw.common.api.ApiResponse;
import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case ErrorCodes.NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ErrorCodes.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case ErrorCodes.FORBIDDEN -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCodes.VALIDATION, ex.getMessage()));
    }
}
