package com.herbaltea.common.exception;

import com.herbaltea.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常（message 透传业务语义，由全局异常处理器统一包装）
 */
@Getter
public class BizException extends RuntimeException {

    private final ResultCode code;

    public BizException(String message) {
        super(message);
        this.code = ResultCode.BIZ_ERROR;
    }

    public BizException(ResultCode code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException conflict(String message) {
        return new BizException(ResultCode.CONFLICT, message);
    }

    public static BizException unauthorized(String message) {
        return new BizException(ResultCode.UNAUTHORIZED, message);
    }
}
