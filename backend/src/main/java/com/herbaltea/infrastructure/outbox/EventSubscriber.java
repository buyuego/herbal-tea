package com.herbaltea.infrastructure.outbox;

/**
 * 事件订阅者（各业务模块实现并注册为 Spring Bean）
 *
 * <p>设计文档 2.3：进程内 Worker 按订阅关系分发给各模块处理器（同进程方法调用）。
 * 模块边界约束：订阅者属于消费模块，只读对方数据须走对方模块接口（禁止跨模块直读表）。
 */
public interface EventSubscriber {

    /** 关注的事件类型 */
    OutboxEventType type();

    /**
     * 消费事件。
     *
     * <p>实现约定：
     * <ul>
     *   <li>必须以 bizKey 为键先查 idempotency_keys，命中直接返回（幂等消费）</li>
     *   <li>失败抛异常，由 Worker 指数退避重试</li>
     *   <li>重试 5 次仍失败：Worker 置 FAILED + 告警，业务按终态规则转人工（如退款回退失败 11.3）</li>
     * </ul>
     */
    void consume(OutboxEvent event);
}
