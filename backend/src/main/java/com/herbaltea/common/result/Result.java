package com.herbaltea.common.result;

import lombok.Data;

/**
 * 统一响应包装
 *
 * @param <T> 载荷类型
 */
@Data
public class Result<T> {

    private int code;
    private String message;
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = ResultCode.SUCCESS.getCode();
        r.message = ResultCode.SUCCESS.getMessage();
        r.data = data;
        return r;
    }

    public static Result<Void> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(ResultCode code, String message) {
        Result<T> r = new Result<>();
        r.code = code.getCode();
        r.message = message;
        return r;
    }
}
