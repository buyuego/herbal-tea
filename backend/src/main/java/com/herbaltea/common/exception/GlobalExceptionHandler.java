package com.herbaltea.common.exception;

import com.herbaltea.common.result.Result;
import com.herbaltea.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：统一响应包装 + 状态码映射
 *
 * <p>对应设计文档 16.1 五层防护的异常出口：写冲突 409、幂等冲突 409、限流 429、
 * 鉴权 401/403 均在拦截器或业务层抛出，这里统一收敛。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e) {
        HttpStatus status = switch (e.getCode()) {
            case UNAUTHORIZED, TOKEN_REVOKED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN, DEVICE_RISK -> HttpStatus.FORBIDDEN;
            case CONFLICT, IDEMPOTENT_REPLAY -> HttpStatus.CONFLICT;
            case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.OK;
        };
        return ResponseEntity.status(status)
                .body(Result.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Result<Void>> handleValidation(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .orElse(ResultCode.PARAM_ERROR.getMessage());
        return ResponseEntity.badRequest().body(Result.fail(ResultCode.PARAM_ERROR, msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ResultCode.SYSTEM_ERROR, ResultCode.SYSTEM_ERROR.getMessage()));
    }
}
