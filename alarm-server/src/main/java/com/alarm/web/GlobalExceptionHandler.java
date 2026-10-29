package com.alarm.web;

import com.alarm.contracts.common.ApiError;
import com.alarm.contracts.common.ErrorCodes;
import com.alarm.domain.exception.AlarmDomainException;
import com.alarm.domain.exception.IdempotencyConflictException;
import com.alarm.domain.exception.IllegalRequestException;
import com.alarm.domain.exception.InvalidTransitionException;
import com.alarm.domain.exception.NotFoundException;
import com.alarm.domain.exception.OptimisticLockConflictException;
import com.alarm.domain.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 异常 -> HTTP 状态与统一错误码映射；错误正文仅含可公开信息。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MissingHeaderException.class)
    public ResponseEntity<ApiError> missingHeader(MissingHeaderException e) {
        return ResponseEntity.status(e.status()).body(e.error());
    }

    @ExceptionHandler(OptimisticLockConflictException.class)
    public ResponseEntity<ApiError> optimisticLock(OptimisticLockConflictException e) {
        return status(HttpStatus.CONFLICT, e.getApiError());
    }

    @ExceptionHandler(InvalidTransitionException.class)
    public ResponseEntity<ApiError> invalidTransition(InvalidTransitionException e) {
        return status(HttpStatus.CONFLICT, e.getApiError());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiError> idempotency(IdempotencyConflictException e) {
        return status(HttpStatus.CONFLICT, e.getApiError());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException e) {
        return status(HttpStatus.NOT_FOUND, e.getApiError());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiError> validation(ValidationException e) {
        return status(HttpStatus.UNPROCESSABLE_ENTITY, e.getApiError());
    }

    @ExceptionHandler(IllegalRequestException.class)
    public ResponseEntity<ApiError> illegalRequest(IllegalRequestException e) {
        return status(HttpStatus.BAD_REQUEST, e.getApiError());
    }

    @ExceptionHandler(AlarmDomainException.class)
    public ResponseEntity<ApiError> domain(AlarmDomainException e) {
        return status(HttpStatus.INTERNAL_SERVER_ERROR, e.getApiError());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiError> badRequest(Exception e) {
        return status(HttpStatus.BAD_REQUEST,
                ApiError.of(ErrorCodes.INVALID_ARGUMENT, "malformed request: " + safeMessage(e)));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> noResource(NoResourceFoundException e) {
        return status(HttpStatus.NOT_FOUND, ApiError.of(ErrorCodes.NOT_FOUND, "route not found"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        // 未分类错误仅记录服务端日志，正文不含敏感信息
        log.error("unexpected error", e);
        return status(HttpStatus.INTERNAL_SERVER_ERROR,
                ApiError.of(ErrorCodes.INTERNAL_ERROR, "internal error"));
    }

    private ResponseEntity<ApiError> status(HttpStatus httpStatus, ApiError error) {
        return ResponseEntity.status(httpStatus).body(error);
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? "unparseable" : message.substring(0, Math.min(message.length(), 200));
    }
}
