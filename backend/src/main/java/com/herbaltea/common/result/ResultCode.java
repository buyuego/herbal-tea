package com.herbaltea.common.result;

import lombok.Getter;

/**
 * 统一响应码（HTTP 语义 + 业务码）
 */
@Getter
public enum ResultCode {

    /** 成功 */
    SUCCESS(0, "success"),

    // ===== 4xx 客户端错误 =====
    /** 参数校验失败 */
    PARAM_ERROR(40000, "参数错误"),
    /** 未登录或令牌缺失 */
    UNAUTHORIZED(40100, "未登录或登录已过期"),
    /** 登录态已吊销（token_version 不匹配，需重新登录） */
    TOKEN_REVOKED(40101, "登录已失效，请重新登录"),
    /** 无权限（角色/数据范围不满足） */
    FORBIDDEN(40300, "无权限执行该操作"),
    /** 资源不存在 */
    NOT_FOUND(40400, "资源不存在"),
    /** 写冲突（乐观锁 version 不匹配，返回 409） */
    CONFLICT(40900, "数据已被他人修改，请刷新后重试"),
    /** 幂等冲突（同幂等键已处理过，返回原始结果） */
    IDEMPOTENT_REPLAY(40901, "重复请求"),
    /** 频率超限 */
    TOO_MANY_REQUESTS(42900, "请求过于频繁，请稍后再试"),
    /** 设备风险（异地登录强制短信验证，A5） */
    DEVICE_RISK(40310, "登录环境异常，请完成短信验证"),

    // ===== 5xx 服务端错误 =====
    /** 业务异常（message 透传业务语义） */
    BIZ_ERROR(50000, "业务处理失败"),
    /** 系统内部错误 */
    SYSTEM_ERROR(50001, "系统繁忙，请稍后再试"),
    /** Outbox 投递超过重试上限（走人工处理，见 11.3 终态） */
    OUTBOX_MAX_RETRY(50002, "异步任务处理失败，已转人工"),
    ;

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
