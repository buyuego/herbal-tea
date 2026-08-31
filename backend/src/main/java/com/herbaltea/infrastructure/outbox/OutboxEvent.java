package com.herbaltea.infrastructure.outbox;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * event_outbox 表实体（对齐 V1__schema.sql 权威结构）
 *
 * <ul>
 *   <li>id 自增主键；event_id 为业务事件唯一 ID（UUID，uk_eo_event_id 唯一索引兜底 D1）</li>
 *   <li>status TINYINT：0 待投递 / 1 已投递 / 2 投递失败（超限转人工）</li>
 *   <li>retry_count 指数退避重试计数；next_retry_at 下次可投递时间（Worker 轮询条件）</li>
 * </ul>
 *
 * <p>Outbox 模式：业务事务提交时同事务写入（业务成功 ⇔ 事件必达），
 * Worker 扫描投递；本地事务保证不丢，幂等消费保证不重（at-least-once）。
 */
@Data
@TableName("event_outbox")
public class OutboxEvent {

    /** 待投递 */
    public static final int STATUS_PENDING = 0;
    /** 已投递成功 */
    public static final int STATUS_DISPATCHED = 1;
    /** 投递失败（超过重试上限，转人工处理） */
    public static final int STATUS_FAILED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件唯一 ID（UUID，业务幂等兜底） */
    private String eventId;

    /** 事件类型（OutboxEventType 枚举名） */
    private String eventType;

    /** 业务幂等键：消费前查 idempotency_keys 去重（与写接口共用幂等键体系） */
    private String bizKey;

    /** 事件载荷（JSON） */
    private String payload;

    private Integer status;

    /** 指数退避重试次数（2^retry * 5s，上限 5 次） */
    private Integer retryCount;

    /** 指数退避下次可投递时间（Worker 轮询条件；超限终态后不再更新） */
    private LocalDateTime nextRetryAt;

    private LocalDateTime createdAt;

    /** 投递完成时间（完结记录保留 7 天后清理，D11） */
    private LocalDateTime deliveredAt;
}
